package com.soap.soap.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.DocumentImportLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemDocumentAssetStorageAdapterTest {
  @TempDir Path root;

  @Test
  void storesSourceAndCoverUnderGeneratedPrivateKeysAndDeletesThem() throws Exception {
    var storage = storage();
    var userId = UUID.randomUUID();
    var documentId = UUID.randomUUID();
    var source = root.resolve("../unsafe-name.epub").normalize();
    Files.writeString(source, "source");

    var sourceKey = storage.storeSource(userId, documentId, source);
    var coverKey = storage.storeCover(userId, documentId, "image/jpeg", new byte[] {1, 2, 3});

    assertThat(sourceKey).isEqualTo("documents/" + userId + "/" + documentId + "/source.epub");
    assertThat(coverKey).isEqualTo("documents/" + userId + "/" + documentId + "/cover.jpg");
    assertThat(Files.readString(root.resolve(sourceKey))).isEqualTo("source");
    assertThat(Files.readAllBytes(root.resolve(coverKey))).containsExactly(1, 2, 3);

    storage.delete(sourceKey);
    storage.delete(coverKey);
    assertThat(root.resolve("documents").resolve(userId.toString()).resolve(documentId.toString()))
        .doesNotExist();
  }

  @Test
  void callerFilenameCanNeverInfluenceTheStoredPath() throws Exception {
    var storage = storage();
    var source = root.resolve("payload.epub");
    Files.writeString(source, "data");

    var key = storage.storeSource(UUID.randomUUID(), UUID.randomUUID(), source);

    assertThat(key).doesNotContain("payload").doesNotContain("..").doesNotContain("\\");
  }

  private FilesystemDocumentAssetStorageAdapter storage() {
    return new FilesystemDocumentAssetStorageAdapter(
        root.toAbsolutePath().normalize(),
        new DocumentImportLimits(1_000, 10, 1_000, 2_000, 100, 1_000, 1_000));
  }
}
