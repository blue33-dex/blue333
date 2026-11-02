package com.blue333.ordermgmt.util;

import cn.hutool.http.HTMLFilter;
import org.springframework.stereotype.Component;

/**
 * Sanitizes user-entered text before persistence.
 *
 * <p>The application defaults to plain text. A new Hutool HTMLFilter is
 * created per call because the filter maintains parsing state and is not
 * thread-safe. Any remaining HTML tags are then removed so callers never
 * persist executable markup.</p>
 */
@Component
public final class XSSCleaner {
  private static final String HTML_TAG_PATTERN = "(?s)<[^>]*>";

  public static String clean(String input) {
    if (input == null) {
      return null;
    }

    String filtered = new HTMLFilter().filter(input);
    return filtered.replaceAll(HTML_TAG_PATTERN, "");
  }
}
