package io.github.zhyou.starter.maintenance;

public enum DatabaseMode {
  SINGLE,
  SHARDING;

  public static DatabaseMode parse(String value) {
    return switch (value) {
      case "single" -> SINGLE;
      case "sharding" -> SHARDING;
      default -> throw new IllegalArgumentException("APP_DB_MODE must be single or sharding");
    };
  }
}
