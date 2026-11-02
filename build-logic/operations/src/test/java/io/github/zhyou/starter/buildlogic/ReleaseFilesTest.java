package io.github.zhyou.starter.buildlogic;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseFilesTest {
  @TempDir Path root;

  @Test
  void sourceDigestTracksGroovyAndIncludedBuildSourcesButIgnoresCaches() throws Exception {
    Files.writeString(root.resolve("build.gradle"), "plugins { id 'base' }\n");
    String first = ReleaseFiles.sourceDigest(root);
    Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }\n");
    String second = ReleaseFiles.sourceDigest(root);
    assertNotEquals(first, second);
    Path plugin = root.resolve("build-logic/operations/src/main/java/Tool.java");
    Files.createDirectories(plugin.getParent());
    Files.writeString(plugin, "class Tool {}\n");
    String third = ReleaseFiles.sourceDigest(root);
    assertNotEquals(second, third);
    Path cache = root.resolve("build-logic/.gradle/cache");
    Files.createDirectories(cache.getParent());
    Files.writeString(cache, "transient");
    Path artifact = root.resolve("build-logic/operations/build/generated");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, "transient");
    assertEquals(third, ReleaseFiles.sourceDigest(root));
    ReleaseFiles.sourceMetadata(root, root.resolve("SOURCE.json"), "1.2.3", null);
    var json = new ObjectMapper().readTree(root.resolve("SOURCE.json").toFile());
    assertEquals("1.2.3", json.get("version").asText());
    assertTrue(json.get("gitCommit").isNull());
    assertEquals(third, json.get("sourceTreeSha256").asText());
  }

  @Test
  void validatesSbomAndChecksumsActualArtifactBytes() throws Exception {
    Path sbom = root.resolve("cli.cdx.json");
    Files.writeString(sbom, "{\"bomFormat\":\"CycloneDX\",\"components\":[]}");
    assertThrows(IllegalArgumentException.class, () -> ReleaseFiles.validateSbom(sbom));
    Files.writeString(sbom, "{\"bomFormat\":\"CycloneDX\",\"components\":[{\"name\":\"app\"}]}");
    ReleaseFiles.validateSbom(sbom);
    byte[] bytes = "artifact\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(root.resolve("cli.jar"), bytes);
    ReleaseFiles.checksums(root);
    String expected =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            + "  cli.jar\n";
    assertTrue(Files.readString(root.resolve("SHA256SUMS")).contains(expected));
    assertFalse(Files.readString(root.resolve("SHA256SUMS")).contains("  SHA256SUMS"));
  }

  @Test
  void rootDependencyLocksArePartOfTheSourceIdentity() throws Exception {
    Files.writeString(root.resolve("build.gradle"), "plugins { id 'base' }\n");
    String first = ReleaseFiles.sourceDigest(root);
    Files.writeString(root.resolve("gradle.lockfile"), "library:1=maintenanceRuntime\n");
    String second = ReleaseFiles.sourceDigest(root);
    assertNotEquals(first, second);
    Files.writeString(root.resolve("settings-gradle.lockfile"), "resolver:1=classpath\n");
    assertNotEquals(second, ReleaseFiles.sourceDigest(root));
  }
}
