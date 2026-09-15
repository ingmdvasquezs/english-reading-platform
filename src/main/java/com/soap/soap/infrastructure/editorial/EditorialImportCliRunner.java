package com.soap.soap.infrastructure.editorial;

import com.soap.soap.application.port.in.IngestEditorialReadingPort;
import java.nio.file.Path;
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
  private final ConfigurableApplicationContext context;
  private Consumer<Integer> exitStrategy;

  public EditorialImportCliRunner(
      EditorialManifestParser parser,
      IngestEditorialReadingPort ingestion,
      ConfigurableApplicationContext context) {
    this.parser = parser;
    this.ingestion = ingestion;
    this.context = context;
    this.exitStrategy =
        code -> {
          SpringApplication.exit(context, () -> code);
          System.exit(code);
        };
  }

  // Package-private for testing to prevent System.exit during test execution
  void setExitStrategy(Consumer<Integer> exitStrategy) {
    this.exitStrategy = exitStrategy;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!args.containsOption("editorial-import")) {
      return;
    }

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
}
