package io.github.zhyou.starter.cli;

import io.github.zhyou.starter.application.OrderService;

@picocli.CommandLine.Command(name = "create", mixinStandardHelpOptions = true)
@jakarta.enterprise.inject.Vetoed
public class CreateOrderCommand extends OrderCommand {
  public CreateOrderCommand(OrderService service, java.util.function.Supplier<CliOutput> output) {
    super(service, output);
  }

  @picocli.CommandLine.Option(names = "--description", required = true)
  String description;

  @Override
  void validate() {
    if (description == null || description.strip().isEmpty() || description.strip().length() > 200)
      invalid("Description must contain 1 to 200 characters");
  }

  Object execute() {
    return service.create(customerId, description);
  }
}
