package io.github.zhyou.starter.cli;

import io.github.zhyou.starter.domain.*;

public final class CliErrors {
  private CliErrors() {}

  public static int exitCode(OrderErrorCode code) {
    return switch (code) {
      case INVALID_INPUT -> 2;
      case ORDER_NOT_FOUND -> 3;
      case ORDER_CONFLICT -> 4;
      case INTERNAL_ERROR -> 1;
    };
  }
}
