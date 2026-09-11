package com.soap.soap.application.model;

public record VocabularySummary(
    long totalCount, long newCount, long learningCount, long knownCount, long ignoredCount) {

  public static VocabularySummary empty() {
    return new VocabularySummary(0L, 0L, 0L, 0L, 0L);
  }
}
