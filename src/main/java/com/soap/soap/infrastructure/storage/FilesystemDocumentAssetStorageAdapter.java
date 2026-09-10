package com.soap.soap.infrastructure.storage;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.domain.model.DocumentFormat;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilesystemDocumentAssetStorageAdapter implements DocumentAssetStoragePort {
  private final Path documentStorageRoot;
  private final DocumentImportLimits limits;

  @Override
  public String storeSource(UUID userId, UUID documentId, Path source) {
    return storeSource(userId, documentId, source, DocumentFormat.EPUB);
  }

  @Override
  public String storeSource(UUID userId, UUID documentId, Path source, DocumentFormat format) {
    try {
      if (Files.size(source) > limits.maxSourceBytes()) {
        throw new DocumentImportException(Reason.FILE_TOO_LARGE, "Document exceeds source limit");
      }
      var key =
          key(userId, documentId, format == DocumentFormat.PDF ? "source.pdf" : "source.epub");
      atomicCopy(source, resolve(key));
      return key;
    } catch (DocumentImportException exception) {
      throw exception;
    } catch (IOException exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public String storeCover(UUID userId, UUID documentId, String mediaType, byte[] bytes) {
    if (bytes.length > limits.maxCoverBytes()) {
      throw new DocumentImportException(Reason.SECURITY_LIMIT_EXCEEDED, "Cover exceeds byte limit");
    }
    var key = key(userId, documentId, "cover." + extension(mediaType));
    try {
      var target = resolve(key);
      Files.createDirectories(target.getParent());
      var temporary = Files.createTempFile(target.getParent(), ".cover-", ".tmp");
      try {
        Files.write(temporary, bytes);
        moveAtomically(temporary, target);
      } finally {
        Files.deleteIfExists(temporary);
      }
      return key;
    } catch (IOException exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public void delete(String assetKey) {
    if (assetKey == null) return;
    try {
      var target = resolve(assetKey);
      Files.deleteIfExists(target);
      deleteEmptyParents(target.getParent());
    } catch (IOException exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public Path locate(String assetKey) {
    var path = resolve(assetKey);
    if (!Files.isRegularFile(path)) {
      throw new DocumentImportException(Reason.STORAGE_FAILURE, "Document asset is missing");
    }
    return path;
  }

  @Override
  public int deleteUnreferenced(Set<String> referencedKeys, Instant olderThan) {
    var documentsRoot = documentStorageRoot.resolve("documents");
    if (!Files.isDirectory(documentsRoot)) return 0;
    try (var paths = Files.walk(documentsRoot)) {
      var candidates =
          paths
              .filter(Files::isRegularFile)
              .filter(
                  path -> {
                    var key = documentStorageRoot.relativize(path).toString().replace('\\', '/');
                    if (referencedKeys.contains(key)) return false;
                    try {
                      return Files.getLastModifiedTime(path).toInstant().isBefore(olderThan);
                    } catch (IOException exception) {
                      return false;
                    }
                  })
              .toList();
      for (var candidate : candidates) Files.deleteIfExists(candidate);
      return candidates.size();
    } catch (IOException exception) {
      throw storageFailure(exception);
    }
  }

  private void atomicCopy(Path source, Path target) throws IOException {
    Files.createDirectories(target.getParent());
    var temporary = Files.createTempFile(target.getParent(), ".source-", ".tmp");
    try {
      Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
      moveAtomically(temporary, target);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Path resolve(String key) {
    var target = documentStorageRoot.resolve(key).normalize();
    if (!target.startsWith(documentStorageRoot)) {
      throw new DocumentImportException(Reason.STORAGE_FAILURE, "Invalid asset key");
    }
    return target;
  }

  private String key(UUID userId, UUID documentId, String filename) {
    return "documents/" + userId + "/" + documentId + "/" + filename;
  }

  private String extension(String mediaType) {
    return switch (mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT)) {
      case "image/jpeg" -> "jpg";
      case "image/gif" -> "gif";
      case "image/webp" -> "webp";
      default -> "png";
    };
  }

  private void deleteEmptyParents(Path directory) throws IOException {
    var current = directory;
    while (current != null && !current.equals(documentStorageRoot)) {
      try (var children = Files.list(current)) {
        if (children.findAny().isPresent()) return;
      }
      Files.deleteIfExists(current);
      current = current.getParent();
    }
  }

  private DocumentImportException storageFailure(Exception cause) {
    return new DocumentImportException(
        Reason.STORAGE_FAILURE, "Document asset storage failed", cause);
  }
}
