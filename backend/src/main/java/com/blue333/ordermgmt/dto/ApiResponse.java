package com.blue333.ordermgmt.dto;

import com.blue333.ordermgmt.service.CursorPage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Standard response envelope. {@code pagination} is present only for cursor listings. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Pagination pagination) {
  public static <T> ApiResponse<T> of(T data) {
    return new ApiResponse<>(data, null);
  }

  public static <T> ApiResponse<List<T>> page(CursorPage<T> page) {
    return new ApiResponse<>(page.items(), new Pagination(page.hasNext(), page.nextCursor()));
  }

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Pagination(
      @JsonProperty("has_next") boolean hasNext,
      @JsonProperty("next_cursor") String nextCursor) {
  }
}
