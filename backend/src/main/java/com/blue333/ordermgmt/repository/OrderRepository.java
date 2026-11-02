package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Order;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select order from Order order where order.id = :orderId")
  Optional<Order> findByIdForUpdate(Long orderId);
}
