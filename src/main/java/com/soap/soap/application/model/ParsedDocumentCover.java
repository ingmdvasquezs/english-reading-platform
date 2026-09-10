package com.soap.soap.application.model;

public record ParsedDocumentCover(String mediaType, byte[] bytes) {
  public ParsedDocumentCover {
    bytes = bytes.clone();
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }
}
