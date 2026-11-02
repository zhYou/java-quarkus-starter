package io.github.zhyou.starter.buildlogic;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.Stream;

public final class ReleaseFiles {
  public static final List<String> SOURCE_ROOTS =
      List.of(
          "README.md",
          "AGENTS.md",
          "gradle.properties",
          "settings.gradle",
          "build.gradle",
          "gradlew",
          "gradlew.bat",
          "gradle",
          "build-logic",
          "mise.toml",
          ".editorconfig",
          ".gitignore",
          ".dockerignore",
          ".gitattributes",
          ".env.example",
          ".github",
          "core",
          "cli",
          "server",
          "infra",
          "deploy",
          "docs");

  private ReleaseFiles() {}

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public static String sourceDigest(Path root) {
    var files = new ArrayList<Path>();
    try {
      for (String name : SOURCE_ROOTS) {
        Path source = root.resolve(name);
        if (!Files.exists(source)) continue;
        try (Stream<Path> paths = Files.walk(source)) {
          paths
              .filter(Files::isRegularFile)
              .filter(
                  p -> {
                    Path relative = root.relativize(p);
                    if (Set.of("gradle.lockfile", "settings-gradle.lockfile")
                            .contains(p.getFileName().toString())
                        || relative.equals(Path.of("gradle", "verification-metadata.xml")))
                      return false;
                    for (Path part : relative)
                      if (Set.of("build", ".gradle", ".local", "__pycache__")
                          .contains(part.toString())) return false;
                    return true;
                  })
              .forEach(files::add);
        }
      }
      files.sort(Comparator.comparing(p -> root.relativize(p).toString()));
      var hash = digest();
      for (Path path : files) {
        hash.update(
            root.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
        hash.update((byte) 0);
        hash.update(Files.readAllBytes(path));
        hash.update((byte) 0);
      }
      return HexFormat.of().formatHex(hash.digest());
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read release source files");
    }
  }

  public static void sourceMetadata(Path root, Path target, String version, String commit) {
    var source = new LinkedHashMap<String, Object>();
    source.put("version", version);
    source.put("gitCommit", commit == null || commit.isBlank() ? null : commit);
    source.put("sourceTreeSha256", sourceDigest(root));
    try {
      Files.writeString(
          target,
          new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(source) + "\n");
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot write release source metadata");
    }
  }

  public static void validateSbom(Path path) {
    try {
      var json = new ObjectMapper().readTree(path.toFile());
      if (json == null
          || !"CycloneDX".equals(json.path("bomFormat").asText())
          || !json.path("components").isArray()
          || json.path("components").isEmpty())
        throw new IllegalArgumentException("Invalid release SBOM");
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read release SBOM");
    }
  }

  public static void checksums(Path directory) {
    try (Stream<Path> paths = Files.list(directory)) {
      var files =
          paths
              .filter(Files::isRegularFile)
              .filter(p -> !p.getFileName().toString().equals("SHA256SUMS"))
              .sorted()
              .toList();
      var checksums = new StringBuilder();
      for (Path file : files)
        checksums
            .append(HexFormat.of().formatHex(digest().digest(Files.readAllBytes(file))))
            .append("  ")
            .append(file.getFileName())
            .append('\n');
      Path target = directory.resolve("SHA256SUMS");
      Files.writeString(target, checksums);
      for (String line : Files.readAllLines(target)) {
        String[] parts = line.split("  ", 2);
        if (parts.length != 2
            || !HexFormat.of()
                .formatHex(digest().digest(Files.readAllBytes(directory.resolve(parts[1]))))
                .equals(parts[0]))
          throw new IllegalArgumentException("Release checksum verification failed");
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot write or verify release checksums");
    }
  }
}
