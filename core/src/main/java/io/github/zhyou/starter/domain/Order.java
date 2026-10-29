package io.github.zhyou.starter.domain;

import java.time.Instant;
import java.util.UUID;

public record Order(
    UUID customerId, UUID id, String description, OrderStatus status, Instant createdAt) {}
