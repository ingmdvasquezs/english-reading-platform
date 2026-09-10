package com.soap.soap.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.domain.model.DocumentFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfDocumentParserAdapterTest {
  private static final DocumentImportLimits LIMITS =
      new DocumentImportLimits(
          1_000_000, 100, 1_000_000, 2_000_000, 100, 100_000, 1_000_000, 10, 10_000);

  @Test
  void extractsMetadataLanguageAndOneSectionPerPage(@TempDir Path directory) throws Exception {
    var source = directory.resolve("book.pdf");
    try (var document = new PDDocument()) {
      document.getDocumentInformation().setTitle("  A Useful Book  ");
      document.getDocumentInformation().setAuthor("Ada Author");
      document.getDocumentCatalog().setLanguage("en_US");
      addPage(document, "First page has enough readable content.");
      addPage(document, "Second page also contains useful text.");
      document.save(source.toFile());
    }

    var parsed = new PdfDocumentParserAdapter(LIMITS).parse(source, DocumentFormat.PDF);

    assertThat(parsed.title()).isEqualTo("A Useful Book");
    assertThat(parsed.author()).isEqualTo("Ada Author");
    assertThat(parsed.declaredLanguage()).isEqualTo("en-US");
    assertThat(parsed.cover()).isNull();
    assertThat(parsed.sections()).hasSize(2);
    assertThat(parsed.sections())
        .extracting(section -> section.sourceLocator())
        .containsExactly("page:1", "page:2");
    assertThat(parsed.sections()).allSatisfy(section -> assertThat(section.title()).isNull());
  }

  @Test
  void usesEditorialHeadingWhenMetadataIsMissing(@TempDir Path directory) throws Exception {
    var source = directory.resolve("heading.pdf");
    try (var document = new PDDocument()) {
      addPage(
          document,
          "English Reading Sample - Page 1",
          "The Last Light of Alder Harbor",
          "A short English reading sample",
          "Chapter 1 - The Letter",
          "On a cold Monday morning, Daniel found a letter under his door.");
      document.save(source.toFile());
    }

    var parsed = new PdfDocumentParserAdapter(LIMITS).parse(source, DocumentFormat.PDF);

    assertThat(parsed.title()).isEqualTo("The Last Light of Alder Harbor");
    assertThat(parsed.author()).isNull();
  }

  @Test
  void rejectsAnonymousOrEmptyMetadataAndFallsBackConservatively(@TempDir Path directory)
      throws Exception {
    var anonymous = directory.resolve("anonymous.pdf");
    try (var document = new PDDocument()) {
      document.getDocumentInformation().setTitle("(anonymous)");
      document.getDocumentInformation().setAuthor("(anonymous)");
      addPage(document, "The Last Light of Alder Harbor", "Readable body content follows here.");
      document.save(anonymous.toFile());
    }
    var empty = directory.resolve("empty.pdf");
    try (var document = new PDDocument()) {
      document.getDocumentInformation().setTitle("   ");
      addPage(document, "ordinary lowercase body text without an editorial heading");
      document.save(empty.toFile());
    }

    var parser = new PdfDocumentParserAdapter(LIMITS);
    var parsedAnonymous = parser.parse(anonymous, DocumentFormat.PDF);
    var parsedEmpty = parser.parse(empty, DocumentFormat.PDF);

    assertThat(parsedAnonymous.title()).isEqualTo("The Last Light of Alder Harbor");
    assertThat(parsedAnonymous.author()).isNull();
    assertThat(parsedEmpty.title()).isNull();
  }

  @Test
  void conservativelyRemovesRepeatedEdgesPageNumbersAndLowercaseHyphenation(@TempDir Path directory)
      throws Exception {
    var source = directory.resolve("clean.pdf");
    try (var document = new PDDocument()) {
      addPage(
          document, "Shared Header", "A long exam-", "ple remains readable.", "1", "Shared Footer");
      addPage(
          document, "Shared Header", "Second page contains readable words.", "2", "Shared Footer");
      addPage(
          document, "Shared Header", "Third page contains readable words.", "3", "Shared Footer");
      document.save(source.toFile());
    }

    var parsed = new PdfDocumentParserAdapter(LIMITS).parse(source, DocumentFormat.PDF);
    var text = parsed.sections().stream().flatMap(section -> section.blocks().stream()).toList();

    assertThat(text)
        .noneMatch(value -> value.contains("Shared Header") || value.contains("Shared Footer"));
    assertThat(text.getFirst()).contains("example remains readable");
  }

  @Test
  void rejectsPasswordProtectedPdfWithSpecificReason(@TempDir Path directory) throws Exception {
    var source = directory.resolve("protected.pdf");
    try (var document = new PDDocument()) {
      addPage(document, "Secret but otherwise readable content.");
      var policy =
          new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
      policy.setEncryptionKeyLength(128);
      document.protect(policy);
      document.save(source.toFile());
    }

    assertReason(source, DocumentImportException.Reason.PDF_PASSWORD_PROTECTED);
  }

  @Test
  void rejectsImageOnlyOrBlankPdfAsScanned(@TempDir Path directory) throws Exception {
    var source = directory.resolve("scan.pdf");
    try (var document = new PDDocument()) {
      document.addPage(new PDPage());
      document.save(source.toFile());
    }

    assertReason(source, DocumentImportException.Reason.PDF_SCANNED_NOT_SUPPORTED);
  }

  @Test
  void rejectsMalformedPdfWithSpecificReason(@TempDir Path directory) throws Exception {
    var source = Files.writeString(directory.resolve("broken.pdf"), "%PDF-not-really-a-pdf");
    assertReason(source, DocumentImportException.Reason.INVALID_PDF);
  }

  @Test
  void enforcesPageAndExtractedCharacterLimits(@TempDir Path directory) throws Exception {
    var pages = directory.resolve("pages.pdf");
    try (var document = new PDDocument()) {
      addPage(document, "First readable page content.");
      addPage(document, "Second readable page content.");
      document.save(pages.toFile());
    }
    var pageLimited =
        new DocumentImportLimits(
            1_000_000, 100, 1_000_000, 2_000_000, 100, 100_000, 1_000_000, 1, 10_000);
    assertThatThrownBy(
            () -> new PdfDocumentParserAdapter(pageLimited).parse(pages, DocumentFormat.PDF))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception ->
                assertThat(exception.reason())
                    .isEqualTo(DocumentImportException.Reason.SECURITY_LIMIT_EXCEEDED));

    var characterLimited =
        new DocumentImportLimits(
            1_000_000, 100, 1_000_000, 2_000_000, 100, 100_000, 1_000_000, 10, 5);
    assertThatThrownBy(
            () -> new PdfDocumentParserAdapter(characterLimited).parse(pages, DocumentFormat.PDF))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception ->
                assertThat(exception.reason())
                    .isEqualTo(DocumentImportException.Reason.SECURITY_LIMIT_EXCEEDED));
  }

  private void assertReason(Path source, DocumentImportException.Reason reason) {
    assertThatThrownBy(() -> new PdfDocumentParserAdapter(LIMITS).parse(source, DocumentFormat.PDF))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> assertThat(exception.reason()).isEqualTo(reason));
  }

  private static void addPage(PDDocument document, String... lines) throws Exception {
    var page = new PDPage();
    document.addPage(page);
    try (var content = new PDPageContentStream(document, page)) {
      content.beginText();
      content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
      content.newLineAtOffset(50, 740);
      for (var line : lines) {
        content.showText(line);
        content.newLineAtOffset(0, -18);
      }
      content.endText();
    }
  }
}
