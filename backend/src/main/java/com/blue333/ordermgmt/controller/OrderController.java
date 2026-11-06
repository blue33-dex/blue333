package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.domain.Attachment;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.order.CreateOrderCommand;
import com.blue333.ordermgmt.dto.order.MaterialSpecCommand;
import com.blue333.ordermgmt.dto.order.OrderResponses.AttachmentResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.OrderDetailResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.OrderSummaryResponse;
import com.blue333.ordermgmt.dto.order.RegisterAttachmentCommand;
import com.blue333.ordermgmt.dto.order.RejectOrderCommand;
import com.blue333.ordermgmt.dto.storage.PresignedUrlResponse;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.service.AccessControlService;
import com.blue333.ordermgmt.service.AttachmentFilePolicyValidator;
import com.blue333.ordermgmt.service.CurrentUser;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.blue333.ordermgmt.service.FileStorageService;
import com.blue333.ordermgmt.service.InvalidStateTransitionException;
import com.blue333.ordermgmt.service.KanbanService;
import com.blue333.ordermgmt.service.MaterialTaskService;
import com.blue333.ordermgmt.service.OrderQueryService;
import com.blue333.ordermgmt.service.OrderService;
import com.blue333.ordermgmt.service.ResourceNotFoundException;
import com.blue333.ordermgmt.service.TimelineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonProperty;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
  private final CurrentUserService currentUserService;
  private final AccessControlService accessControl;
  private final OrderService orderService;
  private final OrderQueryService orderQueryService;
  private final MaterialTaskService materialTaskService;
  private final AttachmentFilePolicyValidator attachmentPolicy;
  private final FileStorageService fileStorageService;
  private final UserRepository userRepository;
  private final KanbanService kanbanService;
  private final TimelineService timelineService;

  public OrderController(CurrentUserService currentUserService, AccessControlService accessControl,
      OrderService orderService, OrderQueryService orderQueryService, MaterialTaskService materialTaskService,
      AttachmentFilePolicyValidator attachmentPolicy, FileStorageService fileStorageService,
      UserRepository userRepository, KanbanService kanbanService, TimelineService timelineService) {
    this.currentUserService = currentUserService;
    this.accessControl = accessControl;
    this.orderService = orderService;
    this.orderQueryService = orderQueryService;
    this.materialTaskService = materialTaskService;
    this.attachmentPolicy = attachmentPolicy;
    this.fileStorageService = fileStorageService;
    this.userRepository = userRepository;
    this.kanbanService = kanbanService;
    this.timelineService = timelineService;
  }

  @PostMapping
  @PreAuthorize("hasRole('AM')")
  public ResponseEntity<ApiResponse<OrderDetailResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    Order order = orderService.createOrder(new CreateOrderCommand(request.customerName(), request.useScene(),
        request.deliveryDeadline(), request.remark(), request.materialSpecs().stream()
            .map(s -> new MaterialSpecCommand(s.materialType(), s.specSize())).toList()), current.user());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(orderQueryService.detail(order)));
  }

  @PostMapping("/{orderId:\\d+}/attachments/presign")
  @PreAuthorize("hasRole('AM')")
  public ApiResponse<PresignedUrlResponse> presign(@PathVariable Long orderId,
      @Valid @RequestBody PresignAttachmentRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    Order order = accessControl.findOwnedOrderForAm(orderId, current);
    if (order.getStatus() != OrderStatus.PENDING_CONFIRM) throw new InvalidStateTransitionException("Attachments can only be uploaded while order is pending confirmation");
    attachmentPolicy.validate(request.fileName(), request.fileSize());
    String key = fileStorageService.generateObjectKey("attachments/order-" + orderId, request.fileName());
    return ApiResponse.of(fileStorageService.generatePresignedPutUrl(key));
  }

  @PostMapping("/{orderId:\\d+}/attachments")
  @PreAuthorize("hasRole('AM')")
  public ResponseEntity<ApiResponse<AttachmentResponse>> confirmAttachment(@PathVariable Long orderId,
      @Valid @RequestBody ConfirmAttachmentRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    accessControl.findOwnedOrderForAm(orderId, current);
    attachmentPolicy.validate(request.fileName(), request.fileSize());
    if (!request.objectKey().startsWith("attachments/order-" + orderId + "/")) throw new IllegalArgumentException("object_key does not belong to this order");
    long actualSize = fileStorageService.findObjectSize(request.objectKey())
        .orElseThrow(() -> new IllegalStateException("Uploaded object does not exist"));
    if (actualSize != request.fileSize()) throw new IllegalStateException("Uploaded object size does not match file_size");
    Attachment attachment = orderService.registerStoredAttachment(orderId,
        new RegisterAttachmentCommand(request.fileName(), request.fileSize(), request.objectKey()), current.user());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(new AttachmentResponse(
        attachment.getId(), attachment.getFileName(), attachment.getFileSize(), attachment.getCreatedAt())));
  }

  @PostMapping("/{orderId:\\d+}/submit")
  @PreAuthorize("hasRole('AM')")
  public ApiResponse<OrderDetailResponse> submit(@PathVariable Long orderId) {
    CurrentUser current = currentUserService.requireCurrentUser();
    accessControl.findOwnedOrderForAm(orderId, current);
    return ApiResponse.of(orderQueryService.detail(orderService.submitOrder(orderId)));
  }

  @GetMapping("/mine")
  @PreAuthorize("hasRole('AM')")
  public ApiResponse<List<OrderSummaryResponse>> mine(@RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ApiResponse.page(orderQueryService.listMine(currentUserService.requireCurrentUser(), cursor, limit));
  }

  @GetMapping("/review-queue")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<List<OrderSummaryResponse>> reviewQueue(@RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    currentUserService.requireCurrentUser();
    return ApiResponse.page(orderQueryService.reviewQueue(cursor, limit));
  }

  /** M4 BO internal-review queue: orders in production whose versions can be compared, released or rejected. */
  @GetMapping("/internal-review-queue")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<List<OrderSummaryResponse>> internalReviewQueue(@RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    currentUserService.requireCurrentUser();
    return ApiResponse.page(orderQueryService.internalReviewQueue(cursor, limit));
  }

  /** BO archive queue (A-19/A-28): delivered orders with their archive state. */
  @GetMapping("/completed")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<List<OrderSummaryResponse>> completedQueue(@RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    currentUserService.requireCurrentUser();
    return ApiResponse.page(orderQueryService.completedQueue(cursor, limit));
  }

  @GetMapping("/{orderId:\\d+}/review")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<OrderDetailResponse> review(@PathVariable Long orderId) {
    return ApiResponse.of(orderQueryService.detail(orderId, currentUserService.requireCurrentUser()));
  }

  /** A-25 BO kanban: four status columns, optional AM/customer/date filters. */
  @GetMapping("/kanban")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<com.blue333.ordermgmt.dto.kanban.KanbanResponses.KanbanResponse> kanban(
      @RequestParam(name = "am_id", required = false) Long amId,
      @RequestParam(required = false) String customer,
      @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to) {
    currentUserService.requireCurrentUser();
    return ApiResponse.of(kanbanService.kanban(amId, customer, from, to));
  }

  /** A-29 order timeline: visible to AM/BO/DS under BR-13 scoping. */
  @GetMapping("/{orderId:\\d+}/timeline")
  @PreAuthorize("hasAnyRole('AM','BO','DS')")
  public ApiResponse<List<com.blue333.ordermgmt.dto.timeline.TimelineResponses.TimelineEntryResponse>> timeline(
      @PathVariable Long orderId, @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ApiResponse.page(timelineService.timeline(orderId,
        currentUserService.requireCurrentUser(), cursor, limit));
  }

  @GetMapping("/{orderId:\\d+}")
  @PreAuthorize("hasAnyRole('AM','BO','DS')")
  public ApiResponse<OrderDetailResponse> detail(@PathVariable Long orderId) {
    return ApiResponse.of(orderQueryService.detail(orderId, currentUserService.requireCurrentUser()));
  }

  @PostMapping("/{orderId:\\d+}/reject")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<OrderDetailResponse> reject(@PathVariable Long orderId, @Valid @RequestBody RejectOrderRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    Order order = orderService.rejectOrder(new RejectOrderCommand(orderId, request.reasonType(), request.remark()), current.user());
    return ApiResponse.of(orderQueryService.detail(order));
  }

  @PostMapping("/{orderId:\\d+}/assign")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<OrderDetailResponse> assign(@PathVariable Long orderId, @Valid @RequestBody AssignOrderRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    User assignee = userRepository.findById(request.assigneeId()).orElseThrow(() -> new ResourceNotFoundException("User does not exist: " + request.assigneeId()));
    materialTaskService.assignOrder(orderId, current.user(), assignee, request.taskDeadline(), request.priority(), request.remark());
    return ApiResponse.of(orderQueryService.detail(orderId, current));
  }

  /**
   * A-22 cancel. AM may cancel their own pending-confirmation draft; BO may cancel an in-production
   * order and must state a reason. IN_PRODUCTION cancellation cascades to every non-terminal task.
   */
  @PostMapping("/{orderId:\\d+}/cancel")
  @PreAuthorize("hasAnyRole('AM','BO')")
  public ApiResponse<OrderDetailResponse> cancel(@PathVariable Long orderId,
      @RequestBody(required = false) CancelOrderRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    boolean businessOwner = current.hasRole(com.blue333.ordermgmt.domain.enums.RoleType.BO);
    Order order = businessOwner
        ? accessControl.findVisibleOrder(orderId, current)
        : accessControl.findOwnedOrderForAm(orderId, current);
    String reason = request == null ? null : request.reason();
    if (businessOwner) {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("A cancellation reason is required");
      }
      if (order.getStatus() != OrderStatus.IN_PRODUCTION) {
        throw new InvalidStateTransitionException(
            "Business owners cancel in-production orders only (current: " + order.getStatus() + ")");
      }
    } else if (order.getStatus() != OrderStatus.PENDING_CONFIRM) {
      throw new InvalidStateTransitionException(
          "Account managers cancel pending-confirmation drafts only (current: " + order.getStatus() + ")");
    }
    return ApiResponse.of(orderQueryService.detail(orderService.cancelOrder(orderId, reason)));
  }

  /** A-18: the owning AM marks the client-confirmed order as delivered (IN_PRODUCTION → COMPLETED). */
  @PostMapping("/{orderId:\\d+}/deliver")
  @PreAuthorize("hasRole('AM')")
  public ApiResponse<OrderDetailResponse> deliver(@PathVariable Long orderId) {
    CurrentUser current = currentUserService.requireCurrentUser();
    accessControl.findOwnedOrderForAm(orderId, current);
    return ApiResponse.of(orderQueryService.detail(orderService.markDelivered(orderId)));
  }

  public record MaterialSpecRequest(
      @NotNull @JsonProperty("material_type") com.blue333.ordermgmt.domain.enums.MaterialType materialType,
      @NotBlank(message = "规格尺寸必填")
      @Size(max = 100, message = "规格尺寸不能超过 100 个字符")
      @Pattern(regexp = "^(?:[A-Za-z]{1,2}\\d{1,3}|\\d+(?:\\.\\d+)?\\s*[A-Za-z\\u4e00-\\u9fa5]{1,6}(?:\\s*[xX×*/]\\s*\\d+(?:\\.\\d+)?\\s*[A-Za-z\\u4e00-\\u9fa5]{1,6})?)$",
          message = "规格尺寸格式不正确，请使用 600mm*900mm 或 A4 这类写法")
      @JsonProperty("spec_size") String specSize) {}
  public record CreateOrderRequest(@NotBlank @JsonProperty("customer_name") String customerName, @NotBlank @JsonProperty("use_scene") String useScene, @NotNull @JsonProperty("delivery_deadline") LocalDate deliveryDeadline, @JsonProperty("remark") String remark, @NotNull @JsonProperty("material_specs") List<@Valid MaterialSpecRequest> materialSpecs) {}
  public record PresignAttachmentRequest(@NotBlank @JsonProperty("file_name") String fileName, @Positive @JsonProperty("file_size") long fileSize) {}
  public record ConfirmAttachmentRequest(@NotBlank @JsonProperty("file_name") String fileName, @Positive @JsonProperty("file_size") long fileSize, @NotBlank @JsonProperty("object_key") String objectKey) {}
  public record RejectOrderRequest(@NotNull @JsonProperty("reject_reason_type") com.blue333.ordermgmt.domain.enums.RejectReasonType reasonType, @NotBlank @JsonProperty("remark") String remark) {}
  public record AssignOrderRequest(@NotNull @Positive @JsonProperty("assignee_id") Long assigneeId, @NotNull @JsonProperty("task_deadline") LocalDateTime taskDeadline, @NotNull @JsonProperty("priority") com.blue333.ordermgmt.domain.enums.Priority priority, @JsonProperty("remark") String remark) {}
  public record CancelOrderRequest(@JsonProperty("reason") String reason) {}
}
