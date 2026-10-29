package io.github.zhyou.starter.cli;

import io.github.zhyou.starter.application.OrderService;
import io.github.zhyou.starter.domain.*;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;

abstract class OrderCommand implements Callable<Integer> {
  @Option(names = "--customer-id", required = true)
  UUID customerId;

  final OrderService service;
  final java.util.function.Supplier<CliOutput> output;

  OrderCommand(OrderService service, java.util.function.Supplier<CliOutput> output) {
    this.service = service;
    this.output = output;
  }

  void validate() {}

  public Integer call() {
    validate();
    output.get().writeSuccess(execute());
    return 0;
  }

  abstract Object execute();

  void invalid(String message) {
    throw new OrderException(OrderErrorCode.INVALID_INPUT, message);
  }
}
