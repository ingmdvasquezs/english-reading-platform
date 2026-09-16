package com.soap.soap.infrastructure.editorial;

import com.soap.soap.application.command.UpdatePlatformReadingProvenanceCommand;
import com.soap.soap.application.port.in.IngestEditorialReadingPort;
import com.soap.soap.application.port.in.PublishPlatformReadingPort;
import com.soap.soap.application.port.in.UpdatePlatformReadingProvenancePort;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EditorialImportCliRunner implements ApplicationRunner {

  private final EditorialManifestParser parser;
  private final IngestEditorialReadingPort ingestion;
  private final UpdatePlatformReadingProvenancePort provenanceUpdate;
  private final PublishPlatformReadingPort publishReadingPort;
  private final EditorialCollectionManifestParser collectionParser;
  private final com.soap.soap.application.port.in.ImportEditorialCollectionPort
      collectionImportPort;
  private final ConfigurableApplicationContext context;
  private Consumer<Integer> exitStrategy;

  @org.springframework.beans.factory.annotation.Autowired
  public EditorialImportCliRunner(
      EditorialManifestParser parser,
      IngestEditorialReadingPort ingestion,
      UpdatePlatformReadingProvenancePort provenanceUpdate,
      PublishPlatformReadingPort publishReadingPort,
      EditorialCollectionManifestParser collectionParser,
      com.soap.soap.application.port.in.ImportEditorialCollectionPort collectionImportPort,
      ConfigurableApplicationContext context) {
    this.parser = parser;
    this.ingestion = ingestion;
    this.provenanceUpdate = provenanceUpdate;
    this.publishReadingPort = publishReadingPort;
    this.collectionParser = collectionParser;
    this.collectionImportPort = collectionImportPort;
    this.context = context;
    this.exitStrategy =
        code -> {
          SpringApplication.exit(context, () -> code);
          System.exit(code);
        };
  }

  public EditorialImportCliRunner(
      EditorialManifestParser parser,
      IngestEditorialReadingPort ingestion,
      UpdatePlatformReadingProvenancePort provenanceUpdate,
      PublishPlatformReadingPort publishReadingPort,
      ConfigurableApplicationContext context) {
    this(parser, ingestion, provenanceUpdate, publishReadingPort, null, null, context);
  }

  // Package-private for testing to prevent System.exit during test execution
  void setExitStrategy(Consumer<Integer> exitStrategy) {
    this.exitStrategy = exitStrategy;
  }

  @Override
  public void run(ApplicationArguments args) {
    boolean hasImport = args.containsOption("editorial-import");
    boolean hasProvenance = args.containsOption("editorial-provenance-update");
    boolean hasPublish = args.containsOption("editorial-publish");
    boolean hasCollectionImport = args.containsOption("editorial-collection-import");

    int operationsCount =
        (hasImport ? 1 : 0)
            + (hasProvenance ? 1 : 0)
            + (hasPublish ? 1 : 0)
            + (hasCollectionImport ? 1 : 0);

    if (operationsCount == 0) {
      return;
    }

    if (operationsCount > 1) {
      log.error("Multiple editorial operations supplied");
      System.err.println(
          "EDITORIAL_OPERATION_FAILED: Exactly one editorial operation may be supplied");
      exitStrategy.accept(1);
      return;
    }

    if (hasImport) {
      handleImport(args);
    } else if (hasProvenance) {
      handleProvenanceUpdate(args);
    } else if (hasPublish) {
      handlePublish(args);
    } else {
      handleCollectionImport(args);
    }
  }

  private void handleImport(ApplicationArguments args) {

    int exitCode = 0;
    try {
      var values = args.getOptionValues("editorial-import");
      if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
        throw new IllegalArgumentException("Option --editorial-import requires a valid file path");
      }
      var path = Path.of(values.getFirst());
      log.info("Starting editorial import from: {}", path);

      var command = parser.parse(path);
      var result = ingestion.ingest(command);

      log.info(
          "Editorial import completed successfully: readingId={}, status={}, created={}, questionsCount={}",
          result.readingId(),
          result.status(),
          result.created(),
          result.questionsCount());
      System.out.println(
          "EDITORIAL_IMPORT_SUCCESS: readingId="
              + result.readingId()
              + ", status="
              + result.status()
              + ", created="
              + result.created());
      exitCode = 0;
    } catch (Exception e) {
      log.error("Editorial import failed: {}", e.getMessage());
      System.err.println("EDITORIAL_IMPORT_FAILED: " + e.getMessage());
      exitCode = 1;
    } finally {
      exitStrategy.accept(exitCode);
    }
  }

  private void handleProvenanceUpdate(ApplicationArguments args) {
    int exitCode = 0;
    try {
      var values = args.getOptionValues("editorial-provenance-update");
      if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
        throw new IllegalArgumentException(
            "Option --editorial-provenance-update requires a valid file path");
      }
      var path = Path.of(values.getFirst());
      log.info("Starting editorial provenance update from: {}", path);

      var parsed = parser.parse(path);
      var command =
          new UpdatePlatformReadingProvenanceCommand(
              parsed.adaptationGroupKey(),
              parsed.language(),
              parsed.editorialLevel(),
              parsed.sourceTitle(),
              parsed.sourceAuthor(),
              parsed.sourceUrl(),
              parsed.sourceNotes());

      var result = provenanceUpdate.updateProvenance(command);

      log.info(
          "Editorial provenance update completed successfully: readingId={}, status={}, title={}",
          result.id(),
          result.editorialStatus(),
          result.title());
      System.out.println(
          "EDITORIAL_PROVENANCE_UPDATE_SUCCESS: readingId="
              + result.id()
              + ", status="
              + result.editorialStatus()
              + ", title="
              + result.title());
      exitCode = 0;
    } catch (Exception e) {
      log.error("Editorial provenance update failed: {}", e.getMessage());
      System.err.println("EDITORIAL_PROVENANCE_UPDATE_FAILED: " + e.getMessage());
      exitCode = 1;
    } finally {
      exitStrategy.accept(exitCode);
    }
  }

  private void handlePublish(ApplicationArguments args) {
    int exitCode = 0;
    try {
      var values = args.getOptionValues("editorial-publish");
      if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
        throw new IllegalArgumentException("Option --editorial-publish requires a valid readingId");
      }
      UUID readingId;
      try {
        readingId = UUID.fromString(values.getFirst().trim());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Invalid UUID format for --editorial-publish: " + values.getFirst());
      }

      log.info("Starting editorial publication for readingId: {}", readingId);
      var result = publishReadingPort.publish(readingId);

      log.info(
          "Editorial publish completed successfully: readingId={}, status={}, title={}",
          result.id(),
          result.editorialStatus(),
          result.title());
      System.out.println(
          "EDITORIAL_PUBLISH_SUCCESS: readingId="
              + result.id()
              + ", status="
              + result.editorialStatus()
              + ", title="
              + result.title());
      exitCode = 0;
    } catch (Exception e) {
      log.error("Editorial publish failed: {}", e.getMessage());
      System.err.println("EDITORIAL_PUBLISH_FAILED: " + e.getMessage());
      exitCode = 1;
    } finally {
      exitStrategy.accept(exitCode);
    }
  }

  private void handleCollectionImport(ApplicationArguments args) {
    int exitCode = 0;
    try {
      var values = args.getOptionValues("editorial-collection-import");
      if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
        throw new IllegalArgumentException(
            "Option --editorial-collection-import requires a valid file path");
      }
      var path = Path.of(values.getFirst());
      log.info("Starting editorial collection import from: {}", path);

      var command = collectionParser.parse(path);
      var result = collectionImportPort.importCollection(command);

      log.info(
          "Editorial collection import completed successfully: collectionId={}, key={}, created={}, memberships={}",
          result.collectionId(),
          result.key(),
          result.created(),
          result.membershipsCount());
      System.out.println(
          "EDITORIAL_COLLECTION_IMPORT_SUCCESS: collectionId="
              + result.collectionId()
              + ", key="
              + result.key()
              + ", created="
              + result.created()
              + ", membershipsCount="
              + result.membershipsCount());
      exitCode = 0;
    } catch (Exception e) {
      log.error("Editorial collection import failed: {}", e.getMessage());
      System.err.println("EDITORIAL_COLLECTION_IMPORT_FAILED: " + e.getMessage());
      exitCode = 1;
    } finally {
      exitStrategy.accept(exitCode);
    }
  }
}
