package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
