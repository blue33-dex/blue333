package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.DeliveryArchive;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.archive.ArchiveResponses.DeliveryArchiveResponse;
import com.blue333.ordermgmt.repository.AuditLogRepository;
import com.blue333.ordermgmt.repository.ClientReviewRepository;
import com.blue333.ordermgmt.repository.ConfirmationRecordRepository;
import com.blue333.ordermgmt.repository.DeliveryArchiveRepository;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.ExceptionRecordRepository;
import com.blue333.ordermgmt.repository.FeedbackItemRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** P3-T07: A-19 archive gate + PDF generation + A-28 report lookup. */
class ArchiveServiceTest {
  private static final Long ORDER_ID = 1L;
  private static final Long BO_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private OrderRepository orderRepository;
  private MaterialTaskRepository taskRepository;
  private DesignVersionRepository versionRepository;
  private ClientReviewRepository clientReviewRepository;
  private FeedbackItemRepository feedbackItemRepository;
  private ConfirmationRecordRepository confirmationRecordRepository;
  private DeliveryArchiveRepository deliveryArchiveRepository;
  private AuditLogRepository auditLogRepository;
  private UserRepository userRepository;
  private DeliveryRules deliveryRules;
  private ExceptionRecordService exceptionRecordService;
  private ExceptionRecordRepository exceptionRecordRepository;
  private FileStorageService fileStorageService;
  private ArchiveReportGenerator reportGenerator;
  private ArchiveService service;

  private com.blue333.ordermgmt.domain.Order order;
  private User bo;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    taskRepository = mock(MaterialTaskRepository.class);
    versionRepository = mock(DesignVersionRepository.class);
    clientReviewRepository = mock(ClientReviewRepository.class);
    feedbackItemRepository = mock(FeedbackItemRepository.class);
    confirmationRecordRepository = mock(ConfirmationRecordRepository.class);
    deliveryArchiveRepository = mock(DeliveryArchiveRepository.class);
    auditLogRepository = mock(AuditLogRepository.class);
    userRepository = mock(UserRepository.class);
    deliveryRules = mock(DeliveryRules.class);
    exceptionRecordService = mock(ExceptionRecordService.class);
    exceptionRecordRepository = mock(ExceptionRecordRepository.class);
    fileStorageService = mock(FileStorageService.class);
    reportGenerator = mock(ArchiveReportGenerator.class);
    service = new ArchiveService(orderRepository, taskRepository, versionRepository,
        clientReviewRepository, feedbackItemRepository, confirmationRecordRepository,
        deliveryArchiveRepository, auditLogRepository, userRepository, deliveryRules,
        exceptionRecordService, exceptionRecordRepository,
        fileStorageService, reportGenerator, Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Shanghai");

    bo = mock(User.class);
    when(bo.getId()).thenReturn(BO_ID);
    when(bo.getName()).thenReturn("Business Owner");
    when(userRepository.findById(BO_ID)).thenReturn(Optional.of(bo));

    order = mock(com.blue333.ordermgmt.domain.Order.class);
    when(order.getId()).thenReturn(ORDER_ID);
    when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
    when(order.getOrderNo()).thenReturn("ORD-20261104-001");
    when(order.getCustomerName()).thenReturn("Customer");
    when(order.getUseScene()).thenReturn("Launch event");
    when(order.getDeliveryDeadline()).thenReturn(LocalDate.of(2026, 12, 15));
    when(order.getRemark()).thenReturn(null);
    when(order.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 1, 9, 0));
    when(order.getAm()).thenReturn(mock(User.class));
    when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

    when(taskRepository.findAllByOrderIdOrderByIdAsc(any())).thenReturn(List.of());
    when(versionRepository.findAllForOrder(any())).thenReturn(List.of());
    when(clientReviewRepository.findAllByOrderIdOrderByIdAsc(any())).thenReturn(List.of());
    when(confirmationRecordRepository.findAllByOrderIdOrderByIdAsc(any())).thenReturn(List.of());
    when(auditLogRepository.findAllForTargets(any(), any())).thenReturn(List.of());
    when(reportGenerator.generate(any())).thenReturn(new byte[] {1, 2, 3});
    when(fileStorageService.generateObjectKey("archives/order-" + ORDER_ID, "archive-report.pdf"))
        .thenReturn("archives/order-1/archive-report.pdf");
    when(deliveryArchiveRepository.save(any(DeliveryArchive.class)))
        .thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void archivesACompletedDeliverableOrderAndStoresThePdf() {
    DeliveryArchiveResponse response = service.archiveOrder(ORDER_ID, businessOwner());

    assertThat(response.orderId()).isEqualTo(ORDER_ID);
    assertThat(response.archivedAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 16, 0));
    assertThat(response.archiveReportAvailable()).isTrue();
    verify(deliveryRules).requireDeliverable(ORDER_ID);
    verify(reportGenerator).generate(any());
    verify(fileStorageService).putObject(
        "archives/order-1/archive-report.pdf", new byte[] {1, 2, 3}, "application/pdf");
    verify(deliveryArchiveRepository).save(any(DeliveryArchive.class));
  }

  @Test
  void archiveRejectsOrdersThatAreNotCompletedOrAlreadyArchived() {
    when(order.getStatus()).thenReturn(OrderStatus.IN_PRODUCTION);
    assertThatThrownBy(() -> service.archiveOrder(ORDER_ID, businessOwner()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("COMPLETED");
    verify(reportGenerator, never()).generate(any());

    when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
    when(deliveryArchiveRepository.existsByOrderId(ORDER_ID)).thenReturn(true);
    assertThatThrownBy(() -> service.archiveOrder(ORDER_ID, businessOwner()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("already archived");
    verify(reportGenerator, never()).generate(any());
  }

  @Test
  void archiveIsBlockedWhileExceptionsRemainOpen() {
    org.mockito.Mockito.doThrow(new IllegalStateException("2 exception(s) are still open"))
        .when(exceptionRecordService).requireAllClosed(ORDER_ID);

    assertThatThrownBy(() -> service.archiveOrder(ORDER_ID, businessOwner()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("still open");
    verify(reportGenerator, never()).generate(any());
    verify(deliveryArchiveRepository, never()).save(any());
  }

  @Test
  void archiveRequiresBusinessOwnerRole() {
    assertThatThrownBy(() -> service.archiveOrder(ORDER_ID, designer()))
        .isInstanceOf(AccessDeniedException.class);
    verify(orderRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void archiveReportPathIsOnlyAvailableForArchivedOrders() {
    DeliveryArchive archive = mock(DeliveryArchive.class);
    when(archive.getArchiveReportPath()).thenReturn("archives/order-1/archive-report.pdf");
    when(deliveryArchiveRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(archive));

    assertThat(service.archiveReportPath(ORDER_ID, businessOwner()))
        .isEqualTo("archives/order-1/archive-report.pdf");

    when(deliveryArchiveRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.archiveReportPath(ORDER_ID, businessOwner()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.archiveReportPath(ORDER_ID, designer()))
        .isInstanceOf(AccessDeniedException.class);
  }

  private CurrentUser businessOwner() {
    return new CurrentUser(bo, Set.of(RoleType.BO));
  }

  private CurrentUser designer() {
    User designer = mock(User.class);
    when(designer.getId()).thenReturn(3L);
    return new CurrentUser(designer, Set.of(RoleType.DS));
  }
}
