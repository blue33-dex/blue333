package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  /** Notification center list: newest first, optionally filtered to unread only. */
  @Query("select n from Notification n where n.recipient.id = :recipientId"
      + " and (:unreadOnly = false or n.read = false)"
      + " and (:cursor is null or n.id < :cursor) order by n.id desc")
  List<Notification> findPageForRecipient(@Param("recipientId") Long recipientId,
      @Param("unreadOnly") boolean unreadOnly, @Param("cursor") Long cursor, Pageable pageable);

  long countByRecipientIdAndReadFalse(Long recipientId);

  /** Scoped lookup so one user can never read another user's notification. */
  Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Notification n set n.read = true, n.readAt = :readAt"
      + " where n.recipient.id = :recipientId and n.read = false")
  int markAllRead(@Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);
}
