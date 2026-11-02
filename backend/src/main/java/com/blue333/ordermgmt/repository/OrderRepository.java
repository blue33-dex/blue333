package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
