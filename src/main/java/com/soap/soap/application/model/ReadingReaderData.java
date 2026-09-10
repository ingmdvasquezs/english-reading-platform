package com.soap.soap.application.model;

import com.soap.soap.domain.model.ReadingProgressStatus;
import java.util.List;
import java.util.UUID;

public record ReadingReaderData(
    UUID readingId,
    String title,
    String language,
    List<ReaderToken> tokens,
    ReadingProgressStatus progressStatus,
    Integer currentPartOrdinal,
    Integer paginationVersion) {
  public ReadingReaderData(
      UUID readingId,
      String title,
      String language,
      List<ReaderToken> tokens,
      ReadingProgressStatus progressStatus) {
    this(readingId, title, language, tokens, progressStatus, null, null);
  }

  public ReadingReaderData(
      UUID readingId, String title, String language, List<ReaderToken> tokens) {
    this(readingId, title, language, tokens, null, null, null);
  }
}
