package com.soap.soap.application.command;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.domain.model.VocabularyStatus;

public record SetVocabularyStatusCommand(String word, String language, VocabularyStatus status) {

  public SetVocabularyStatusCommand {
    if (word == null || word.isBlank()) {
      throw new InvalidApplicationArgumentException("Word must not be blank");
    }
    if (language == null || language.isBlank()) {
      throw new InvalidApplicationArgumentException("Language must not be blank");
    }
    if (status == null) {
      throw new InvalidApplicationArgumentException("Vocabulary status must not be null");
    }
  }
}
