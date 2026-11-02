package io.github.zhyou.starter.buildlogic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEnvironmentTest {
  @TempDir Path root;

  record Invocation(
      List<String> command, Map<String, String> environment, Path input, Path output) {}

  final List<Invocation> calls = new ArrayList<>();

  LocalEnvironment controller(Map<String, String> parent) {
    return new LocalEnvironment(
        root,
        parent,
        (command, env, input, output) ->
            calls.add(new Invocation(List.copyOf(command), Map.copyOf(env), input, output)),
        List.of("managed-java", "-cp", "maintenance"));
  }

  @Test
  void createsPrivateStableStateAndCredentials() throws Exception {
    var c = controller(Map.of());
    var first = c.create("single", null, null);
    byte[] credentials = Files.readAllBytes(Path.of(first.env_file()));
    assertEquals(first, c.create("single", null, null));
    assertArrayEquals(credentials, Files.readAllBytes(Path.of(first.env_file())));
    assertEquals(
        "rwx------",
        PosixFilePermissions.toString(Files.getPosixFilePermissions(root.resolve(".local"))));
    assertEquals(
        "rw-------",
        PosixFilePermissions.toString(Files.getPosixFilePermissions(Path.of(first.env_file()))));
    assertEquals(
        "rw-------",
        PosixFilePermissions.toString(
            Files.getPosixFilePermissions(root.resolve(".local/state-single.json"))));
    assertEquals(
        "jdbc:postgresql://127.0.0.1:5432/starter", c.environment(first).get("APP_DB_JDBC_URL"));
    assertEquals(first, c.load("single", null));
  }

  @Test
  void resetRequiresConfirmationAndDownRetainsVolumes() {
    var c = controller(Map.of());
    var state = c.create("single", null, null);
    assertThrows(IllegalArgumentException.class, () -> c.down(state, true, false));
    assertTrue(calls.isEmpty());
    c.down(state, false, false);
    assertEquals("down", calls.getFirst().command().getLast());
    assertFalse(calls.getFirst().command().contains("--volumes"));
    assertTrue(calls.getFirst().command().contains(state.project()));
    c.down(state, true, true);
    assertEquals(
        List.of("down", "--volumes"),
        calls
            .getLast()
            .command()
            .subList(calls.getLast().command().size() - 2, calls.getLast().command().size()));
  }

  @Test
  void rejectsInvalidNamesExternalUrlsTamperingAndSymlinks() throws Exception {
    var c = controller(Map.of());
    assertThrows(IllegalArgumentException.class, () -> c.create("single", "../../foreign", null));
    assertFalse(Files.exists(root.resolve(".local")));
    var state = c.create("single", null, null);
    var external = controller(Map.of("APP_DB_JDBC_URL", "jdbc:postgresql://prod/starter"));
    assertThrows(IllegalArgumentException.class, () -> external.load("single", null));
    Path env = Path.of(state.env_file());
    String original = Files.readString(env);
    Files.writeString(env, original.replace("127.0.0.1", "prod.example"));
    assertThrows(IllegalArgumentException.class, () -> c.load("single", null));
    Files.delete(env);
    Path other = root.resolve("elsewhere");
    Files.writeString(other, original);
    Files.createSymbolicLink(env, other);
    assertThrows(IllegalArgumentException.class, () -> c.load("single", null));
    assertTrue(calls.isEmpty());
  }

  @Test
  void rejectsMissingRestoreBeforeCreatingResources() {
    var c = controller(Map.of());
    assertThrows(IllegalArgumentException.class, () -> c.restore("single", root.resolve("absent")));
    assertFalse(Files.exists(root.resolve(".local")));
    assertTrue(calls.isEmpty());
  }

  @Test
  void restoreUsesNewTargetsCredentialsDespiteExportedSourceEnvironment() throws Exception {
    for (String mode : List.of("single", "sharding")) {
      calls.clear();
      var c = controller(Map.of());
      var source = c.create(mode, null, null);
      var original = c.environment(source);
      Path backup = root.resolve(mode + ".backup");
      Files.writeString(backup, "fixture");
      var restoring = controller(original);
      var target = restoring.restore(mode, backup);
      assertNotEquals(source.name(), target.name());
      assertNotEquals(
          original.get("APP_DB_PASSWORD"), c.environment(target).get("APP_DB_PASSWORD"));
      assertFalse(calls.isEmpty());
      for (var call : calls) {
        assertEquals(
            c.environment(target).get("APP_DB_JDBC_URL"),
            call.environment().get("APP_DB_JDBC_URL"));
        assertEquals(
            c.environment(target).get("APP_DB_PASSWORD"),
            call.environment().get("APP_DB_PASSWORD"));
        assertEquals(
            c.environment(target).get("APP_DB_PORT"), call.environment().get("APP_DB_PORT"));
      }
      assertEquals(original, c.environment(source));
    }
  }

  @Test
  void refusesBackupOverwrite() throws Exception {
    var c = controller(Map.of());
    var source = c.create("single", null, null);
    Path file = root.resolve("backup");
    Files.writeString(file, "existing");
    assertThrows(IllegalArgumentException.class, () -> c.backup(source, file));
    assertEquals("existing", Files.readString(file));
    assertTrue(calls.isEmpty());
  }

  @Test
  void rejectsIncompleteStateWithoutLeakingItsContents() throws Exception {
    var c = controller(Map.of());
    var state = c.create("single", null, null);
    Files.writeString(
        root.resolve(".local/state-single.json"),
        "{\"owner\":\""
            + root
            + "\",\"mode\":\"single\",\"name\":\"single\",\"port\":5432,\"env_file\":\""
            + state.env_file()
            + "\"}");
    assertThrows(IllegalArgumentException.class, () -> c.load("single", null));
    assertTrue(calls.isEmpty());
  }

  @Test
  void copiedValidStateCannotSelectAnotherRecordedEnvironment() throws Exception {
    var c = controller(Map.of());
    c.create("single", null, null);
    c.create("sharding", null, null);
    Files.copy(
        root.resolve(".local/state-sharding.json"),
        root.resolve(".local/state-single.json"),
        StandardCopyOption.REPLACE_EXISTING);
    assertThrows(IllegalArgumentException.class, () -> c.load("single", null));
    c.create("single", "single-restore-0123abcd", 15432);
    Files.copy(
        root.resolve(".local/state-single-restore-0123abcd.json"),
        root.resolve(".local/state-single.json"),
        StandardCopyOption.REPLACE_EXISTING);
    assertThrows(IllegalArgumentException.class, () -> c.load("single", null));
    assertTrue(calls.isEmpty());
  }
}
