package com.soap.soap.infrastructure.storage;

import java.util.regex.Pattern;

/**
 * Validation of the configured S3 document bucket. There is deliberately no fallback bucket: a
 * missing, blank or unresolved value (for example a literal {@code ${DOCUMENT_STORAGE_S3_BUCKET}})
 * must never be used to sign requests.
 */
public final class S3BucketNames {
  /** S3 general purpose bucket naming rules: 3-63 chars, lowercase, digits, dots and hyphens. */
  private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");

  static final String INVALID_BUCKET_MESSAGE =
      "app.document-storage.s3.bucket (DOCUMENT_STORAGE_S3_BUCKET) must be set to a valid S3"
          + " bucket name when the S3 document storage provider is used";

  private S3BucketNames() {}

  public static boolean isValid(String bucket) {
    return bucket != null && VALID.matcher(bucket).matches();
  }

  /** Returns the bucket name or fails without echoing the invalid configured value. */
  public static String requireValid(String bucket) {
    if (!isValid(bucket)) {
      throw new IllegalStateException(INVALID_BUCKET_MESSAGE);
    }
    return bucket;
  }
}
