package com.blue333.ordermgmt.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Generates unique order business identifiers in the ORD-YYYYMMDD-NNN format.
 *
 * <p>Call this component from the same transaction that inserts the Order. The
 * locked order_no index range keeps concurrent requests for one calendar day
 * from reading the same highest sequence before either transaction commits.</p>
 */
@Component
public class IdGenerator {
  private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String ORDER_PREFIX = "ORD-";

  private final JdbcTemplate jdbcTemplate;
  private final Clock applicationClock;

  public IdGenerator(JdbcTemplate jdbcTemplate, Clock applicationClock) {
    this.jdbcTemplate = jdbcTemplate;
    this.applicationClock = applicationClock;
  }

  @Transactional(propagation = Propagation.MANDATORY, isolation = Isolation.REPEATABLE_READ)
  public String generateOrderNo() {
    return generateOrderNo(LocalDate.now(applicationClock.withZone(ZoneOffset.UTC)));
  }

  /**
   * Generates an order number for a supplied date. This overload permits
   * deterministic tests while preserving the production locking behaviour.
   */
  @Transactional(propagation = Propagation.MANDATORY, isolation = Isolation.REPEATABLE_READ)
  public String generateOrderNo(LocalDate orderDate) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("IdGenerator must run inside the order creation transaction");
    }

    String prefix = ORDER_PREFIX + ORDER_DATE_FORMAT.format(orderDate) + "-";
    String nextPrefix = ORDER_PREFIX + ORDER_DATE_FORMAT.format(orderDate.plusDays(1)) + "-";

    List<String> matchingOrderNumbers = jdbcTemplate.query(
        "SELECT order_no FROM orders "
            + "WHERE order_no >= ? AND order_no < ? "
            + "ORDER BY order_no DESC LIMIT 1 FOR UPDATE",
        (resultSet, rowNumber) -> resultSet.getString("order_no"),
        prefix,
        nextPrefix);

    int nextSequence = matchingOrderNumbers.isEmpty()
        ? 1
        : extractSequence(matchingOrderNumbers.get(0), prefix) + 1;
    return prefix + String.format("%03d", nextSequence);
  }

  private int extractSequence(String orderNo, String prefix) {
    try {
      return Integer.parseInt(orderNo.substring(prefix.length()));
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Stored order number does not match expected format: " + orderNo, exception);
    }
  }
}
