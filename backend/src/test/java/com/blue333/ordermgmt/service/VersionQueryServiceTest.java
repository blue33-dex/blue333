package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.VersionFile;
import com.blue333.ordermgmt.domain.enums.MaterialType;
import com.blue333.ordermgmt.domain.enums.Priority;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.version.VersionResponses.VersionComparisonResponse;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.VersionFileRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A-10 (BO-05): side-by-side comparison of two versions of one order. */
class VersionQueryServiceTest {
  private static final long ORDER_ID = 1L;
  private static final long V1_ID = 50L;
  private static final long V2_ID = 51L;

  private AccessControlService accessControl;
  private DesignVersionRepository versionRepository;
  private VersionFileRepository fileRepository;
  private VersionQueryService service;

  private DesignVersion v1;
  private DesignVersion v2;

  @BeforeEach
  void setUp() {
    accessControl = mock(AccessControlService.class);
    versionRepository = mock(DesignVersionRepository.class);
    fileRepository = mock(VersionFileRepository.class);
    service = new VersionQueryService(accessControl, versionRepository, fileRepository);

    Order order = mock(Order.class);
    when(order.getId()).thenReturn(ORDER_ID);
    User uploader = mock(User.class);
    when(uploader.getId()).thenReturn(3L);
    when(uploader.getName()).thenReturn("Designer");
    MaterialTask task = MaterialTask.create(order,
        MaterialSpec.create(order, MaterialType.POSTER, "A3"), uploader,
        LocalDateTime.of(2026, 12, 1, 18, 0), Priority.HIGH);
    v1 = version(task, V1_ID, "V1", "first draft", "initial");
    v2 = version(task, V2_ID, "V2", "second draft", "initial");
  }

  @Test
  void returnsBothVersionsSideBySideWithFieldChangeFlags() {
    when(versionRepository.findAllForOrder(ORDER_ID)).thenReturn(List.of(v2, v1));
    List<VersionFile> files = List.of(file(v1, "v1.pdf"), file(v2, "v2.pdf"));
    when(fileRepository.findAllByVersionIdIn(List.of(V1_ID, V2_ID))).thenReturn(files);

    VersionComparisonResponse comparison = service.compare(ORDER_ID, V1_ID, V2_ID, reviewer());

    assertThat(comparison.orderId()).isEqualTo(ORDER_ID);
    assertThat(comparison.left().versionNo()).isEqualTo("V1");
    assertThat(comparison.right().versionNo()).isEqualTo("V2");
    assertThat(comparison.left().file().fileName()).isEqualTo("v1.pdf");
    assertThat(comparison.right().file().fileName()).isEqualTo("v2.pdf");
    assertThat(comparison.designNoteChanged()).isTrue();
    assertThat(comparison.changeSummaryChanged()).isFalse();
  }

  @Test
  void rejectsSameVersionMissingVersionsAndVersionsFromAnotherOrder() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.compare(ORDER_ID, V1_ID, V1_ID, reviewer()));

    when(versionRepository.findAllForOrder(ORDER_ID)).thenReturn(List.of(v1));
    assertThatThrownBy(() -> service.compare(ORDER_ID, V1_ID, V2_ID, reviewer()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("At least two versions");

    when(versionRepository.findAllForOrder(ORDER_ID)).thenReturn(List.of(v1, v2));
    assertThatThrownBy(() -> service.compare(ORDER_ID, V1_ID, 999L, reviewer()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.compare(ORDER_ID, 0L, V2_ID, reviewer()));
  }

  private DesignVersion version(MaterialTask task, long id, String versionNo, String note, String summary) {
    DesignVersion version = spy(DesignVersion.draft(task, versionNo, note, summary, task.getAssignee(), false));
    when(version.getId()).thenReturn(id);
    return version;
  }

  private VersionFile file(DesignVersion version, String fileName) {
    VersionFile file = mock(VersionFile.class);
    when(file.getVersion()).thenReturn(version);
    when(file.getFileName()).thenReturn(fileName);
    when(file.getFileSize()).thenReturn(2048L);
    return file;
  }

  private CurrentUser reviewer() {
    User bo = mock(User.class);
    when(bo.getId()).thenReturn(2L);
    return new CurrentUser(bo, Set.of(RoleType.BO));
  }
}
