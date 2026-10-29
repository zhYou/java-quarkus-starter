package io.github.zhyou.starter.cli;

import io.github.zhyou.starter.application.OrderService;
import io.github.zhyou.starter.domain.*;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import java.io.PrintWriter;
import java.util.*;
import picocli.CommandLine;

@QuarkusMain
public final class StarterCli {
  private StarterCli() {}

  public static CommandLine commandLine(OrderService service, PrintWriter out, PrintWriter err) {
    var root = new RootCommand();
    var line = new CommandLine(root);
    line.setOut(out);
    line.setErr(err);
    java.util.function.Supplier<CliOutput> output =
        () -> new CliOutput(root.output == RootCommand.Output.json, out, err);
    var orders = new CommandLine(new OrdersCommand());
    orders.addSubcommand(new CreateOrderCommand(service, output));
    orders.addSubcommand(new GetOrderCommand(service, output));
    orders.addSubcommand(new ListOrdersCommand(service, output));
    orders.addSubcommand(new CompleteOrderCommand(service, output));
    line.addSubcommand(orders);
    line.setOut(out);
    line.setErr(err);
    line.setExecutionExceptionHandler(
        (ex, cmd, parsed) -> {
          if (ex instanceof OrderException e) {
            output.get().writeFailure(e.code(), e.getMessage());
            return CliErrors.exitCode(e.code());
          }
          output
              .get()
              .writeFailure(
                  OrderErrorCode.INTERNAL_ERROR,
                  "Order operation failed; check database connectivity");
          err.println("Order operation failed (internal error)");
          err.flush();
          return 1;
        });
    line.setParameterExceptionHandler(
        (ex, args) -> {
          output
              .get()
              .writeFailure(OrderErrorCode.INVALID_INPUT, "Invalid command arguments; use --help");
          return 2;
        });
    return line;
  }

  public static int preflight(String[] args, PrintWriter out, PrintWriter err) {
    var line = commandLine(null, out, err);
    try {
      var parsed = line.parseArgs(args);
      if (CommandLine.printHelpIfRequested(parsed)) return 0;
      while (parsed.hasSubcommand()) parsed = parsed.subcommand();
      if (!(parsed.commandSpec().userObject() instanceof OrderCommand command)) {
        parsed.commandSpec().commandLine().usage(out);
        return 0;
      }
      command.validate();
      return -1;
    } catch (CommandLine.ParameterException | OrderException e) {
      new CliOutput(jsonRequested(args), out, err)
          .writeFailure(OrderErrorCode.INVALID_INPUT, "Invalid command arguments; use --help");
      return 2;
    }
  }

  private static boolean jsonRequested(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--output=json")
          || (args[i].equals("--output") && i + 1 < args.length && args[i + 1].equals("json")))
        return true;
    }
    return false;
  }

  public static void main(String[] args) {
    var out = new PrintWriter(System.out, true);
    var err = new PrintWriter(System.err, true);
    int validation = preflight(args, out, err);
    if (validation >= 0) {
      System.exit(validation);
      return;
    }
    Quarkus.run(
        CliApplication.class,
        (code, failure) -> {
          if (failure != null) {
            new CliOutput(jsonRequested(args), out, err)
                .writeFailure(
                    OrderErrorCode.INTERNAL_ERROR,
                    "Application startup failed; check database configuration");
            err.println("Application startup failed (configuration or database error)");
            System.exit(1);
          } else System.exit(code);
        },
        args);
  }
}
