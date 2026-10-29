package io.github.zhyou.starter.cli;

import io.github.zhyou.starter.application.OrderService;
import io.quarkus.runtime.QuarkusApplication;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.PrintWriter;

@ApplicationScoped
public class CliApplication implements QuarkusApplication {
  @Inject OrderService service;

  @Override
  @jakarta.enterprise.context.control.ActivateRequestContext
  public int run(String... args) {
    return StarterCli.commandLine(
            service, new PrintWriter(System.out, true), new PrintWriter(System.err, true))
        .execute(args);
  }
}
