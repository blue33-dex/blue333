package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.Attachment;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.RequirementBaseline;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import com.blue333.ordermgmt.dto.order.OrderResponses.AttachmentResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.BaselineResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.MaterialSpecResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.OrderDetailResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.OrderSummaryResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.RejectReasonResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.TaskResponse;
import com.blue333.ordermgmt.repository.AttachmentRepository;
import com.blue333.ordermgmt.repository.ClientReviewRepository;
import com.blue333.ordermgmt.repository.DeliveryArchiveRepository;
import com.blue333.ordermgmt.repository.FeedbackItemRepository;
import com.blue333.ordermgmt.repository.MaterialSpecRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.repository.RequirementBaselineRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-model assembly performed in one transaction while all entity associations remain available. */
@Service
public class OrderQueryService {
  private final AccessControlService accessControl;
  private final OrderRepository orderRepository;
  private final MaterialSpecRepository materialSpecRepository;
  private final AttachmentRepository attachmentRepository;
  private final MaterialTaskRepository materialTaskRepository;
  private final RequirementBaselineRepository baselineRepository;
  private final ClientReviewRepository clientReviewRepository;
  private final FeedbackItemRepository feedbackItemRepository;
  private final DeliveryArchiveRepository deliveryArchiveRepository;
  private final ObjectMapper objectMapper;

  public OrderQueryService(AccessControlService accessControl, OrderRepository orderRepository,
      MaterialSpecRepository materialSpecRepository, AttachmentRepository attachmentRepository,
      MaterialTaskRepository materialTaskRepository, RequirementBaselineRepository baselineRepository,
      ClientReviewRepository clientReviewRepository, FeedbackItemRepository feedbackItemRepository,
      DeliveryArchiveRepository deliveryArchiveRepository, ObjectMapper objectMapper) {
    this.accessControl = accessControl;
    this.orderRepository = orderRepository;
    this.materialSpecRepository = materialSpecRepository;
    this.attachmentRepository = attachmentRepository;
    this.materialTaskRepository = materialTaskRepository;
    this.baselineRepository = baselineRepository;
    this.clientReviewRepository = clientReviewRepository;
    this.feedbackItemRepository = feedbackItemRepository;
    this.deliveryArchiveRepository = deliveryArchiveRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public CursorPage<OrderSummaryResponse> listMine(CurrentUser currentUser, String cursor, Integer limit) {
    int pageLimit = CursorPagination.normalizeLimit(limit);
    List<Order> rows = orderRepository.findPageByAmId(currentUser.id(), CursorPagination.decodeCursor(cursor),
        CursorPagination.pageable(pageLimit));
    return CursorPage.fromFetched(rows, pageLimit, Order::getId).map(this::summary);
  }

  @Transactional(readOnly = true)
  public CursorPage<OrderSummaryResponse> reviewQueue(String cursor, Integer limit) {
    int pageLimit = CursorPagination.normalizeLimit(limit);
    List<Order> rows = orderRepository.findPendingConfirmPage(CursorPagination.decodeCursor(cursor),
        CursorPagination.pageable(pageLimit));
    return CursorPage.fromFetched(rows, pageLimit, Order::getId).map(this::summary);
  }

  /** BO internal-review queue (M4): orders that are in production and may hold versions awaiting review. */
  @Transactional(readOnly = true)
  public CursorPage<OrderSummaryResponse> internalReviewQueue(String cursor, Integer limit) {
    int pageLimit = CursorPagination.normalizeLimit(limit);
    List<Order> rows = orderRepository.findPageByStatus(OrderStatus.IN_PRODUCTION,
        CursorPagination.decodeCursor(cursor), CursorPagination.pageable(pageLimit));
    return CursorPage.fromFetched(rows, pageLimit, Order::getId).map(this::summary);
  }

  /** BO archive queue (A-19/A-28): delivered orders, flagged with their archive state. */
  @Transactional(readOnly = true)
  public CursorPage<OrderSummaryResponse> completedQueue(String cursor, Integer limit) {
    int pageLimit = CursorPagination.normalizeLimit(limit);
    List<Order> rows = orderRepository.findPageByStatus(OrderStatus.COMPLETED,
        CursorPagination.decodeCursor(cursor), CursorPagination.pageable(pageLimit));
    return CursorPage.fromFetched(rows, pageLimit, Order::getId).map(this::archivedSummary);
  }

  @Transactional(readOnly = true)
  public OrderDetailResponse detail(Long orderId, CurrentUser currentUser) {
    return detail(accessControl.findVisibleOrder(orderId, currentUser));
  }

  /**
   * Builds the detail view for an order returned by a write service. That instance usually belongs to an
   * already-closed persistence context, so it is reloaded here before lazy associations are touched.
   */
  @Transactional(readOnly = true)
  public OrderDetailResponse detail(Order detachedOrder) {
    Order order = orderRepository.findById(detachedOrder.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Order does not exist: " + detachedOrder.getId()));
    List<MaterialSpecResponse> specs = materialSpecRepository.findAllByOrderId(order.getId()).stream()
        .map(this::spec).toList();
    List<AttachmentResponse> attachments = attachmentRepository.findAllByOrderIdOrderByCreatedAtAscIdAsc(order.getId())
        .stream().map(this::attachment).toList();
    List<TaskResponse> tasks = materialTaskRepository.findAllByOrderIdOrderByIdAsc(order.getId())
        .stream().map(this::task).toList();
    BaselineResponse baseline = baselineRepository.findFirstByOrderIdOrderByBaselineVersionDesc(order.getId())
        .map(this::baseline).orElse(null);
    return new OrderDetailResponse(order.getId(), order.getOrderNo(), order.getCustomerName(), order.getUseScene(),
        order.getDeliveryDeadline(), order.getRemark(), order.getStatus().name(), order.getAm().getId(),
        order.getAm().getName(), order.getCreatedAt(), order.getUpdatedAt(), specs, attachments, baseline, tasks,
        rejection(order));
  }

  private OrderSummaryResponse summary(Order order) {
    String reviewStatus = clientReviewRepository.findFirstByOrderIdOrderByIdDesc(order.getId())
        .map(review -> review.getStatus().name()).orElse(null);
    return new OrderSummaryResponse(order.getId(), order.getOrderNo(), order.getCustomerName(), order.getStatus().name(),
        order.getAm().getName(), order.getDeliveryDeadline(), order.getCreatedAt(),
        attachmentRepository.countByOrderId(order.getId()), materialTaskRepository.countByOrderId(order.getId()),
        reviewStatus, feedbackItemRepository.countByOrderIdAndStatus(order.getId(), FeedbackStatus.UNRESOLVED),
        null, null, rejection(order));
  }

  /** Completed-order summary enriched with the archive state (BO archive list). */
  private OrderSummaryResponse archivedSummary(Order order) {
    LocalDateTime archivedAt = deliveryArchiveRepository.findByOrderId(order.getId())
        .map(archive -> archive.getArchivedAt()).orElse(null);
    OrderSummaryResponse base = summary(order);
    return new OrderSummaryResponse(base.id(), base.orderNo(), base.customerName(), base.status(), base.amName(),
        base.deliveryDeadline(), base.createdAt(), base.attachmentCount(), base.taskCount(),
        base.clientReviewStatus(), base.openFeedbackCount(), archivedAt != null, archivedAt, base.rejectReason());
  }

  private MaterialSpecResponse spec(MaterialSpec item) { return new MaterialSpecResponse(item.getId(), item.getMaterialType().name(), item.getSpecSize()); }
  private AttachmentResponse attachment(Attachment item) { return new AttachmentResponse(item.getId(), item.getFileName(), item.getFileSize(), item.getCreatedAt()); }
  private TaskResponse task(MaterialTask item) { return new TaskResponse(item.getId(), item.getMaterialSpec().getMaterialType().name(), item.getMaterialSpec().getSpecSize(), item.getAssignee().getId(), item.getAssignee().getName(), item.getTaskDeadline(), item.getPriority().name(), item.getStatus().name()); }
  private RejectReasonResponse rejection(Order order) {
    return order.getLastRejectReasonType() == null ? null : new RejectReasonResponse(order.getLastRejectReasonType().name(), order.getLastRejectRemark(), order.getLastRejectedBy().getName(), order.getLastRejectedAt());
  }
  private BaselineResponse baseline(RequirementBaseline item) {
    return new BaselineResponse(item.getBaselineVersion(), item.getCustomerName(), item.getUseScene(), item.getDeliveryDeadline(), item.getRemark(), parseSnapshot(item.getMaterialSpecsSnapshot()), item.getCreatedAt());
  }
  private List<MaterialSpecResponse> parseSnapshot(String json) {
    try { return objectMapper.readValue(json, new TypeReference<List<SnapshotItem>>() {}).stream().map(i -> new MaterialSpecResponse(null, i.materialType(), i.specSize())).toList(); }
    catch (Exception exception) { throw new IllegalStateException("Stored requirement baseline is invalid", exception); }
  }
  private record SnapshotItem(String materialType, String specSize) {}
}
