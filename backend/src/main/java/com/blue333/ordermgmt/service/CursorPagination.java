package com.blue333.ordermgmt.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Cursor helpers for descending-id listings. A cursor is the Base64Url (unpadded) encoding of the
 * decimal id of the last item on the previous page. Invalid input raises IllegalArgumentException,
 * which the API maps to 400.
 */
public final class CursorPagination {
  public static final int DEFAULT_LIMIT = 20;
  public static final int MAX_LIMIT = 100;

  private CursorPagination() {
  }

  /** {@code null} means the default limit; values outside 1..100 are rejected. */
  public static int normalizeLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
    }
    return limit;
  }

  /** Requests one row more than {@code limit} so the page can tell whether a next page exists. */
  public static Pageable pageable(int limit) {
    return PageRequest.of(0, normalizeLimit(limit) + 1);
  }

  public static String encodeCursor(Long id) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("cursor id must be a positive value");
    }
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(Long.toString(id).getBytes(StandardCharsets.US_ASCII));
  }

  /** Returns {@code null} for a missing or blank cursor (first page). */
  public static Long decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.US_ASCII);
      if (decoded.isEmpty() || !decoded.chars().allMatch(Character::isDigit)) {
        throw invalidCursor();
      }
      long id = Long.parseLong(decoded);
      if (id <= 0) {
        throw invalidCursor();
      }
      return id;
    } catch (IllegalArgumentException exception) {
      // Covers malformed Base64 and NumberFormatException (overflow).
      throw invalidCursor();
    }
  }

  private static IllegalArgumentException invalidCursor() {
    return new IllegalArgumentException("cursor is invalid");
  }
}
