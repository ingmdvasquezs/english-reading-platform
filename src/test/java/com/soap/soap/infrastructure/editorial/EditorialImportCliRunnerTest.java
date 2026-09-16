package com.soap.soap.infrastructure.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.IngestEditorialReadingCommand;
import com.soap.soap.application.command.UpdatePlatformReadingProvenanceCommand;
import com.soap.soap.application.exception.EditorialPublicationException;
import com.soap.soap.application.model.IngestEditorialReadingResult;
import com.soap.soap.application.port.in.IngestEditorialReadingPort;
import com.soap.soap.application.port.in.PublishPlatformReadingPort;
import com.soap.soap.application.port.in.UpdatePlatformReadingProvenancePort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class EditorialImportCliRunnerTest {

  private EditorialManifestParser parser;
  private IngestEditorialReadingPort ingestion;
  private UpdatePlatformReadingProvenancePort provenanceUpdate;
  private PublishPlatformReadingPort publishReadingPort;
  private EditorialCollectionManifestParser collectionParser;
  private com.soap.soap.application.port.in.ImportEditorialCollectionPort collectionImportPort;
  private ConfigurableApplicationContext context;
  private EditorialImportCliRunner runner;
  private AtomicInteger exitCode;

  @BeforeEach
  void setUp() {
    parser = mock(EditorialManifestParser.class);
    ingestion = mock(IngestEditorialReadingPort.class);
    provenanceUpdate = mock(UpdatePlatformReadingProvenancePort.class);
    publishReadingPort = mock(PublishPlatformReadingPort.class);
    collectionParser = mock(EditorialCollectionManifestParser.class);
    collectionImportPort =
        mock(com.soap.soap.application.port.in.ImportEditorialCollectionPort.class);
    context = mock(ConfigurableApplicationContext.class);
    runner =
        new EditorialImportCliRunner(
            parser,
            ingestion,
            provenanceUpdate,
            publishReadingPort,
            collectionParser,
            collectionImportPort,
            context);
    exitCode = new AtomicInteger(-999);
    runner.setExitStrategy(exitCode::set);
  }

  @Test
  void doesNothingWhenNoEditorialArgumentPassed() {
    var args = new DefaultApplicationArguments();
    runner.run(args);

    verifyNoInteractions(parser, ingestion, provenanceUpdate, publishReadingPort);
    assertThat(exitCode.get()).isEqualTo(-999); // Exit strategy not called
  }

  @Test
  void executesIngestionAndExitsWithZeroOnSuccess() {
    var command = mock(IngestEditorialReadingCommand.class);
    when(parser.parse(Path.of("test-path.json"))).thenReturn(command);
    when(ingestion.ingest(command))
        .thenReturn(
            new IngestEditorialReadingResult(UUID.randomUUID(), true, EditorialStatus.DRAFT, 6));

    var args = new DefaultApplicationArguments("--editorial-import=test-path.json");
    runner.run(args);

    verify(parser).parse(Path.of("test-path.json"));
    verify(ingestion).ingest(command);
    assertThat(exitCode.get()).isEqualTo(0);
  }

  @Test
  void executesProvenanceUpdateAndExitsWithZeroOnSuccess() {
    var command =
        new IngestEditorialReadingCommand(
            "The Mohán and the Spring of Water",
            "content",
            "en",
            EditorialLevel.B1,
            "Culture",
            "short desc",
            null,
            "CO",
            null,
            null,
            null,
            null,
            "es",
            "Cuento del Mohán",
            "Comunidad de Pasuncha",
            "https://url.com",
            "notes",
            "mohan-pasuncha",
            "mohan-pasuncha",
            null,
            null,
            null);

    var reading = mock(Reading.class);
    when(reading.id()).thenReturn(UUID.randomUUID());
    when(reading.editorialStatus()).thenReturn(EditorialStatus.PUBLISHED);
    when(reading.title()).thenReturn("The Mohán and the Spring of Water");

    when(parser.parse(Path.of("provenance-path.json"))).thenReturn(command);
    when(provenanceUpdate.updateProvenance(any(UpdatePlatformReadingProvenanceCommand.class)))
        .thenReturn(reading);

    var args =
        new DefaultApplicationArguments("--editorial-provenance-update=provenance-path.json");
    runner.run(args);

    verify(parser).parse(Path.of("provenance-path.json"));
    verify(provenanceUpdate).updateProvenance(any(UpdatePlatformReadingProvenanceCommand.class));
    assertThat(exitCode.get()).isEqualTo(0);
  }

  @Test
  void executesPublicationAndExitsWithZeroOnSuccess() {
    var readingId = UUID.randomUUID();
    var reading = mock(Reading.class);
    when(reading.id()).thenReturn(readingId);
    when(reading.editorialStatus()).thenReturn(EditorialStatus.PUBLISHED);
    when(reading.title()).thenReturn("The Mohán and the Spring of Water");

    when(publishReadingPort.publish(readingId)).thenReturn(reading);

    var args = new DefaultApplicationArguments("--editorial-publish=" + readingId);
    runner.run(args);

    verify(publishReadingPort).publish(readingId);
    assertThat(exitCode.get()).isEqualTo(0);
  }

  @Test
  void rejectsMalformedUuidAndExitsWithNonZeroWithoutCallingPublishPort() {
    var args = new DefaultApplicationArguments("--editorial-publish=not-a-uuid");
    runner.run(args);

    verifyNoInteractions(publishReadingPort);
    assertThat(exitCode.get()).isEqualTo(1);
  }

  @Test
  void exitsWithNonZeroOnPublicationFailure() {
    var readingId = UUID.randomUUID();
    when(publishReadingPort.publish(readingId))
        .thenThrow(new EditorialPublicationException("Only DRAFT readings can be published"));

    var args = new DefaultApplicationArguments("--editorial-publish=" + readingId);
    runner.run(args);

    verify(publishReadingPort).publish(readingId);
    assertThat(exitCode.get()).isEqualTo(1);
  }

  @Test
  void rejectsMultipleEditorialOperationsAndExitsWithNonZero() {
    var args =
        new DefaultApplicationArguments(
            "--editorial-import=path.json", "--editorial-publish=" + UUID.randomUUID());
    runner.run(args);

    verifyNoInteractions(parser, ingestion, provenanceUpdate, publishReadingPort);
    assertThat(exitCode.get()).isEqualTo(1);
  }

  @Test
  void exitsWithNonZeroOnIngestionFailure() {
    when(parser.parse(any(Path.class)))
        .thenThrow(new EditorialManifestParseException("File not readable"));

    var args = new DefaultApplicationArguments("--editorial-import=invalid.json");
    runner.run(args);

    verifyNoInteractions(ingestion, provenanceUpdate, publishReadingPort);
    assertThat(exitCode.get()).isEqualTo(1);
  }

  @Test
  void executesCollectionImportAndExitsWithZeroOnSuccess() {
    var command = mock(com.soap.soap.application.command.ImportEditorialCollectionCommand.class);
    when(collectionParser.parse(Path.of("collection-path.json"))).thenReturn(command);
    when(collectionImportPort.importCollection(command))
        .thenReturn(
            new com.soap.soap.application.model.ImportEditorialCollectionResult(
                UUID.randomUUID(), "key", "title", true, 5));

    var args =
        new DefaultApplicationArguments("--editorial-collection-import=collection-path.json");
    runner.run(args);

    verify(collectionParser).parse(Path.of("collection-path.json"));
    verify(collectionImportPort).importCollection(command);
    assertThat(exitCode.get()).isEqualTo(0);
  }

  @Test
  void exitsWithNonZeroOnCollectionImportFailure() {
    when(collectionParser.parse(any(Path.class)))
        .thenThrow(
            new com.soap.soap.application.exception.EditorialCollectionImportException(
                "Invalid collection"));

    var args = new DefaultApplicationArguments("--editorial-collection-import=invalid.json");
    runner.run(args);

    verifyNoInteractions(collectionImportPort);
    assertThat(exitCode.get()).isEqualTo(1);
  }

  @Test
  void rejectsCollectionImportCombinedWithOtherOperation() {
    var args =
        new DefaultApplicationArguments(
            "--editorial-collection-import=col.json", "--editorial-publish=" + UUID.randomUUID());
    runner.run(args);

    verifyNoInteractions(
        parser, ingestion, provenanceUpdate, publishReadingPort, collectionImportPort);
    assertThat(exitCode.get()).isEqualTo(1);
  }
}
