package com.soap.soap.application.exception;

public class ConcurrentVocabularyModificationException extends RuntimeException {
  public ConcurrentVocabularyModificationException() {
    super("Vocabulary entry was modified concurrently");
  }
}
