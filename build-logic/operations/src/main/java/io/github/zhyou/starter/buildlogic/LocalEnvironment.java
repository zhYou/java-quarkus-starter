package io.github.zhyou.starter.buildlogic;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;

/** Project-owned local environments. Credentials are read only while an operation executes. */
public final class LocalEnvironment {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final SecureRandom RANDOM = new SecureRandom();
  private final Path root;
  private final Path local;
  private final String owner;
  private final Map<String, String> parent;
  private final Runner runner;
  private final List<String> javaPrefix;

  public record State(
      String owner, String mode, String name, String project, int port, String env_file) {}

  @FunctionalInterface
  public interface Runner {
    void run(List<String> command, Map<String, String> environment, Path input, Path output);
  }

  public LocalEnvironment(
      Path root, Map<String, String> environment, Runner runner, List<String> javaPrefix) {
    this.root = root.toAbsolutePath().normalize();
    this.local = this.root.resolve(".local");
    this.parent = Map.copyOf(environment);
    this.runner = runner;
    this.javaPrefix = List.copyOf(javaPrefix);
    try {
      owner =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(this.root.toString().getBytes(StandardCharsets.UTF_8)))
              .substring(0, 12);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static String key(String mode, String name) {
    if (mode == null || !Set.of("single", "sharding").contains(mode))
      throw new IllegalArgumentException("mode must be single or sharding");
    String result = name == null || name.isEmpty() ? mode : name;
    if (!result.matches(mode + "(-restore-[0-9a-f]{8})?"))
      throw new IllegalArgumentException("invalid local environment name");
    return result;
  }

  private static String token(int bytes) {
    byte[] buffer = new byte[bytes];
    RANDOM.nextBytes(buffer);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
  }

  private void privateDirectory() throws IOException {
    if (Files.isSymbolicLink(local))
      throw new IllegalArgumentException("local state directory must not be a symlink");
    Files.createDirectories(local);
    try {
      Files.setPosixFilePermissions(local, PosixFilePermissions.fromString("rwx------"));
    } catch (UnsupportedOperationException ignored) {
      /* Non-POSIX platforms use their directory ACLs. */
    }
  }

  private static void privateFile(Path path, String content) throws IOException {
    try {
      Files.createFile(
          path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    } catch (UnsupportedOperationException ignored) {
      Files.createFile(path);
    }
    Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
  }

  public State create(String mode, String name, Integer port) {
    String id = key(mode, name);
    Path stateFile = local.resolve("state-" + id + ".json");
    if (Files.exists(stateFile, LinkOption.NOFOLLOW_LINKS)) return load(mode, id);
    try {
      privateDirectory();
      int selected = port == null ? (mode.equals("single") ? 5432 : 55432) : port;
      if (selected < 1 || selected > 65535)
        throw new IllegalArgumentException("invalid local port");
      Path env = local.resolve("db-" + id + ".env");
      var config = new LinkedHashMap<String, String>();
      config.put("APP_DB_MODE", mode);
      config.put("APP_DB_PORT", Integer.toString(selected));
      config.put("APP_DB_JDBC_URL", "jdbc:postgresql://127.0.0.1:" + selected + "/starter");
      config.put("APP_DB_USERNAME", "app_runtime");
      config.put("APP_DB_PASSWORD", token(24));
      config.put("APP_DB_MIGRATION_USERNAME", "app_migration");
      config.put("APP_DB_MIGRATION_PASSWORD", token(24));
      config.put("APP_DB_ADMIN_PASSWORD", token(24));
      var text = new StringBuilder();
      config.forEach((k, v) -> text.append(k).append('=').append(v).append('\n'));
      privateFile(env, text.toString());
      var state =
          new State(
              root.toString(), mode, id, "starter-" + owner + "-" + id, selected, env.toString());
      privateFile(stateFile, JSON.writeValueAsString(state));
      return state;
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot create private local state");
    }
  }

  public State load(String mode, String name) {
    String id = key(mode, name);
    Path stateFile = local.resolve("state-" + id + ".json");
    if (Files.isSymbolicLink(local) || !Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalArgumentException(
          "Cannot read project-owned local state; start the selected environment first");
    try {
      State state = JSON.readValue(stateFile.toFile(), State.class);
      if (state == null || !mode.equals(state.mode()) || !id.equals(state.name())) {
        throw new IllegalArgumentException("state does not match the selected local environment");
      }
      validate(state);
      String external = parent.get("APP_DB_JDBC_URL");
      if (external != null
          && !external.isEmpty()
          && !external.equals(config(state).get("APP_DB_JDBC_URL")))
        throw new IllegalArgumentException(
            "external database variables are set; local operation refused");
      return state;
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read local state");
    }
  }

  private void validate(State state) {
    if (state == null) throw new IllegalArgumentException("invalid local state");
    String id = key(state.mode(), state.name());
    Path expected = local.resolve("db-" + id + ".env");
    if (!root.toString().equals(state.owner())
        || !id.equals(state.name())
        || !("starter-" + owner + "-" + id).equals(state.project())
        || !expected.toString().equals(state.env_file())
        || Files.isSymbolicLink(local)
        || !Files.isRegularFile(expected, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalArgumentException("refusing resources not owned by this project");
    var config = config(state);
    if (state.port() < 1
        || state.port() > 65535
        || !Objects.equals(config.get("APP_DB_PORT"), Integer.toString(state.port()))
        || !Objects.equals(
            config.get("APP_DB_JDBC_URL"),
            "jdbc:postgresql://127.0.0.1:" + state.port() + "/starter"))
      throw new IllegalArgumentException("refusing a non-local database URL or inconsistent port");
  }

  private Map<String, String> config(State state) {
    try {
      var config = new LinkedHashMap<String, String>();
      for (String line : Files.readAllLines(Path.of(state.env_file()))) {
        if (line.isBlank() || line.startsWith("#")) continue;
        int separator = line.indexOf('=');
        if (separator < 1) throw new IllegalArgumentException("invalid local configuration");
        config.put(line.substring(0, separator), line.substring(separator + 1));
      }
      return config;
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read local configuration");
    }
  }

  public Map<String, String> environment(State state) {
    validate(state);
    var env = new HashMap<>(parent);
    env.putAll(config(state));
    return env;
  }

  private void compose(State state, List<String> arguments, Path input, Path output) {
    var command =
        new ArrayList<>(
            List.of(
                "docker",
                "compose",
                "--env-file",
                state.env_file(),
                "-p",
                state.project(),
                "-f",
                root.resolve(
                        "infra/"
                            + (state.mode().equals("single") ? "postgres" : "citus")
                            + "/compose.yaml")
                    .toString()));
    command.addAll(arguments);
    runner.run(command, environment(state), input, output);
  }

  private void java(State state, String main, String... arguments) {
    if (javaPrefix.isEmpty()) throw new IllegalStateException("maintenance runtime unavailable");
    var command = new ArrayList<>(javaPrefix);
    command.add(main);
    command.addAll(List.of(arguments));
    runner.run(command, environment(state), null, null);
  }

  public void migrate(State state) {
    java(state, "io.github.zhyou.starter.maintenance.MigrationRunner");
  }

  public void up(State state) {
    start(state, true);
  }

  private void start(State state, boolean migrate) {
    compose(state, List.of("up", "-d", "--wait", "--wait-timeout", "90"), null, null);
    if (state.mode().equals("sharding"))
      compose(
          state,
          List.of(
              "exec",
              "-T",
              "coordinator",
              "psql",
              "-U",
              "bootstrap",
              "-d",
              "starter",
              "-v",
              "ON_ERROR_STOP=1",
              "-f",
              "/opt/starter/topology.sql"),
          null,
          null);
    if (migrate) migrate(state);
  }

  public void down(State state, boolean reset, boolean confirmed) {
    if (reset && !confirmed)
      throw new IllegalArgumentException(
          "reset requires -PconfirmLocalReset=true; it deletes only the selected local volumes");
    State checked = load(state.mode(), state.name());
    compose(checked, reset ? List.of("down", "--volumes") : List.of("down"), null, null);
  }

  public void backup(State state, Path target) {
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalArgumentException("export target already exists");
    if (state.mode().equals("sharding")) {
      java(
          state,
          "io.github.zhyou.starter.maintenance.SampleDataTransfer",
          "export",
          target.toString());
      return;
    }
    try {
      privateFile(target, "");
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot create private backup file");
    }
    try {
      compose(
          state,
          List.of(
              "exec",
              "-T",
              "postgres",
              "pg_dump",
              "-U",
              "app_migration",
              "-d",
              "starter",
              "--no-owner",
              "--format=custom"),
          null,
          target);
    } catch (RuntimeException failure) {
      try {
        Files.deleteIfExists(target);
      } catch (IOException ignored) {
        /* Preserve the original failure. */
      }
      throw failure;
    }
  }

  public State restore(String mode, Path input) {
    key(mode, null);
    if (!Files.isRegularFile(input))
      throw new IllegalArgumentException("restore input does not exist");
    String name = mode + "-restore-" + HexFormat.of().formatHex(randomBytes(4));
    int port;
    try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot allocate a local restore port");
    }
    State target = create(mode, name, port);
    System.err.println("New restore target: " + name + "; configuration: " + target.env_file());
    start(target, mode.equals("sharding"));
    if (mode.equals("single")) {
      compose(
          target,
          List.of(
              "exec",
              "-T",
              "postgres",
              "pg_restore",
              "-U",
              "app_migration",
              "-d",
              "starter",
              "--clean",
              "--if-exists",
              "--no-owner",
              "--exit-on-error"),
          input,
          null);
      migrate(target);
    } else
      java(
          target,
          "io.github.zhyou.starter.maintenance.SampleDataTransfer",
          "import",
          input.toString());
    return target;
  }

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    RANDOM.nextBytes(bytes);
    return bytes;
  }
}
