package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** OpenPDF smoke test: the A-19 report generator must emit a valid, non-empty PDF document. */
class ArchiveReportGeneratorTest {
  private final ArchiveReportGenerator generator = new ArchiveReportGenerator("");

  @Test
  void generatesAValidPdfWithAllSections() {
    ArchiveReportData data = new ArchiveReportData("ORD-20261104-001", "Customer", "Launch event",
        LocalDate.of(2026, 12, 15), "Handle with care", "COMPLETED", "Account Manager",
        LocalDateTime.of(2026, 11, 1, 9, 0),
        List.of(new ArchiveReportData.TaskRow("POSTER", "A3", "Designer", "HIGH", "APPROVED",
            LocalDateTime.of(2026, 12, 1, 18, 0))),
        List.of(new ArchiveReportData.VersionRow("V1", "POSTER", "A3", "FINAL", "Designer",
            LocalDateTime.of(2026, 11, 2, 10, 0), false)),
        List.of(new ArchiveReportData.ClientReviewRow("V1", "COMPLETED", "Account Manager",
            LocalDateTime.of(2026, 11, 2, 12, 0), LocalDateTime.of(2026, 11, 3, 12, 0))),
        List.of(new ArchiveReportData.FeedbackRow("FB-001", "V1", "MODIFICATION_SUGGESTION", "CLIENT",
            "RESOLVED", "Account Manager", "Make the logo larger")),
        List.of(new ArchiveReportData.ExceptionRow("DELAY", "CLOSED", "Business Owner",
            "Task missed its deadline", "Rescheduled with the supplier",
            LocalDateTime.of(2026, 11, 3, 10, 0))),
        new ArchiveReportData.ConfirmationRow("V1", "Client Jane", LocalDateTime.of(2026, 11, 3, 12, 0)),
        List.of(new ArchiveReportData.TimelineRow(LocalDateTime.of(2026, 11, 3, 12, 0), "CONFIRM_VERSION",
            "Account Manager", "CONFIRMATION_RECORD#7", "IN_CLIENT_REVIEW -> CLIENT_CONFIRMED", "SUCCESS")));

    byte[] pdf = generator.generate(data);

    assertThat(pdf).isNotEmpty();
    assertThat(pdf[0]).isEqualTo((byte) '%');
    assertThat(pdf[1]).isEqualTo((byte) 'P');
    assertThat(pdf[2]).isEqualTo((byte) 'D');
    assertThat(pdf[3]).isEqualTo((byte) 'F');
  }

  @Test
  void generatesAPdfForAnOrderWithoutAnyRelatedRows() {
    ArchiveReportData data = new ArchiveReportData("ORD-20261104-002", "Customer", "Launch event",
        LocalDate.of(2026, 12, 15), null, "COMPLETED", "Account Manager",
        LocalDateTime.of(2026, 11, 1, 9, 0), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, List.of());

    assertThat(generator.generate(data)).isNotEmpty();
  }
}
