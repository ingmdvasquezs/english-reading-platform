package com.soap.soap.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.domain.model.DocumentFormat;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BACK-002 regressions: real decompressed-byte, extracted-character and time budgets for EPUB. */
class EpubResourceLimitsTest {
  /** Production defaults from application.yaml. */
  private static final DocumentImportLimits PRODUCTION =
      new DocumentImportLimits(
          52_428_800L,
          2_000,
          10_485_760L,
          157_286_400L,
          100,
          10_485_760L,
          25_000_000,
          1_000,
          5_000_000,
          Duration.ofSeconds(120));

  private static final String REPEATED_PARAGRAPH =
      "<p>The quick brown fox jumps over the lazy dog while the reader keeps reading this"
          + " sentence again and again for the probe.</p>\n";

  @TempDir Path directory;

  @Test
  @DisplayName("A. An honest, normal EPUB is accepted")
  void honestNormalEpubIsAccepted() throws Exception {
    var source = write("normal.epub", variedBook(3, 60), false);

    var parsed = new EpubDocumentParserAdapter(PRODUCTION).parse(source, DocumentFormat.EPUB);

    assertThat(parsed.sections()).hasSize(3);
    assertThat(parsed.title()).isEqualTo("Probe");
  }

  @Test
  @DisplayName("B. Honest high compression ratio is still rejected by the declared-ratio defense")
  void honestHighCompressionRatioIsRejectedByTheExistingDefense() throws Exception {
    var source = write("honest-bomb.epub", repetitiveBook(3, 12_000), false);

    assertSecurityLimit(
        () -> new EpubDocumentParserAdapter(PRODUCTION).parse(source, DocumentFormat.EPUB),
        "ZIP compression ratio exceeds limit");
  }

  @Test
  @DisplayName(
      "C + adversarial probe: ~45 KB EPUB with forged central-directory sizes is rejected early")
  void forgedCentralDirectoryIsRejectedByRealByteAccounting() throws Exception {
    // Same shape as the audit probe that extracted 11.4M characters (~182 MB heap) before the fix.
    var source = write("forged.epub", repetitiveBook(8, 12_000), true);
    assertThat(Files.size(source)).isLessThan(60_000);

    var started = System.nanoTime();
    var failure =
        assertSecurityLimit(
            () -> new EpubDocumentParserAdapter(PRODUCTION).parse(source, DocumentFormat.EPUB),
            "ZIP entry is larger than its declared size");
    var elapsedMillis = (System.nanoTime() - started) / 1_000_000;

    // At most one 64 KiB buffer beyond the forged declared size was decompressed.
    var matcher = Pattern.compile("declared=(\\d+), read>=(\\d+)").matcher(failure.getMessage());
    assertThat(matcher.find()).isTrue();
    var declared = Long.parseLong(matcher.group(1));
    var read = Long.parseLong(matcher.group(2));
    assertThat(read).isGreaterThan(declared).isLessThanOrEqualTo(declared + 64 * 1024);
    assertThat(elapsedMillis).isLessThan(5_000);
  }

  @Test
  @DisplayName("D. Extracted characters are counted while extracting and stop the parse early")
  void characterLimitStopsExtractionBeforeTheWholeDocumentIsAccumulated() throws Exception {
    var source = write("long.epub", variedBook(6, 400), false);
    var total = totalCharacters(source);
    var limit = Math.toIntExact(total / 6); // below the first chapter's text

    var failure =
        assertSecurityLimit(
            () -> parserWithCharacterLimit(limit).parse(source, DocumentFormat.EPUB),
            "Extracted characters exceed limit");

    var extracted =
        Long.parseLong(
            Pattern.compile("extracted=(\\d+)")
                .matcher(failure.getMessage())
                .results()
                .findFirst()
                .orElseThrow()
                .group(1));
    assertThat(extracted).isGreaterThan(limit).isLessThan(total / 3);
  }

  @Test
  @DisplayName("E/F. Character limit boundary: exactly the total passes, total - 1 fails")
  void characterLimitBoundary() throws Exception {
    var source = write("boundary.epub", variedBook(2, 50), false);
    var total = Math.toIntExact(totalCharacters(source));

    assertThat(parserWithCharacterLimit(total).parse(source, DocumentFormat.EPUB).sections())
        .hasSize(2);
    assertSecurityLimit(
        () -> parserWithCharacterLimit(total - 1).parse(source, DocumentFormat.EPUB),
        "Extracted characters exceed limit");
  }

  @Test
  @DisplayName("Real uncompressed byte budget: exact limit passes, limit + 1 fails immediately")
  void realUncompressedByteBudgetBoundary() {
    var limits =
        new DocumentImportLimits(
            1_000, 10, 100, 1_000, 100, 100, 100, 10, 1_000, Duration.ofSeconds(1));
    var budget = new DocumentParseBudget(limits, () -> 0L);

    budget.addUncompressedBytes(600);
    budget.addUncompressedBytes(400);
    assertThat(budget.uncompressedBytes()).isEqualTo(1_000);
    assertThatThrownBy(() -> budget.addUncompressedBytes(1))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> {
              assertThat(exception.reason()).isEqualTo(Reason.SECURITY_LIMIT_EXCEEDED);
              assertThat(exception.getMessage()).contains("read=1001", "limit=1000");
            });
  }

  @Test
  @DisplayName(
      "Real archive total: honest entries whose combined size crosses the total are rejected")
  void realTotalAcrossEntriesIsEnforcedOnDecompressedBytes() throws Exception {
    var source = write("total.epub", variedBook(4, 60), false);
    var limits =
        new DocumentImportLimits(
            10_000_000,
            2_000,
            30_000,
            30_000,
            1_000,
            10_000_000,
            25_000_000,
            1_000,
            5_000_000,
            Duration.ofSeconds(120));

    // Declared sizes are honest, so either the declared total or the real total rejects it; both
    // are SECURITY_LIMIT_EXCEEDED and neither lets the parse reach XHTML extraction.
    assertThatThrownBy(
            () -> new EpubDocumentParserAdapter(limits).parse(source, DocumentFormat.EPUB))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> assertThat(exception.reason()).isEqualTo(Reason.SECURITY_LIMIT_EXCEEDED));
  }

  @Test
  @DisplayName("J. Expired deadline aborts deterministically before any parsing work")
  void expiredDeadlineAbortsDeterministically() throws Exception {
    var source = write("deadline.epub", variedBook(2, 50), false);
    var clock = new AtomicLong();
    // The budget reads the clock once at start; every later check sees the deadline passed.
    var parser =
        new EpubDocumentParserAdapter(
            PRODUCTION, () -> clock.getAndSet(Duration.ofMinutes(10).toNanos()));

    assertSecurityLimit(
        () -> parser.parse(source, DocumentFormat.EPUB),
        "processing time limit exceeded during ZIP size verification");
  }

  @Test
  @DisplayName("J. EpubCheck runs inside the remaining budget and times out deterministically")
  void epubCheckIsBoundedByTheRemainingBudget() throws Exception {
    var source = write("epubcheck.epub", variedBook(2, 50), false);
    var deadline = PRODUCTION.maxProcessingTime().toNanos();
    var calls = new AtomicLong();
    // Start at 0; afterwards report 1 ns left, so every cooperative check passes but EpubCheck,
    // which needs far more than 1 ns, cannot finish inside the budget.
    var parser =
        new EpubDocumentParserAdapter(
            PRODUCTION, () -> calls.getAndIncrement() == 0 ? 0L : deadline - 1);

    assertSecurityLimit(
        () -> parser.parse(source, DocumentFormat.EPUB),
        "processing time limit exceeded during EPUB validation");

    // The abandoned validation thread has bounded input and finishes on its own; wait for it so
    // the temporary EPUB can be deleted (Windows keeps open files locked).
    for (var thread : Thread.getAllStackTraces().keySet()) {
      if ("epubcheck-validation".equals(thread.getName())) {
        thread.join(Duration.ofSeconds(60));
        assertThat(thread.isAlive()).isFalse();
      }
    }
  }

  @Test
  @DisplayName("Limits reject non-positive values and incoherent relations at startup")
  void limitsAreValidated() {
    assertThatThrownBy(
            () ->
                new DocumentImportLimits(
                    1_000, 10, 100, 1_000, 100, 100, 100, 10, 1_000, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DocumentImportLimits(
                    1_000, 10, 2_000, 1_000, 100, 100, 100, 10, 1_000, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("per-entry");
  }

  private EpubDocumentParserAdapter parserWithCharacterLimit(int characters) {
    return new EpubDocumentParserAdapter(
        new DocumentImportLimits(
            52_428_800L,
            2_000,
            10_485_760L,
            157_286_400L,
            100,
            10_485_760L,
            25_000_000,
            1_000,
            characters,
            Duration.ofSeconds(120)));
  }

  private long totalCharacters(Path source) {
    return new EpubDocumentParserAdapter(PRODUCTION)
        .parse(source, DocumentFormat.EPUB).sections().stream()
            .flatMap(section -> section.blocks().stream())
            .mapToLong(String::length)
            .sum();
  }

  private static DocumentImportException assertSecurityLimit(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String messagePart) {
    var holder = new DocumentImportException[1];
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> {
              assertThat(exception.reason()).isEqualTo(Reason.SECURITY_LIMIT_EXCEEDED);
              assertThat(exception.getMessage()).containsIgnoringCase(messagePart);
              holder[0] = exception;
            });
    return holder[0];
  }

  private static Map<String, byte[]> variedBook(int chapters, int paragraphs) {
    var random = new Random(42);
    String[] words = {
      "river", "mountain", "quiet", "village", "teacher", "window", "morning", "garden", "letter",
      "journey", "bright", "market", "silver", "storm", "harbor", "lantern", "forest", "candle"
    };
    return book(
        chapters,
        chapter -> {
          var body = new StringBuilder();
          for (int p = 0; p < paragraphs; p++) {
            body.append("<p>");
            for (int w = 0; w < 30; w++) {
              body.append(words[random.nextInt(words.length)])
                  .append(random.nextInt(10_000))
                  .append(' ');
            }
            body.append("</p>\n");
          }
          return body.toString();
        });
  }

  private static Map<String, byte[]> repetitiveBook(int chapters, int paragraphs) {
    return book(chapters, chapter -> REPEATED_PARAGRAPH.repeat(paragraphs));
  }

  private static Map<String, byte[]> book(
      int chapters, java.util.function.IntFunction<String> chapterBody) {
    var entries = new LinkedHashMap<String, byte[]>();
    entries.put("mimetype", bytes("application/epub+zip"));
    entries.put(
        "META-INF/container.xml",
        bytes(
            "<?xml version=\"1.0\"?><container version=\"1.0\""
                + " xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles>"
                + "<rootfile full-path=\"OPS/package.opf\""
                + " media-type=\"application/oebps-package+xml\"/></rootfiles></container>"));
    var manifest = new StringBuilder();
    var spine = new StringBuilder();
    for (int i = 1; i <= chapters; i++) {
      manifest.append(
          "<item id=\"c%d\" href=\"ch%d.xhtml\" media-type=\"application/xhtml+xml\"/>"
              .formatted(i, i));
      spine.append("<itemref idref=\"c%d\"/>".formatted(i));
    }
    entries.put(
        "OPS/package.opf",
        bytes(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package"
                + " xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\""
                + " unique-identifier=\"id\"><metadata"
                + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier"
                + " id=\"id\">urn:probe</dc:identifier><dc:title>Probe</dc:title>"
                + "<dc:language>en</dc:language><meta property=\"dcterms:modified\">"
                + "2026-01-01T00:00:00Z</meta></metadata><manifest>"
                + manifest
                + "</manifest><spine>"
                + spine
                + "</spine></package>"));
    for (int i = 1; i <= chapters; i++) {
      entries.put(
          "OPS/ch" + i + ".xhtml",
          bytes(
              "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html"
                  + " xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>c"
                  + i
                  + "</title></head><body><h1>Chapter "
                  + i
                  + "</h1>\n"
                  + chapterBody.apply(i)
                  + "</body></html>"));
    }
    return entries;
  }

  /**
   * Writes a ZIP. With {@code forgeSizes}, every XHTML entry's central-directory uncompressed size
   * is rewritten to twice its compressed size (an innocent-looking 2:1 ratio).
   */
  private Path write(String name, Map<String, byte[]> entries, boolean forgeSizes)
      throws Exception {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      for (var item : entries.entrySet()) {
        var entry = new ZipEntry(item.getKey());
        if (item.getKey().equals("mimetype")) {
          var crc = new CRC32();
          crc.update(item.getValue());
          entry.setMethod(ZipEntry.STORED);
          entry.setSize(item.getValue().length);
          entry.setCompressedSize(item.getValue().length);
          entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry);
        zip.write(item.getValue());
        zip.closeEntry();
      }
    }
    var bytes = output.toByteArray();
    if (forgeSizes) {
      var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      for (int i = 0; i + 46 < bytes.length; i++) {
        if (buffer.getInt(i) != 0x02014b50) continue; // central directory file header
        int nameLength = Short.toUnsignedInt(buffer.getShort(i + 28));
        var entryName = new String(bytes, i + 46, nameLength, StandardCharsets.UTF_8);
        if (entryName.endsWith(".xhtml")) buffer.putInt(i + 24, buffer.getInt(i + 20) * 2);
      }
    }
    var path = directory.resolve(name);
    Files.write(path, bytes);
    return path;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
