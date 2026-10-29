package io.github.zhyou.starter.api;

import jakarta.validation.constraints.*;

public record CreateOrderRequest(@NotBlank String description) {}
