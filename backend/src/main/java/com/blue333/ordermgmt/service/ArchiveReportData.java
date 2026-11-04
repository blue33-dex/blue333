package com.blue333.ordermgmt.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read model rendered into the A-19 archive PDF; assembled inside the archive transaction. */
public record ArchiveReportData(
    String orderNo,
    String customerName,
    String useScene,
    LocalDate deliveryDeadline,
    String remark,
    String orderStatus,
    String accountManager,
    LocalDateTime createdAt,
    List<TaskRow> tasks,
    List<VersionRow> versions,
    List<ClientReviewRow> clientReviews,
    List<FeedbackRow> feedbacks,
    List<ExceptionRow> exceptions,
    ConfirmationRow confirmation,
    List<TimelineRow> timeline) {

  public record TaskRow(
      String materialType, String specSize, String assignee, String priority,
      String status, LocalDateTime deadline) {
  }

  public record VersionRow(
      String versionNo, String materialType, String specSize, String status,
      String uploader, LocalDateTime submittedAt, boolean overdue) {
  }

  public record ClientReviewRow(
      String versionNo, String status, String initiator,
      LocalDateTime startedAt, LocalDateTime completedAt) {
  }

  public record FeedbackRow(
      String feedbackNo, String versionNo, String type, String source,
      String status, String createdBy, String content) {
  }

  public record ConfirmationRow(
      String versionNo, String confirmerName, LocalDateTime confirmedAt) {}

  public record ExceptionRow(
      String type, String status, String recorder, String description,
      String resolution, LocalDateTime createdAt) {}

  public record TimelineRow(
      LocalDateTime at, String action, String operator, String target, String state, String result) {
  }
}
