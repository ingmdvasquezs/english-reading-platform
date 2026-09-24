package com.soap.soap.infrastructure.storage;

import com.soap.soap.application.exception.StorageObjectNotFoundException;
import com.soap.soap.application.exception.TransientStorageException;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.domain.model.StoredObjectAttributes;
import com.soap.soap.domain.model.UploadAuthorization;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectAttributesRequest;
import software.amazon.awssdk.services.s3.model.GetObjectAttributesResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectAttributes;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3DocumentObjectStorageAdapter implements DocumentObjectStoragePort {

  private static final Logger LOG = LoggerFactory.getLogger(S3DocumentObjectStorageAdapter.class);
  private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final S3DocumentStorageProperties properties;

  public S3DocumentObjectStorageAdapter(
      S3Client s3Client, S3Presigner s3Presigner, S3DocumentStorageProperties properties) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.properties = properties;
  }

  @Override
  public UploadAuthorization createUploadAuthorization(
      String storageKey,
      String canonicalContentType,
      String checksumSha256Hex,
      Duration presignDuration) {
    Objects.requireNonNull(storageKey, "storageKey must not be null");
    Objects.requireNonNull(canonicalContentType, "canonicalContentType must not be null");
    Objects.requireNonNull(checksumSha256Hex, "checksumSha256Hex must not be null");

    String normalizedHex = checksumSha256Hex.strip().toLowerCase(Locale.ROOT);
    if (!SHA256_PATTERN.matcher(normalizedHex).matches()) {
      throw new IllegalArgumentException(
          "checksumSha256Hex must contain exactly 64 lowercase hexadecimal characters");
    }

    byte[] checksumBytes = HexFormat.of().parseHex(normalizedHex);
    String checksumBase64 = Base64.getEncoder().encodeToString(checksumBytes);

    String bucket = properties.s3().bucket();
    if (bucket == null || bucket.isBlank()) {
      bucket = "english-reading-documents";
    }

    PutObjectRequest putObjectRequest =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(storageKey)
            .contentType(canonicalContentType)
            .checksumSHA256(checksumBase64)
            .ifNoneMatch("*")
            .build();

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(presignDuration)
            .putObjectRequest(putObjectRequest)
            .build();

    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
    URI uploadUrl = URI.create(presigned.url().toString());
    Instant expiresAt = presigned.expiration();

    // Required headers to be sent by browser (excluding host, authorization, content-length)
    Map<String, String> requiredHeaders = new LinkedHashMap<>();
    requiredHeaders.put("Content-Type", canonicalContentType);
    requiredHeaders.put("x-amz-checksum-sha256", checksumBase64);
    requiredHeaders.put("If-None-Match", "*");

    return new UploadAuthorization(uploadUrl, "PUT", requiredHeaders, expiresAt);
  }

  @Override
  public StoredObjectAttributes inspectObject(String storageKey) {
    Objects.requireNonNull(storageKey, "storageKey must not be null");
    String bucket = properties.s3().bucket();
    if (bucket == null || bucket.isBlank()) {
      bucket = "english-reading-documents";
    }

    try {
      GetObjectAttributesRequest request =
          GetObjectAttributesRequest.builder()
              .bucket(bucket)
              .key(storageKey)
              .objectAttributes(ObjectAttributes.OBJECT_SIZE, ObjectAttributes.CHECKSUM)
              .build();

      GetObjectAttributesResponse response = s3Client.getObjectAttributes(request);
      long size = response.objectSize() != null ? response.objectSize() : 0L;
      String checksumHex = null;

      if (response.checksum() != null && response.checksum().checksumSHA256() != null) {
        byte[] decoded = Base64.getDecoder().decode(response.checksum().checksumSHA256());
        checksumHex = HexFormat.of().formatHex(decoded).toLowerCase(Locale.ROOT);
      } else {
        // Fallback: check if HeadObject with ChecksumMode.ENABLED can provide the checksum
        try {
          HeadObjectResponse head =
              s3Client.headObject(
                  HeadObjectRequest.builder()
                      .bucket(bucket)
                      .key(storageKey)
                      .checksumMode(ChecksumMode.ENABLED)
                      .build());
          if (head.checksumSHA256() != null) {
            byte[] decoded = Base64.getDecoder().decode(head.checksumSHA256());
            checksumHex = HexFormat.of().formatHex(decoded).toLowerCase(Locale.ROOT);
          }
        } catch (Exception ignored) {
          // Keep checksumHex as null if HeadObject fails
        }
      }

      String etag = response.eTag();
      return new StoredObjectAttributes(storageKey, size, checksumHex, etag);
    } catch (NoSuchKeyException e) {
      throw new StorageObjectNotFoundException(storageKey);
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw new StorageObjectNotFoundException(storageKey);
      }
      // If GetObjectAttributes is not implemented in mock/localstack, fallback to HeadObject
      if (e.statusCode() == 501
          || (e.awsErrorDetails() != null
              && "NotImplemented".equalsIgnoreCase(e.awsErrorDetails().errorCode()))) {
        return inspectViaHeadObject(bucket, storageKey);
      }
      throw new TransientStorageException(
          "Transient S3 error inspecting object " + storageKey + ": " + e.getMessage(), e);
    } catch (SdkClientException e) {
      throw new TransientStorageException(
          "Transient SDK client error inspecting object " + storageKey + ": " + e.getMessage(), e);
    }
  }

  private StoredObjectAttributes inspectViaHeadObject(String bucket, String storageKey) {
    try {
      HeadObjectResponse head =
          s3Client.headObject(
              HeadObjectRequest.builder()
                  .bucket(bucket)
                  .key(storageKey)
                  .checksumMode(ChecksumMode.ENABLED)
                  .build());
      long size = head.contentLength() != null ? head.contentLength() : 0L;
      String checksumHex = null;
      if (head.checksumSHA256() != null) {
        byte[] decoded = Base64.getDecoder().decode(head.checksumSHA256());
        checksumHex = HexFormat.of().formatHex(decoded).toLowerCase(Locale.ROOT);
      }
      return new StoredObjectAttributes(storageKey, size, checksumHex, head.eTag());
    } catch (NoSuchKeyException nsk) {
      throw new StorageObjectNotFoundException(storageKey);
    } catch (S3Exception s3Ex) {
      if (s3Ex.statusCode() == 404) {
        throw new StorageObjectNotFoundException(storageKey);
      }
      throw new TransientStorageException(
          "Transient S3 error inspecting object via HeadObject " + storageKey, s3Ex);
    } catch (SdkClientException clientEx) {
      throw new TransientStorageException(
          "Transient SDK client error inspecting object via HeadObject " + storageKey, clientEx);
    }
  }

  @Override
  public void deleteObject(String storageKey) {
    Objects.requireNonNull(storageKey, "storageKey must not be null");
    String bucket = properties.s3().bucket();
    if (bucket == null || bucket.isBlank()) {
      bucket = "english-reading-documents";
    }

    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
    } catch (Exception e) {
      LOG.warn("Failed best-effort deletion of S3 object: bucket={} key={}", bucket, storageKey, e);
    }
  }
}
