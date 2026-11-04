package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.Priority;
import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.order.OrderResponses.OrderDetailResponse;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.service.AccessControlService;
import com.blue333.ordermgmt.service.CurrentUser;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.blue333.ordermgmt.service.CursorPage;
import com.blue333.ordermgmt.service.CursorPagination;
import com.blue333.ordermgmt.service.MaterialTaskService;
import com.blue333.ordermgmt.service.OrderQueryService;
import com.blue333.ordermgmt.service.ResourceNotFoundException;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/material-tasks")
public class MaterialTaskController {
  private final CurrentUserService currentUserService;
  private final AccessControlService accessControl;
  private final MaterialTaskRepository taskRepository;
  private final OrderQueryService orderQueryService;
  private final MaterialTaskService materialTaskService;
  private final UserRepository userRepository;

  public MaterialTaskController(CurrentUserService currentUserService, AccessControlService accessControl,
      MaterialTaskRepository taskRepository, OrderQueryService orderQueryService,
      MaterialTaskService materialTaskService, UserRepository userRepository) {
    this.currentUserService = currentUserService;
    this.accessControl = accessControl;
    this.taskRepository = taskRepository;
    this.orderQueryService = orderQueryService;
    this.materialTaskService = materialTaskService;
    this.userRepository = userRepository;
  }

  @GetMapping("/mine")
  @PreAuthorize("hasRole('DS')")
  @Transactional(readOnly = true)
  public ApiResponse<List<TaskSummary>> mine(@RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    CurrentUser current = currentUserService.requireCurrentUser();
    int pageLimit = CursorPagination.normalizeLimit(limit);
    List<MaterialTask> rows = taskRepository.findPageByAssigneeId(current.id(),
        CursorPagination.decodeCursor(cursor), CursorPagination.pageable(pageLimit));
    return ApiResponse.page(CursorPage.fromFetched(rows, pageLimit, MaterialTask::getId).map(this::summary));
  }

  @GetMapping("/{taskId:\\d+}")
  @PreAuthorize("hasAnyRole('BO','DS')")
  @Transactional(readOnly = true)
  public ApiResponse<TaskDetail> detail(@PathVariable Long taskId) {
    CurrentUser current = currentUserService.requireCurrentUser();
    MaterialTask task = accessControl.findVisibleTask(taskId, current);
    // OrderDetailResponse includes the frozen requirement baseline and the authorized attachment list.
    return ApiResponse.of(new TaskDetail(summary(task), orderQueryService.detail(task.getOrder().getId(), current)));
  }

  /** A-07: BO hands a task over to another designer; the reason is mandatory (EC-D). */
  @PostMapping("/{taskId:\\d+}/reassign")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<OrderDetailResponse> reassign(@PathVariable Long taskId,
      @Valid @RequestBody ReassignTaskRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    MaterialTask existing = accessControl.findVisibleTask(taskId, current);
    User assignee = userRepository.findById(request.assigneeId())
        .orElseThrow(() -> new ResourceNotFoundException("User does not exist: " + request.assigneeId()));
    materialTaskService.reassignTask(taskId, current.user(), assignee, request.taskDeadline(),
        request.priority(), request.reason());
    return ApiResponse.of(orderQueryService.detail(existing.getOrder().getId(), current));
  }

  /** A-23: BO cancels a single non-terminal task; the reason is mandatory. */
  @PostMapping("/{taskId:\\d+}/cancel")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<OrderDetailResponse> cancel(@PathVariable Long taskId,
      @Valid @RequestBody CancelTaskRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    MaterialTask existing = accessControl.findVisibleTask(taskId, current);
    materialTaskService.cancelTask(taskId, request.reason());
    return ApiResponse.of(orderQueryService.detail(existing.getOrder().getId(), current));
  }

  private TaskSummary summary(MaterialTask task) {
    return new TaskSummary(task.getId(), task.getOrder().getId(), task.getOrder().getOrderNo(),
        task.getMaterialSpec().getMaterialType().name(), task.getMaterialSpec().getSpecSize(),
        task.getTaskDeadline(), task.getPriority().name(), task.getStatus().name(), task.getCreatedAt());
  }

  public record TaskSummary(@JsonProperty("id") Long id, @JsonProperty("order_id") Long orderId,
      @JsonProperty("order_no") String orderNo, @JsonProperty("material_type") String materialType,
      @JsonProperty("spec_size") String specSize, @JsonProperty("task_deadline") LocalDateTime taskDeadline,
      @JsonProperty("priority") String priority, @JsonProperty("status") String status,
      @JsonProperty("created_at") LocalDateTime createdAt) {
  }

  public record TaskDetail(@JsonProperty("task") TaskSummary task,
      @JsonProperty("order") OrderDetailResponse order) {
  }

  /**
   * A-07 payload. {@code task_deadline} and {@code priority} are optional; when omitted the current
   * values are kept, because a handover usually only changes the designer.
   */
  public record ReassignTaskRequest(
      @NotNull @Positive @JsonProperty("assignee_id") Long assigneeId,
      @NotBlank @JsonProperty("reason") String reason,
      @JsonProperty("task_deadline") LocalDateTime taskDeadline,
      @JsonProperty("priority") Priority priority) {
  }

  public record CancelTaskRequest(@NotBlank @JsonProperty("reason") String reason) {
  }
}
