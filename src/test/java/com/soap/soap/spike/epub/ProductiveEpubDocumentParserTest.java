package com.soap.soap.spike.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.infrastructure.document.EpubDocumentParserAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductiveEpubDocumentParserTest {
  @TempDir Path directory;

  private final EpubDocumentParserAdapter parser =
      new EpubDocumentParserAdapter(
          new DocumentImportLimits(
              50L << 20, 2_000, 10L << 20, 150L << 20, 100, 10L << 20, 25_000_000));

  @Test
  void parsesEpub2AndEpub3UsingSpineOrderMetadataUnicodeAndCovers() throws Exception {
    var epub2 =
        parser.parse(EpubFixtureFactory.epub2(directory.resolve("two.epub")), DocumentFormat.EPUB);
    var epub3 =
        parser.parse(
            EpubFixtureFactory.epub3(directory.resolve("three.epub"), true, true),
            DocumentFormat.EPUB);

    assertThat(epub2.title()).isEqualTo("EPUB Two");
    assertThat(epub2.author()).isEqualTo("Grace Writer");
    assertThat(epub2.cover()).isNotNull();
    assertThat(epub3.declaredLanguage()).isEqualTo("en-US");
    assertThat(epub3.sections())
        .extracting(section -> section.title())
        .containsExactly("First", "Second");
    assertThat(epub3.sections().getFirst().blocks()).contains("Café — naïve readers.");
    assertThat(epub3.cover().mediaType()).isEqualTo("image/png");
  }

  @Test
  void usesEditorialHeadingInsteadOfTechnicalManifestIdAsSectionTitle() throws Exception {
    var source =
        EpubFixtureFactory.custom(
            directory.resolve("technical-id.epub"),
            EpubFixtureFactory.simpleOpf("chapter.xhtml")
                .replace("id=\"c\"", "id=\"id-idp123456\"")
                .replace("idref=\"c\"", "idref=\"id-idp123456\""),
            Map.of(
                "OPS/chapter.xhtml",
                EpubFixtureFactory.xhtml(
                        "Fallback title",
                        "<section id=\"id-idp123456\"><h2>Scene II</h2><p>Readable text.</p></section>")
                    .getBytes(StandardCharsets.UTF_8)));

    var parsed = parser.parse(source, DocumentFormat.EPUB);

    assertThat(parsed.sections())
        .singleElement()
        .satisfies(
            section -> {
              assertThat(section.title()).isEqualTo("Scene II");
              assertThat(section.sourceLocator()).isEqualTo("OPS/chapter.xhtml");
              assertThat(section.blocks()).doesNotContain("id-idp123456");
              assertThat(String.join(" ", section.blocks())).doesNotContain("id-idp123456");
            });
  }

  @Test
  void preservesMissingLanguageAndCover() throws Exception {
    var parsed =
        parser.parse(
            EpubFixtureFactory.epub3(directory.resolve("minimal.epub"), false, false),
            DocumentFormat.EPUB);

    assertThat(parsed.declaredLanguage()).isNull();
    assertThat(parsed.cover()).isNull();
  }

  @Test
  void acceptsStandardJpegCoverAndPreservesItsMediaType() throws Exception {
    var opf =
        EpubFixtureFactory.simpleOpf("chapter.xhtml")
            .replace(
                "</manifest>",
                "<item id=\"cover\" href=\"images/front.jpeg\" media-type=\"image/jpeg\" properties=\"cover-image\"/></manifest>");
    var parsed =
        parser.parse(
            EpubFixtureFactory.custom(
                directory.resolve("jpeg.epub"),
                opf,
                Map.of(
                    "OPS/chapter.xhtml",
                        EpubFixtureFactory.xhtml("One", "<p>Text.</p>")
                            .getBytes(StandardCharsets.UTF_8),
                    "OPS/images/front.jpeg", EpubFixtureFactory.jpeg(7, 9))),
            DocumentFormat.EPUB);

    assertThat(parsed.cover().mediaType()).isEqualTo("image/jpeg");
    assertThat(parsed.cover().bytes()).isEqualTo(EpubFixtureFactory.jpeg(7, 9));
  }

  @Test
  void missingOrCorruptDeclaredCoverDoesNotRejectReadableEpub() throws Exception {
    var missingOpf =
        EpubFixtureFactory.simpleOpf("chapter.xhtml")
            .replace(
                "</manifest>",
                "<item id=\"cover\" href=\"missing.png\" media-type=\"image/png\" properties=\"cover-image\"/></manifest>");
    var corruptOpf = missingOpf.replace("missing.png", "corrupt.png");
    var chapter =
        Map.of(
            "OPS/chapter.xhtml",
            EpubFixtureFactory.xhtml("One", "<p>Readable.</p>").getBytes(StandardCharsets.UTF_8));
    var corruptEntries = new java.util.HashMap<>(chapter);
    corruptEntries.put("OPS/corrupt.png", "not an image".getBytes(StandardCharsets.UTF_8));

    var missing =
        parser.parse(
            EpubFixtureFactory.custom(directory.resolve("missing-cover.epub"), missingOpf, chapter),
            DocumentFormat.EPUB);
    var corrupt =
        parser.parse(
            EpubFixtureFactory.custom(
                directory.resolve("corrupt-cover.epub"), corruptOpf, corruptEntries),
            DocumentFormat.EPUB);

    assertThat(missing.cover()).isNull();
    assertThat(corrupt.cover()).isNull();
    assertThat(missing.sections()).isNotEmpty();
  }

  @Test
  void removesExecutableRemoteAndFormContentFromXhtml() throws Exception {
    var body =
        "<h1 onclick=\"x()\">Safe</h1><p>Visible text.</p><script>x()</script>"
            + "<iframe src=\"https://evil.invalid\"></iframe><form><input value=\"secret\"/></form>";
    var source =
        EpubFixtureFactory.custom(
            directory.resolve("hostile.epub"),
            EpubFixtureFactory.simpleOpf("chapter.xhtml"),
            Map.of(
                "OPS/chapter.xhtml",
                EpubFixtureFactory.xhtml("Safe", body).getBytes(StandardCharsets.UTF_8)));

    var parsed = parser.parse(source, DocumentFormat.EPUB);

    assertThat(parsed.sections().getFirst().blocks()).containsExactly("Safe", "Visible text.");
  }

  @Test
  void rejectsDrmXxeTraversalAndInvalidMimetypeWithDistinctReasons() throws Exception {
    var encryption =
        "<encryption xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" xmlns:enc=\"http://www.w3.org/2001/04/xmlenc#\">"
            + "<enc:EncryptedData><enc:CipherData><enc:CipherReference URI=\"OPS/chapter.xhtml\"/>"
            + "</enc:CipherData></enc:EncryptedData></encryption>";
    var drm =
        EpubFixtureFactory.custom(
            directory.resolve("drm.epub"),
            EpubFixtureFactory.simpleOpf("chapter.xhtml"),
            Map.of(
                "OPS/chapter.xhtml",
                new byte[] {1},
                "META-INF/encryption.xml",
                encryption.getBytes(StandardCharsets.UTF_8)));
    var traversal =
        EpubFixtureFactory.rawZip(
            directory.resolve("traversal.epub"),
            Map.of(
                "mimetype",
                "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "../payload",
                new byte[] {1}));
    var xxe =
        EpubFixtureFactory.rawZip(
            directory.resolve("xxe.epub"),
            Map.of(
                "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "META-INF/container.xml",
                    "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///secret'>]><x>&e;</x>"
                        .getBytes(StandardCharsets.UTF_8)));
    var fake =
        EpubFixtureFactory.rawZip(
            directory.resolve("fake.epub"),
            Map.of("mimetype", "text/plain".getBytes(StandardCharsets.US_ASCII)));

    assertReason(drm, Reason.UNSUPPORTED_DRM);
    assertReason(traversal, Reason.SECURITY_LIMIT_EXCEEDED);
    assertReason(xxe, Reason.INVALID_EPUB);
    assertReason(fake, Reason.INVALID_EPUB);
  }

  @Test
  void enforcesEntryCountCompressionAndSourceLimits() throws Exception {
    var source =
        EpubFixtureFactory.rawZip(
            directory.resolve("many.epub"),
            Map.of(
                "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "one", new byte[1],
                "two", new byte[1]));
    var strict =
        new EpubDocumentParserAdapter(
            new DocumentImportLimits(10_000, 2, 100, 1_000, 10, 100, 100));

    assertThatThrownBy(() -> strict.parse(source, DocumentFormat.EPUB))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> assertThat(exception.reason()).isEqualTo(Reason.SECURITY_LIMIT_EXCEEDED));
  }

  private void assertReason(Path source, Reason reason) {
    assertThatThrownBy(() -> parser.parse(source, DocumentFormat.EPUB))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> assertThat(exception.reason()).isEqualTo(reason));
  }
}
