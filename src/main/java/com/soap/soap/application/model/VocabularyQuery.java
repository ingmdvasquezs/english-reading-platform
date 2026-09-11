package com.soap.soap.application.model;

import com.soap.soap.domain.model.VocabularyStatus;

public record VocabularyQuery(PageRequest pageRequest, VocabularyStatus status, String search) {

  public VocabularyQuery {
    if (pageRequest == null) {
      throw new IllegalArgumentException("Page request must not be null");
    }
  }
}
