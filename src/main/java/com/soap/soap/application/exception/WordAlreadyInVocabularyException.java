package com.soap.soap.application.exception;

public class WordAlreadyInVocabularyException extends RuntimeException {
  public WordAlreadyInVocabularyException(String word) {
    super("Word is already in the user's vocabulary: " + word);
  }
}
