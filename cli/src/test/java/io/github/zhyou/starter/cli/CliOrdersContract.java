package io.github.zhyou.starter.tests.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zhyou.starter.application.OrderService;
import io.github.zhyou.starter.cli.StarterCli;
import jakarta.inject.Inject;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

abstract class CliOrdersContract {
  @Inject OrderService service;
  @Inject ObjectMapper mapper;

  @Test
  void fullLifecycleJsonAndExitCodes() throws Exception {
    String customer = UUID.randomUUID().toString();
    String created = execute(0, "create", "--customer-id", customer, "--description", "cli order");
    var node = mapper.readTree(created);
    String id = node.get("id").asText();
    assertEquals("NEW", node.get("status").asText());
    assertEquals(
        id,
        mapper
            .readTree(execute(0, "get", "--customer-id", customer, "--id", id))
            .get("id")
            .asText());
    assertEquals(1, mapper.readTree(execute(0, "list", "--customer-id", customer)).size());
    assertEquals(
        "COMPLETED",
        mapper
            .readTree(execute(0, "complete", "--customer-id", customer, "--id", id))
            .get("status")
            .asText());
    assertEquals(
        "ORDER_CONFLICT",
        mapper
            .readTree(execute(4, "complete", "--customer-id", customer, "--id", id))
            .get("code")
            .asText());
    assertEquals(
        "ORDER_NOT_FOUND",
        mapper
            .readTree(execute(3, "get", "--customer-id", UUID.randomUUID().toString(), "--id", id))
            .get("code")
            .asText());
  }

  private String execute(int expected, String... args) throws Exception {
    var all = new ArrayList<String>(List.of("--output=json", "orders"));
    all.addAll(List.of(args));
    var out = new StringWriter();
    var err = new StringWriter();
    assertEquals(
        expected,
        StarterCli.commandLine(service, new PrintWriter(out, true), new PrintWriter(err, true))
            .execute(all.toArray(String[]::new)),
        err.toString());
    try (var parser = mapper.createParser(out.toString())) {
      mapper.readTree(parser);
      assertNull(parser.nextToken(), out.toString());
    }
    return out.toString();
  }
}
