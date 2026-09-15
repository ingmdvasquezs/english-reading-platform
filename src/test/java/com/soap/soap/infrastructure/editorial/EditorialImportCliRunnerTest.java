package com.soap.soap.infrastructure.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.IngestEditorialReadingCommand;
import com.soap.soap.application.model.IngestEditorialReadingResult;
import com.soap.soap.application.port.in.IngestEditorialReadingPort;
import com.soap.soap.domain.model.EditorialStatus;
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
  private ConfigurableApplicationContext context;
  private EditorialImportCliRunner runner;
  private AtomicInteger exitCode;

  @BeforeEach
  void setUp() {
    parser = mock(EditorialManifestParser.class);
    ingestion = mock(IngestEditorialReadingPort.class);
    context = mock(ConfigurableApplicationContext.class);
    runner = new EditorialImportCliRunner(parser, ingestion, context);
    exitCode = new AtomicInteger(-999);
    runner.setExitStrategy(exitCode::set);
  }

  @Test
  void doesNothingWhenNoEditorialImportArgumentPassed() {
    var args = new DefaultApplicationArguments();
    runner.run(args);

    verifyNoInteractions(parser, ingestion);
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
  void exitsWithNonZeroOnFailure() {
    when(parser.parse(any(Path.class)))
        .thenThrow(new EditorialManifestParseException("File not readable"));

    var args = new DefaultApplicationArguments("--editorial-import=invalid.json");
    runner.run(args);

    verifyNoInteractions(ingestion);
    assertThat(exitCode.get()).isEqualTo(1);
  }
}
