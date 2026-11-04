package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false)
  private User recipient;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 20)
  private NotificationType notificationType;

  @Column(name = "resource_type", nullable = false, length = 30)
  private String resourceType;

  @Column(name = "resource_id", nullable = false)
  private Long resourceId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "is_read", nullable = false)
  private Boolean read = false;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Notification() {
  }

  /** Creates a new unread notification with the required persisted fields. */
  public static Notification create(
      User recipient,
      NotificationType notificationType,
      String resourceType,
      Long resourceId,
      String title,
      String content) {
    Notification notification = new Notification();
    notification.recipient = recipient;
    notification.notificationType = notificationType;
    notification.resourceType = resourceType;
    notification.resourceId = resourceId;
    notification.title = title;
    notification.content = content;
    return notification;
  }

  public Long getId() { return id; }
  public User getRecipient() { return recipient; }
  public NotificationType getNotificationType() { return notificationType; }
  public String getResourceType() { return resourceType; }
  public Long getResourceId() { return resourceId; }
  public String getTitle() { return title; }
  public String getContent() { return content; }
  public Boolean getRead() { return read; }
  public LocalDateTime getReadAt() { return readAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }

  /** Marking an already-read notification again keeps the original read timestamp. */
  public void markRead(LocalDateTime now) {
    if (Boolean.TRUE.equals(read)) {
      return;
    }
    this.read = true;
    this.readAt = now;
  }
}
