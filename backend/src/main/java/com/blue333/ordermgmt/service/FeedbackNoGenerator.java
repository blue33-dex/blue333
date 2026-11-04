package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.repository.FeedbackItemRepository;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates feedback numbers FB-001, FB-002... (GLOSSARY E-15).
 *
 * <p>Concurrency safety mirrors {@link VersionNoGenerator}: the caller already holds the locks for
 * the version (and therefore the material task). The unique index
 * {@code uk_feedback_items_feedback_no} is the final backstop for parallel versions.</p>
 */
@Component
public class FeedbackNoGenerator {
  private static final String PREFIX = "FB-";

  private final FeedbackItemRepository feedbackItemRepository;

  public FeedbackNoGenerator(FeedbackItemRepository feedbackItemRepository) {
    this.feedbackItemRepository = feedbackItemRepository;
  }

  /** Allocates the next number. Must run inside the writing transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public String next() {
    return format(currentMax() + 1);
  }

  public static String format(long number) {
    if (number <= 0) {
      throw new IllegalArgumentException("Feedback number must be positive");
    }
    return PREFIX + String.format(Locale.ROOT, "%03d", number);
  }

  public static long parse(String feedbackNo) {
    if (feedbackNo == null || !feedbackNo.matches("FB-\\d{1,3}")) {
      throw new IllegalArgumentException("Invalid feedback number: " + feedbackNo);
    }
    return Long.parseLong(feedbackNo.substring(PREFIX.length()));
  }

  private long currentMax() {
    Number max = feedbackItemRepository.findMaxFeedbackNumber();
    return max == null ? 0 : max.longValue();
  }
}
