package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Order persistence. Visibility conditions (BR-13) are part of the queries themselves so callers
 * cannot accidentally fetch rows outside the current user's scope. Cursor queries return rows in
 * descending id order; a {@code null} cursor means the first page.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select order from Order order where order.id = :orderId")
  Optional<Order> findByIdForUpdate(Long orderId);

  /** AM scope: only orders owned by {@code amId}. */
  @Query("select o from Order o where o.am.id = :amId and (:cursor is null or o.id < :cursor)"
      + " order by o.id desc")
  List<Order> findPageByAmId(
      @Param("amId") Long amId, @Param("cursor") Long cursor, Pageable pageable);

  @Query("select o from Order o where o.status = :status and (:cursor is null or o.id < :cursor)"
      + " order by o.id desc")
  List<Order> findPageByStatus(
      @Param("status") OrderStatus status, @Param("cursor") Long cursor, Pageable pageable);

  /** BO review queue: orders waiting for confirmation. */
  default List<Order> findPendingConfirmPage(Long cursor, Pageable pageable) {
    return findPageByStatus(OrderStatus.PENDING_CONFIRM, cursor, pageable);
  }

  /** DS scope: an order is visible when at least one of its material tasks is assigned to {@code dsId}. */
  @Query("select case when count(t) > 0 then true else false end from MaterialTask t"
      + " where t.order.id = :orderId and t.assignee.id = :dsId")
  boolean existsTaskAssignedTo(@Param("orderId") Long orderId, @Param("dsId") Long dsId);
}
