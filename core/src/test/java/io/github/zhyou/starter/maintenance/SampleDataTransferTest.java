package io.github.zhyou.starter.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SampleDataTransferTest {
  @TempDir Path dir;

  @Test
  void validatesWholeFileBeforeUsingConnection() throws Exception {
    for (String text :
        new String[] {
          "broken",
          "{\"formatVersion\":2,\"orders\":[]}",
          "{\"formatVersion\":1,\"orders\":[null]}",
          "{\"formatVersion\":1,\"orders\":[{\"id\":null}]}"
        }) {
      Path file = dir.resolve("invalid.json");
      Files.writeString(file, text);
      assertThrows(IllegalArgumentException.class, () -> SampleDataTransfer.importData(null, file));
    }
  }

  @Test
  void readsUuidUtcTextAndStateWithoutLoss() throws Exception {
    Path file = dir.resolve("valid.json");
    Files.writeString(
        file,
        "{\"formatVersion\":1,\"orders\":[{\"customerId\":\"00000000-0000-0000-0000-000000000001\",\"id\":\"00000000-0000-0000-0000-000000000002\",\"description\":\"你好 sample\",\"status\":\"COMPLETED\",\"createdAt\":\"2026-10-29T01:02:03.123456Z\"}]}");
    var bundle = SampleDataTransfer.readBundle(file);
    assertEquals(1, bundle.formatVersion());
    var order = bundle.orders().getFirst();
    assertEquals("你好 sample", order.description());
    assertEquals("COMPLETED", order.status().name());
    assertEquals(Instant.parse("2026-10-29T01:02:03.123456Z"), order.createdAt());
    assertEquals(2, order.id().getLeastSignificantBits());
  }

  @Test
  void rejectsConcatenatedRootsAndGarbageBeforeUsingConnection() throws Exception {
    String valid = "{\"formatVersion\":1,\"orders\":[]}";
    for (String suffix : new String[] {valid, "garbage"}) {
      Path file = dir.resolve("trailing.json");
      Files.writeString(file, valid + suffix);
      assertThrows(IllegalArgumentException.class, () -> SampleDataTransfer.importData(null, file));
    }
  }
}
