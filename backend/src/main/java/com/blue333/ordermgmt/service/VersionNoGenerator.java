package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.repository.DesignVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates per-task version numbers V1, V2, V3... (BR-05, P2-T04).
 *
 * <p>Concurrency safety comes from the caller: {@link #next(Long)} must run inside the transaction
 * that holds the {@code SELECT ... FOR UPDATE} lock on the material task row, so two uploads to the
 * same task are serialized. The unique index {@code uk_dver_task_id_version_no} is the backstop.</p>
 */
@Component
public class VersionNoGenerator {
  private static final String PREFIX = "V";

  private final DesignVersionRepository versionRepository;

  public VersionNoGenerator(DesignVersionRepository versionRepository) {
    this.versionRepository = versionRepository;
  }

  /** Allocates the next number. The caller must already hold the task row lock. */
  @Transactional(propagation = Propagation.MANDATORY)
  public String next(Long taskId) {
    return format(currentMax(taskId) + 1);
  }

  /** The number the next upload would get right now; informational only, it is not reserved. */
  @Transactional(readOnly = true)
  public String peekNext(Long taskId) {
    return format(currentMax(taskId) + 1);
  }

  public static String format(long number) {
    if (number <= 0) {
      throw new IllegalArgumentException("Version number must be positive");
    }
    return PREFIX + number;
  }

  public static long parse(String versionNo) {
    if (versionNo == null || !versionNo.matches("V[1-9]\\d{0,8}")) {
      throw new IllegalArgumentException("Invalid version number: " + versionNo);
    }
    return Long.parseLong(versionNo.substring(1));
  }

  private long currentMax(Long taskId) {
    if (taskId == null || taskId <= 0) {
      throw new IllegalArgumentException("taskId must be a positive value");
    }
    Number max = versionRepository.findMaxVersionNumber(taskId);
    return max == null ? 0 : max.longValue();
  }
}
