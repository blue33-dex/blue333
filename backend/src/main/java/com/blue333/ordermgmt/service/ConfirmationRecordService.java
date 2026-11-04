package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.ConfirmationRecord;
import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.repository.ConfirmationRecordRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * P3-T06: creates the immutable confirmation trail (GLOSSARY E-16, BR-10).
 *
 * <p>Protection is deliberately layered: this service only inserts, the repository exposes no
 * update/delete methods, the entity is {@code @Immutable}, and MySQL triggers reject UPDATE/DELETE
 * on {@code confirmation_records}.</p>
 */
@Service
public class ConfirmationRecordService {
  private final ConfirmationRecordRepository confirmationRecordRepository;

  public ConfirmationRecordService(ConfirmationRecordRepository confirmationRecordRepository) {
    this.confirmationRecordRepository = confirmationRecordRepository;
  }

  /** Must run inside the A-16 transaction so the record is committed atomically with the version. */
  @Transactional(propagation = Propagation.MANDATORY)
  public ConfirmationRecord create(
      DesignVersion version, Order order, String confirmerName, LocalDateTime confirmedAt) {
    if (confirmationRecordRepository.existsByVersionId(version.getId())) {
      throw new InvalidStateTransitionException("This version has already been confirmed");
    }
    return confirmationRecordRepository.save(
        ConfirmationRecord.create(confirmerName, version, order, confirmedAt));
  }
}
