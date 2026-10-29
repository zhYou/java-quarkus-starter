package io.github.zhyou.starter.domain;

public class OrderException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final OrderErrorCode code;

  public OrderException(OrderErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public OrderErrorCode code() {
    return code;
  }
}
