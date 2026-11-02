package com.blue333.ordermgmt.service;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** One page of a descending-id cursor listing. {@code nextCursor} is null when there is no next page. */
public record CursorPage<T>(List<T> items, boolean hasNext, String nextCursor) {
  public CursorPage {
    items = items == null ? List.of() : List.copyOf(items);
  }

  /**
   * Builds a page from rows fetched with {@link CursorPagination#pageable(int)}, i.e. up to
   * {@code limit + 1} rows. The extra row only signals that a next page exists and is not returned.
   */
  public static <T> CursorPage<T> fromFetched(List<T> fetched, int limit, Function<T, Long> idExtractor) {
    Objects.requireNonNull(idExtractor, "idExtractor must not be null");
    int validLimit = CursorPagination.normalizeLimit(limit);
    List<T> rows = fetched == null ? List.of() : fetched;
    boolean hasNext = rows.size() > validLimit;
    List<T> items = hasNext ? rows.subList(0, validLimit) : rows;
    String nextCursor = hasNext
        ? CursorPagination.encodeCursor(idExtractor.apply(items.get(items.size() - 1)))
        : null;
    return new CursorPage<>(items, hasNext, nextCursor);
  }

  public <R> CursorPage<R> map(Function<T, R> mapper) {
    return new CursorPage<>(items.stream().map(mapper).toList(), hasNext, nextCursor);
  }
}
