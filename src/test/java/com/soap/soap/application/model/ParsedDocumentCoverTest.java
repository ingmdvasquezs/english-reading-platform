package com.soap.soap.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParsedDocumentCoverTest {

  @Test
  void instancesWithDifferentArrayReferencesButSameBytesAreEqual() {
    byte[] bytes1 = new byte[] {1, 2, 3, 4};
    byte[] bytes2 = new byte[] {1, 2, 3, 4};

    ParsedDocumentCover cover1 = new ParsedDocumentCover("image/jpeg", bytes1);
    ParsedDocumentCover cover2 = new ParsedDocumentCover("image/jpeg", bytes2);

    assertThat(cover1).isEqualTo(cover2).isNotSameAs(cover2);
    assertThat(cover1.bytes()).isNotSameAs(cover2.bytes());
  }

  @Test
  void equalInstancesHaveSameHashCode() {
    byte[] bytes1 = new byte[] {10, 20, 30};
    byte[] bytes2 = new byte[] {10, 20, 30};

    ParsedDocumentCover cover1 = new ParsedDocumentCover("image/png", bytes1);
    ParsedDocumentCover cover2 = new ParsedDocumentCover("image/png", bytes2);

    assertThat(cover1).hasSameHashCodeAs(cover2);
  }

  @Test
  void instancesWithDifferentBytesOrMediaTypeAreNotEqual() {
    byte[] bytes1 = new byte[] {1, 2, 3};
    byte[] bytes2 = new byte[] {1, 2, 4};

    ParsedDocumentCover cover1 = new ParsedDocumentCover("image/png", bytes1);
    ParsedDocumentCover cover2 = new ParsedDocumentCover("image/png", bytes2);
    ParsedDocumentCover cover3 = new ParsedDocumentCover("image/jpeg", bytes1);

    assertThat(cover1)
        .isNotEqualTo(cover2)
        .isNotEqualTo(cover3)
        .isNotEqualTo(null)
        .isNotEqualTo("someString");
  }

  @Test
  void toStringDoesNotDumpFullArrayContents() {
    byte[] largeBytes = new byte[5000];
    ParsedDocumentCover cover = new ParsedDocumentCover("image/webp", largeBytes);

    String stringRepresentation = cover.toString();
    assertThat(stringRepresentation)
        .contains("image/webp")
        .contains("bytesLength=5000")
        .doesNotContain("[B@");
  }

  @Test
  void accessorAndConstructorReturnDefensiveCopies() {
    byte[] original = new byte[] {1, 2, 3};
    ParsedDocumentCover cover = new ParsedDocumentCover("image/png", original);

    // Modify original array
    original[0] = 99;
    assertThat(cover.bytes()[0]).isEqualTo((byte) 1);

    // Modify accessor array
    byte[] copy = cover.bytes();
    copy[0] = 77;
    assertThat(cover.bytes()[0]).isEqualTo((byte) 1);
  }
}
