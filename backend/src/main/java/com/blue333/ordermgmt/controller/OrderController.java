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
import com.blue333.ordermgmt.service.MaterialTaskService;
import com.blue333.ordermgmt.service.OrderQueryService;
import com.blue333.ordermgmt.service.OrderService;
import com.blue333.ordermgmt.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

  public OrderController(CurrentUserService currentUserService, AccessControlService accessControl,
      OrderService orderService, OrderQueryService orderQueryService, MaterialTaskService materialTaskService,
      AttachmentFilePolicyValidator attachmentPolicy, FileStorageService fileStorageService,
      UserRepository userRepository) {
    this.currentUserService = currentUserService;
    this.accessControl = accessControl;
    this.orderService = orderService;
    this.orderQueryService = orderQueryService;
    this.materialTaskService = materialTaskService;
    this.attachmentPolicy = attachmentPolicy;
    this.fileStorageService = fileStorageService;
    this.userRepository = userRepository;
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

  @GetMapping("/{orderId:\\d+}/review")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<OrderDetailResponse> review(@PathVariable Long orderId) {
    return ApiResponse.of(orderQueryService.detail(orderId, currentUserService.requireCurrentUser()));
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

  @PostMapping("/{orderId:\\d+}/cancel")
  @PreAuthorize("hasAnyRole('AM','BO')")
  public ApiResponse<OrderDetailResponse> cancel(@PathVariable Long orderId, @RequestBody(required = false) CancelOrderRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    if (current.hasRole(com.blue333.ordermgmt.domain.enums.RoleType.AM) && !current.hasRole(com.blue333.ordermgmt.domain.enums.RoleType.BO)) accessControl.findOwnedOrderForAm(orderId, current);
    else accessControl.findVisibleOrder(orderId, current);
    if (current.hasRole(com.blue333.ordermgmt.domain.enums.RoleType.BO) && (request == null || request.reason() == null || request.reason().isBlank())) throw new IllegalArgumentException("A cancellation reason is required");
    Order beforeCancel = accessControl.findVisibleOrder(orderId, current);
    if (beforeCancel.getStatus() == OrderStatus.IN_PRODUCTION) throw new InvalidStateTransitionException("Production-order cascade cancellation will be provided in Phase 4");
    return ApiResponse.of(orderQueryService.detail(orderService.cancelOrder(orderId)));
  }

  public record MaterialSpecRequest(@NotNull @JsonProperty("material_type") com.blue333.ordermgmt.domain.enums.MaterialType materialType, @NotBlank @JsonProperty("spec_size") String specSize) {}
  public record CreateOrderRequest(@NotBlank @JsonProperty("customer_name") String customerName, @NotBlank @JsonProperty("use_scene") String useScene, @NotNull @JsonProperty("delivery_deadline") LocalDate deliveryDeadline, @JsonProperty("remark") String remark, @NotNull @JsonProperty("material_specs") List<@Valid MaterialSpecRequest> materialSpecs) {}
  public record PresignAttachmentRequest(@NotBlank @JsonProperty("file_name") String fileName, @Positive @JsonProperty("file_size") long fileSize) {}
  public record ConfirmAttachmentRequest(@NotBlank @JsonProperty("file_name") String fileName, @Positive @JsonProperty("file_size") long fileSize, @NotBlank @JsonProperty("object_key") String objectKey) {}
  public record RejectOrderRequest(@NotNull @JsonProperty("reject_reason_type") com.blue333.ordermgmt.domain.enums.RejectReasonType reasonType, @NotBlank @JsonProperty("remark") String remark) {}
  public record AssignOrderRequest(@NotNull @Positive @JsonProperty("assignee_id") Long assigneeId, @NotNull @JsonProperty("task_deadline") LocalDateTime taskDeadline, @NotNull @JsonProperty("priority") com.blue333.ordermgmt.domain.enums.Priority priority, @JsonProperty("remark") String remark) {}
  public record CancelOrderRequest(@JsonProperty("reason") String reason) {}
}
