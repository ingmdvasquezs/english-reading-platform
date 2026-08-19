package com.soap.soap.application.model;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

  public PageResult {
    content = List.copyOf(content);
  }

  public int totalPages() {
    return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
  }
}
