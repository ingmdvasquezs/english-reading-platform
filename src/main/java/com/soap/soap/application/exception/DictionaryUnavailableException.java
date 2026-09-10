package com.soap.soap.application.exception;

public class DictionaryUnavailableException extends ExternalProviderException {
  public DictionaryUnavailableException(Throwable cause) {
    super("DICTIONARY_UNAVAILABLE", cause);
  }
}
