package com.soap.soap.spike.epub;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.command.ImportEpubCommand;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.DocumentChunker;
import com.soap.soap.application.service.DocumentLanguageResolver;
import com.soap.soap.application.usecase.ImportEpubUseCase;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.User;
import com.soap.soap.infrastructure.document.EpubDocumentParserAdapter;
import com.soap.soap.infrastructure.storage.FilesystemDocumentAssetStorageAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class EpubImportIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired UserRepositoryPort users;
  @Autowired ImportedDocumentRepositoryPort documents;
  @Autowired JdbcTemplate jdbc;

  @Test
  void importsARealEpubIntoPostgresAndPrivateFilesystemWithoutCreatingUserState(
      @TempDir Path directory) throws Exception {
    var user =
        users.save(
            new User(
                null, "EPUB Owner", "epub-" + UUID.randomUUID() + "@example.com", "hash", null));
    var limits =
        new DocumentImportLimits(
            50L << 20, 2_000, 10L << 20, 150L << 20, 100, 10L << 20, 25_000_000);
    var storage = new FilesystemDocumentAssetStorageAdapter(directory.resolve("assets"), limits);
    var useCase =
        new ImportEpubUseCase(
            () -> user.id(),
            users,
            new EpubDocumentParserAdapter(limits),
            storage,
            documents,
            new DocumentLanguageResolver(),
            new DocumentChunker(),
            Clock.systemUTC());
    var source = EpubFixtureFactory.epub3(directory.resolve("source.epub"), true, true);

    var imported = useCase.importEpub(new ImportEpubCommand(source, "original.epub", null));

    assertThat(imported.importStatus()).isEqualTo(DocumentImportStatus.READY);
    assertThat(imported.chunkingVersion()).isEqualTo(4);
    assertThat(imported.language()).isEqualTo("en-US");
    assertThat(imported.sourceSha256()).hasSize(64);
    assertThat(Files.size(directory.resolve("assets").resolve(imported.sourceAssetKey())))
        .isEqualTo(Files.size(source));
    assertThat(directory.resolve("assets").resolve(imported.coverAssetKey())).exists();
    assertThat(documents.findSections(imported.id())).hasSize(2);
    assertThat(documents.findSectionNavigation(imported.id()))
        .allSatisfy(
            section -> {
              assertThat(section.firstUnitId()).isNotNull();
              assertThat(section.unitCount()).isPositive();
            })
        .extracting(section -> section.title())
        .containsExactly("First", "Second");
    assertThat(documents.findUnits(imported.id()))
        .hasSize(2)
        .extracting(unit -> unit.globalOrdinal())
        .containsExactly(1, 2);
    assertThat(count("document_progress", user.id())).isZero();
    assertThat(count("user_vocabulary", user.id())).isZero();
  }

  private int count(String table, UUID userId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
  }
}
