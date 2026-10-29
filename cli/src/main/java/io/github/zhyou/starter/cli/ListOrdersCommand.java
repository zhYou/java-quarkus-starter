package io.github.zhyou.starter.cli;

import io.github.zhyou.starter.application.OrderService;

@picocli.CommandLine.Command(name = "list", mixinStandardHelpOptions = true)
@jakarta.enterprise.inject.Vetoed
public class ListOrdersCommand extends OrderCommand {
  public ListOrdersCommand(OrderService service, java.util.function.Supplier<CliOutput> output) {
    super(service, output);
  }

  @picocli.CommandLine.Option(names = "--limit", defaultValue = "20")
  int limit;

  @Override
  void validate() {
    if (limit < 1 || limit > 100) invalid("Limit must be 1 to 100");
  }

  Object execute() {
    return service.list(customerId, limit);
  }
}
