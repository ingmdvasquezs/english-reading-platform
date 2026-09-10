package com.soap.soap.application.exception;

public class DictionaryTimeoutException extends ExternalProviderException {
  public DictionaryTimeoutException(Throwable cause) {
    super("DICTIONARY_TIMEOUT", cause);
  }
}
