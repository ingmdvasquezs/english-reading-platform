package com.soap.soap.application.exception;

import java.util.UUID;

public class VocabularyEntryNotFoundException extends RuntimeException {
  public VocabularyEntryNotFoundException(UUID userId, UUID wordId) {
    super("Vocabulary entry not found for user %s and word %s".formatted(userId, wordId));
  }
}
