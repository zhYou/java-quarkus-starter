package io.github.zhyou.starter.maintenance;

import com.fasterxml.jackson.databind.*;
import io.github.zhyou.starter.domain.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.*;

public final class SampleDataTransfer {
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private SampleDataTransfer() {}

  public static ExportBundle readBundle(Path path) {
    try {
      if (Files.size(path) > 50_000_000L)
        throw new IllegalArgumentException("Sample export exceeds 50 MB limit");
      var bundle = MAPPER.readValue(path.toFile(), ExportBundle.class);
      if (bundle == null || bundle.formatVersion() != 1 || bundle.orders() == null)
        throw new IllegalArgumentException("Unsupported sample export format");
      var keys = new HashSet<OrderKey>();
      for (var order : bundle.orders()) {
        if (order == null
            || order.customerId() == null
            || order.id() == null
            || order.description() == null
            || order.description().isBlank()
            || order.description().length() > 200
            || order.createdAt() == null
            || order.status() == null)
          throw new IllegalArgumentException("Invalid order in sample export");
        if (!keys.add(new OrderKey(order.customerId(), order.id())))
          throw new IllegalArgumentException("Duplicate key in sample export");
      }
      return bundle;
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid sample export file");
    }
  }

  public static void exportData(Connection connection, Path path) throws SQLException, IOException {
    var orders = new ArrayList<Order>();
    try (var st = connection.createStatement();
        var rows =
            st.executeQuery(
                "select customer_id,id,description,status,created_at from orders order by customer_id,id")) {
      while (rows.next())
        orders.add(
            new Order(
                rows.getObject(1, UUID.class),
                rows.getObject(2, UUID.class),
                rows.getString(3),
                OrderStatus.valueOf(rows.getString(4)),
                rows.getTimestamp(5).toInstant()));
    }
    Path parent = path.toAbsolutePath().getParent();
    Path temp = Files.createTempFile(parent, ".starter-export-", ".tmp");
    try {
      try {
        Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
      }
      MAPPER.writeValue(temp.toFile(), new ExportBundle(1, orders));
      Files.move(temp, path);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public static void importData(Connection connection, Path path) throws SQLException {
    ExportBundle bundle = readBundle(path);
    if (!connection.getAutoCommit())
      throw new IllegalArgumentException("Import requires a dedicated connection");
    connection.setAutoCommit(false);
    try {
      try (var st = connection.createStatement();
          var rows = st.executeQuery("select count(*) from orders")) {
        rows.next();
        if (rows.getLong(1) != 0) throw new IllegalArgumentException("Import target must be empty");
      }
      try (var st =
          connection.prepareStatement(
              "insert into orders(customer_id,id,description,status,created_at) values (?,?,?,?,?)")) {
        for (var order : bundle.orders()) {
          st.setObject(1, order.customerId());
          st.setObject(2, order.id());
          st.setString(3, order.description());
          st.setString(4, order.status().name());
          st.setTimestamp(5, Timestamp.from(order.createdAt()));
          st.executeUpdate();
        }
      }
      connection.commit();
    } catch (SQLException | RuntimeException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  public static void main(String[] args) {
    try {
      if (args.length != 2 || (!args[0].equals("export") && !args[0].equals("import")))
        throw new IllegalArgumentException("Expected export|import and file path");
      var env = System.getenv();
      try (var connection =
          DriverManager.getConnection(
              env.get("APP_DB_JDBC_URL"), env.get("APP_DB_USERNAME"), env.get("APP_DB_PASSWORD"))) {
        if (args[0].equals("export")) exportData(connection, Path.of(args[1]));
        else importData(connection, Path.of(args[1]));
      }
    } catch (Exception e) {
      System.err.println(
          "Sample transfer failed; check input format, empty target and database access");
      System.exit(1);
    }
  }
}
