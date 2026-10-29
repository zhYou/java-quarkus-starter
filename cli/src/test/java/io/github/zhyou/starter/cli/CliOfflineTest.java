package io.github.zhyou.starter.tests.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.cli.StarterCli;
import java.io.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CliOfflineTest {
  @Test
  void helpAndVersionWithoutDatabase() {
    for (String[] args :
        new String[][] {
          {"--help"}, {"--version"}, {"orders", "--help"}, {"orders", "create", "--help"}
        }) {
      var out = new StringWriter();
      assertEquals(
          0,
          StarterCli.preflight(
              args, new PrintWriter(out, true), new PrintWriter(new StringWriter(), true)));
      assertFalse(out.toString().isBlank());
    }
  }

  @Test
  void invalidArgumentsWithoutDatabase() {
    for (String[] args :
        new String[][] {
          {"orders", "get", "--customer-id", "bad", "--id", "bad"},
          {"orders", "create", "--customer-id", UUID.randomUUID().toString(), "--description", " "},
          {"orders", "list", "--customer-id", UUID.randomUUID().toString(), "--limit", "101"}
        }) {
      assertEquals(
          2,
          StarterCli.preflight(
              args,
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(new StringWriter(), true)));
    }
  }
}
