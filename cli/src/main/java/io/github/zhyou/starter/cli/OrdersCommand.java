package io.github.zhyou.starter.cli;

import picocli.CommandLine.Command;

@Command(
    name = "orders",
    mixinStandardHelpOptions = true,
    description = "Create, inspect and complete orders")
@jakarta.enterprise.inject.Vetoed
public class OrdersCommand {}
