package io.github.zhyou.starter.api;

public record ProblemResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String code,
    String requestId) {}
