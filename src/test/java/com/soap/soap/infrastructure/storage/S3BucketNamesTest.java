package com.soap.soap.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class S3BucketNamesTest {

  @Test
  void rejectsMissingBlankPlaceholderAndMalformedBucketNamesWithoutEchoingThem() {
    for (var invalid :
        new String[] {
          null,
          "",
          "   ",
          "${DOCUMENT_STORAGE_S3_BUCKET}",
          "${DOCUMENT_STORAGE_S3_BUCKET:}",
          "Upper_Case",
          "ab",
          "-starts-with-hyphen"
        }) {
      assertThat(S3BucketNames.isValid(invalid)).as("%s", invalid).isFalse();
      assertThatThrownBy(() -> S3BucketNames.requireValid(invalid))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(S3BucketNames.INVALID_BUCKET_MESSAGE);
    }
  }

  @Test
  void acceptsValidBucketNames() {
    for (var valid : new String[] {"example-documents-bucket", "abc", "docs.example.123"}) {
      assertThat(S3BucketNames.requireValid(valid)).isEqualTo(valid);
    }
  }
}
