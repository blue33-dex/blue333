package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CursorPaginationTest {
  @Test
  void extraFetchedRowSignalsNextPageAndIsNotReturned() {
    List<Long> fetched = List.of(50L, 40L, 30L, 20L);

    CursorPage<Long> page = CursorPage.fromFetched(fetched, 3, Function.identity());

    assertThat(page.items()).containsExactly(50L, 40L, 30L);
    assertThat(page.hasNext()).isTrue();
    assertThat(CursorPagination.decodeCursor(page.nextCursor())).isEqualTo(30L);
  }

  @Test
  void lastPageHasNoNextCursor() {
    CursorPage<Long> page = CursorPage.fromFetched(List.of(20L, 10L), 3, Function.identity());

    assertThat(page.items()).containsExactly(20L, 10L);
    assertThat(page.hasNext()).isFalse();
    assertThat(page.nextCursor()).isNull();
    assertThat(CursorPage.fromFetched(List.<Long>of(), 3, Function.identity()).items()).isEmpty();
  }

  @Test
  void pageableRequestsOneRowMoreThanTheLimit() {
    assertThat(CursorPagination.pageable(20).getPageSize()).isEqualTo(21);
    assertThat(CursorPagination.pageable(20).getPageNumber()).isZero();
  }

  @Test
  void cursorRoundTripsAndIsUrlSafe() {
    for (long id : new long[] {1L, 42L, 9_999_999_999L, Long.MAX_VALUE}) {
      String cursor = CursorPagination.encodeCursor(id);
      assertThat(cursor).doesNotContain("+", "/", "=");
      assertThat(CursorPagination.decodeCursor(cursor)).isEqualTo(id);
    }
  }

  @Test
  void missingCursorMeansFirstPage() {
    assertThat(CursorPagination.decodeCursor(null)).isNull();
    assertThat(CursorPagination.decodeCursor("  ")).isNull();
  }

  @Test
  void invalidCursorsAreRejectedAsBadRequests() {
    for (String cursor : List.of("!!!", encode("abc"), encode("-5"), encode("0"), encode("1.5"),
        encode("99999999999999999999"))) {
      assertThatIllegalArgumentException().isThrownBy(() -> CursorPagination.decodeCursor(cursor));
    }
    assertThatIllegalArgumentException().isThrownBy(() -> CursorPagination.encodeCursor(0L));
  }

  @Test
  void limitDefaultsToTwentyAndMustBeWithinOneToOneHundred() {
    assertThat(CursorPagination.normalizeLimit(null)).isEqualTo(20);
    assertThat(CursorPagination.normalizeLimit(1)).isEqualTo(1);
    assertThat(CursorPagination.normalizeLimit(100)).isEqualTo(100);
    assertThatIllegalArgumentException().isThrownBy(() -> CursorPagination.normalizeLimit(0));
    assertThatIllegalArgumentException().isThrownBy(() -> CursorPagination.normalizeLimit(101));
    assertThatIllegalArgumentException().isThrownBy(() -> CursorPagination.normalizeLimit(-1));
  }

  private static String encode(String raw) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
  }
}
