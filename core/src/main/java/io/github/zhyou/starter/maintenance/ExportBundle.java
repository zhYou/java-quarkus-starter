package io.github.zhyou.starter.maintenance;

import io.github.zhyou.starter.domain.Order;
import java.util.List;

public record ExportBundle(int formatVersion, List<Order> orders) {}
