package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.AuditLog;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.dto.timeline.TimelineResponses.TimelineEntryResponse;
import com.blue333.ordermgmt.repository.AuditLogRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A-29 order timeline. Visibility follows BR-13 through {@link AccessControlService}, so an AM only
 * sees their own orders and a DS only the orders holding one of their tasks.
 */
@Service
public class TimelineService {
  private static final Map<AuditActionType, String> ACTION_LABELS = Map.ofEntries(
      Map.entry(AuditActionType.CREATE_ORDER, "创建订单"),
      Map.entry(AuditActionType.SUBMIT_ORDER, "提交订单"),
      Map.entry(AuditActionType.REJECT_ORDER, "退回订单"),
      Map.entry(AuditActionType.ASSIGN_TASK, "派发任务"),
      Map.entry(AuditActionType.REASSIGN_TASK, "重新派发"),
      Map.entry(AuditActionType.CANCEL_ORDER, "取消订单"),
      Map.entry(AuditActionType.CANCEL_TASK, "取消任务"),
      Map.entry(AuditActionType.UPLOAD_VERSION, "上传版本"),
      Map.entry(AuditActionType.SUBMIT_REVIEW, "提交内部评审"),
      Map.entry(AuditActionType.RELEASE_VERSION, "放行版本"),
      Map.entry(AuditActionType.REJECT_VERSION, "退回版本"),
      Map.entry(AuditActionType.MARK_FINAL, "标记终稿"),
      Map.entry(AuditActionType.RECORD_FEEDBACK, "记录客户反馈"),
      Map.entry(AuditActionType.CONFIRM_VERSION, "客户确认"),
      Map.entry(AuditActionType.MARK_DELIVERED, "标记已交付"),
      Map.entry(AuditActionType.ARCHIVE_ORDER, "归档订单"),
      Map.entry(AuditActionType.RECORD_EXCEPTION, "记录异常"),
      Map.entry(AuditActionType.CLOSE_EXCEPTION, "关闭异常"));

  private final AuditLogRepository auditLogRepository;
  private final AccessControlService accessControl;

  public TimelineService(AuditLogRepository auditLogRepository, AccessControlService accessControl) {
    this.auditLogRepository = auditLogRepository;
    this.accessControl = accessControl;
  }

  @Transactional(readOnly = true)
  public CursorPage<TimelineEntryResponse> timeline(Long orderId, CurrentUser currentUser, String cursor,
      Integer limit) {
    accessControl.findVisibleOrder(orderId, currentUser);
    int pageLimit = CursorPagination.normalizeLimit(limit);
    List<AuditLog> rows = auditLogRepository.findForOrderTimeline(orderId,
        AuditTargetType.ORDER, AuditTargetType.MATERIAL_TASK, AuditTargetType.DESIGN_VERSION,
        AuditTargetType.FEEDBACK_ITEM, AuditTargetType.CONFIRMATION_RECORD,
        AuditTargetType.EXCEPTION_RECORD, CursorPagination.decodeCursor(cursor),
        CursorPagination.pageable(pageLimit));
    return CursorPage.fromFetched(rows, pageLimit, AuditLog::getId).map(this::entry);
  }

  private TimelineEntryResponse entry(AuditLog log) {
    return new TimelineEntryResponse(log.getId(), log.getActionType().name(),
        ACTION_LABELS.getOrDefault(log.getActionType(), log.getActionType().name()),
        log.getTargetType().name(), log.getTargetId(), log.getOperator().getId(),
        log.getOperator().getName(), log.getBeforeState(), log.getAfterState(), log.getResult(),
        log.getRemark(), log.getOperatedAt());
  }
}
