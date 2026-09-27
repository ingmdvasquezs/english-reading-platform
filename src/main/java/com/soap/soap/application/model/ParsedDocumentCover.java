package com.soap.soap.application.model;

import java.util.Arrays;
import java.util.Objects;

public record ParsedDocumentCover(String mediaType, byte[] bytes) {
  public ParsedDocumentCover {
    bytes = bytes.clone();
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ParsedDocumentCover(String otherMediaType, byte[] otherBytes)))
      return false;
    return Objects.equals(this.mediaType, otherMediaType) && Arrays.equals(this.bytes, otherBytes);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hashCode(mediaType) + Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "ParsedDocumentCover[mediaType="
        + mediaType
        + ", bytesLength="
        + (bytes != null ? bytes.length : 0)
        + "]";
  }
}
