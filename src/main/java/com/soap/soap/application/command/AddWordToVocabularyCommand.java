package com.soap.soap.application.command;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.domain.model.VocabularyStatus;

public record AddWordToVocabularyCommand(
    String word, String language, VocabularyStatus initialStatus) {

  public AddWordToVocabularyCommand {
    if (initialStatus == null) {
      throw new InvalidApplicationArgumentException("Initial status must not be null");
    }
    if (word == null || word.isBlank()) {
      throw new InvalidApplicationArgumentException("Word must not be blank");
    }
    if (language == null || language.isBlank()) {
      throw new InvalidApplicationArgumentException("Language must not be blank");
    }
  }
}
