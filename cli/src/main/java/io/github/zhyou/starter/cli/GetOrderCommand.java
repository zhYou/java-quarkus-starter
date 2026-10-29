package io.github.zhyou.starter.cli;

import io.github.zhyou.starter.application.OrderService;

@picocli.CommandLine.Command(name = "get", mixinStandardHelpOptions = true)
@jakarta.enterprise.inject.Vetoed
public class GetOrderCommand extends OrderCommand {
  public GetOrderCommand(OrderService service, java.util.function.Supplier<CliOutput> output) {
    super(service, output);
  }

  @picocli.CommandLine.Option(names = "--id", required = true)
  java.util.UUID id;

  Object execute() {
    return service.get(new io.github.zhyou.starter.domain.OrderKey(customerId, id));
  }
}
