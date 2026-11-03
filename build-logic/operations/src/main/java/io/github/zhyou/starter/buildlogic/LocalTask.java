package io.github.zhyou.starter.buildlogic;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.inject.Inject;
import org.apache.tools.ant.types.Commandline;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Operates on external local state; must always execute")
public abstract class LocalTask extends DefaultTask {
  @Internal
  public abstract DirectoryProperty getWorkspace();

  @Input
  public abstract Property<String> getOperation();

  @Input
  public abstract Property<String> getDbMode();

  @Input
  public abstract Property<String> getDbName();

  @Input
  public abstract Property<Boolean> getConfirmReset();

  @Input
  public abstract Property<String> getInputPath();

  @Input
  public abstract Property<String> getOutputPath();

  @Input
  public abstract ListProperty<String> getProgramArguments();

  @InputFile
  public abstract RegularFileProperty getJavaExecutable();

  @Classpath
  public abstract ConfigurableFileCollection getMaintenanceClasspath();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @Option(
      option = "args",
      description = "Additional program arguments (quoted like a command line)")
  public void setArgumentsString(String args) {
    getProgramArguments().set(List.of(Commandline.translateCommandline(args)));
  }

  private void run(List<String> command, Map<String, String> env, Path input, Path output) {
    try (InputStream in =
            input == null ? InputStream.nullInputStream() : Files.newInputStream(input);
        OutputStream out =
            output == null
                ? OutputStream.nullOutputStream()
                : Files.newOutputStream(
                    output, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      boolean protocol = getOperation().get().equals("serena");
      boolean quiet = getOperation().get().equals("doctor");
      var actualCommand = new ArrayList<>(command);
      String executable = command.getFirst();
      if (!executable.contains("/") && !executable.contains("\\")) {
        Arrays.stream(
                env.getOrDefault("PATH", "")
                    .split(java.util.regex.Pattern.quote(File.pathSeparator)))
            .map(Path::of)
            .map(path -> path.resolve(executable))
            .filter(Files::isExecutable)
            .findFirst()
            .ifPresent(path -> actualCommand.set(0, path.toAbsolutePath().toString()));
      }
      var result =
          getExecOperations()
              .exec(
                  spec -> {
                    spec.commandLine(actualCommand);
                    spec.workingDir(getWorkspace().get().getAsFile());
                    spec.setEnvironment(env);
                    spec.setStandardInput(protocol ? System.in : in);
                    spec.setStandardOutput(
                        output != null ? out : protocol ? System.out : quiet ? out : System.err);
                    spec.setErrorOutput(System.err);
                    spec.setIgnoreExitValue(true);
                  });
      if (result.getExitValue() != 0)
        throw new GradleException(
            "Operation failed (exit "
                + result.getExitValue()
                + "); source configuration retained.");
    } catch (IOException e) {
      throw new GradleException("Cannot read input or write operation output");
    }
  }

  @TaskAction
  public void perform() {
    Path root = getWorkspace().get().getAsFile().toPath();
    String operation = getOperation().get();
    try {
      if (operation.equals("doctor")) {
        for (String tool : List.of("mise", "git", "docker", "jq", "rg", "ast-grep")) {
          boolean found =
              Arrays.stream(
                      System.getenv()
                          .getOrDefault("PATH", "")
                          .split(java.util.regex.Pattern.quote(File.pathSeparator)))
                  .map(Path::of)
                  .anyMatch(p -> Files.isExecutable(p.resolve(tool)));
          if (!found)
            throw new IllegalArgumentException(
                "Missing tool: " + tool + "; install the project tools with mise");
        }
        run(
            List.of("docker", "info", "--format", "{{.ServerVersion}}"),
            System.getenv(),
            null,
            null);
        run(List.of("docker", "compose", "version"), System.getenv(), null, null);
        System.out.println(
            "Tools and Docker ready. Project JDK: "
                + System.getProperty("java.version")
                + " from the Gradle runtime.");
        return;
      }
      if (operation.equals("serena")) {
        var env = new HashMap<>(System.getenv());
        env.put("JAVA_HOME", getJavaExecutable().get().getAsFile().getParentFile().getParent());
        var command =
            new ArrayList<>(
                List.of(
                    "serena",
                    "start-mcp-server",
                    "--project-from-cwd",
                    "--open-web-dashboard",
                    "false"));
        command.addAll(getProgramArguments().get());
        run(command, env, null, null);
        return;
      }
      var prefix =
          List.of(
              getJavaExecutable().get().getAsFile().getAbsolutePath(),
              "-cp",
              getMaintenanceClasspath().getAsPath());
      var local = new LocalEnvironment(root, System.getenv(), this::run, prefix);
      String mode = getDbMode().get(), name = getDbName().get();
      if (operation.equals("reset") && !getConfirmReset().get())
        throw new IllegalArgumentException("reset requires -PconfirmLocalReset=true");
      if (operation.equals("restore")) {
        if (!name.isEmpty())
          throw new IllegalArgumentException(
              "restore always creates a new target; do not supply dbName");
        if (getInputPath().get().isEmpty())
          throw new IllegalArgumentException("restore requires -Pinput=<file>");
        local.restore(mode, root.resolve(getInputPath().get()).normalize());
        return;
      }
      if (operation.equals("backup") && getOutputPath().get().isEmpty())
        throw new IllegalArgumentException("backup requires -Poutput=<file>");
      var state = operation.equals("up") ? local.create(mode, name, null) : local.load(mode, name);
      state = local.load(mode, state.name());
      switch (operation) {
        case "up" -> local.up(state);
        case "down", "reset" ->
            local.down(state, operation.equals("reset"), getConfirmReset().get());
        case "migrate" -> local.migrate(state);
        case "backup" -> local.backup(state, root.resolve(getOutputPath().get()).normalize());
        default -> throw new IllegalArgumentException("Unknown local operation");
      }
    } catch (IllegalArgumentException e) {
      throw new GradleException(e.getMessage());
    }
  }
}
