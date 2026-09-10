package com.soap.soap.application.exception;

public class DictionaryInvalidResponseException extends ExternalProviderException {
  public DictionaryInvalidResponseException(Throwable cause) {
    super("DICTIONARY_INVALID_RESPONSE", cause);
  }
}
