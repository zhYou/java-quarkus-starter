package io.github.zhyou.starter.buildlogic;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "gradle.lockfile",
        "settings-gradle.lockfile",
        "core/gradle.lockfile",
        "cli/gradle.lockfile",
        "server/gradle.lockfile",
        "build-logic/gradle.lockfile",
        "build-logic/operations/gradle.lockfile",
        "mise.lock",
        "gradle/verification-metadata.xml"
      })
  void retiredGeneratedFilesDoNotChangeSourceIdentity(String name) throws Exception {
    Files.writeString(root.resolve("build.gradle"), "plugins { id 'base' }\n");
    String first = ReleaseFiles.sourceDigest(root);
    Path generated = root.resolve(name);
    Files.createDirectories(generated.getParent());
    Files.writeString(generated, "local generated content\n");
    assertEquals(first, ReleaseFiles.sourceDigest(root));
  }

  @ParameterizedTest
  @ValueSource(strings = {"gradle/libs.versions.toml", "mise.toml"})
  void versionDeclarationsArePartOfTheSourceIdentity(String name) throws Exception {
    Path config = root.resolve(name);
    Files.createDirectories(config.getParent());
    Files.writeString(config, "version = '1'\n");
    String first = ReleaseFiles.sourceDigest(root);
    Files.writeString(config, "version = '2'\n");
    assertNotEquals(first, ReleaseFiles.sourceDigest(root));
  }
}
