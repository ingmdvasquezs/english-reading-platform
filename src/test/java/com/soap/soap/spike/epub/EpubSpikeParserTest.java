package com.soap.soap.spike.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adobe.epubcheck.api.EpubCheck;
import com.soap.soap.spike.epub.EpubSpikeModel.Block;
import com.soap.soap.spike.epub.EpubSpikeModel.Status;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpubSpikeParserTest {

  @TempDir Path temporaryDirectory;

  private final EpubSpikeParser parser = new EpubSpikeParser();

  @Test
  void parsesValidEpub3MetadataCoverSpineParagraphsAndUnicode() throws Exception {
    var epub = EpubFixtureFactory.epub3(temporaryDirectory.resolve("book.epub"), true, true);

    var parsed = parser.parse(epub);

    assertThat(parsed.status()).isEqualTo(Status.SUPPORTED);
    assertThat(parsed.metadata().title()).isEqualTo("EPUB Three");
    assertThat(parsed.metadata().author()).isEqualTo("Ada Author");
    assertThat(parsed.metadata().language()).isEqualTo("en-US");
    assertThat(parsed.metadata().packagePath()).isEqualTo("OPS/package.opf");
    assertThat(parsed.cover()).isNotNull();
    assertThat(parsed.cover().mediaType()).isEqualTo("image/png");
    assertThat(parsed.cover().bytes()).isNotEmpty();
    assertThat(parsed.sections())
        .extracting(section -> section.id())
        .containsExactly("first", "second");
    assertThat(parsed.sections().getFirst().blocks())
        .extracting(Block::text)
        .containsExactly("First", "Café — naïve readers.", "Second paragraph.");
    assertThat(parsed.sections().get(1).blocks())
        .extracting(Block::type)
        .containsExactly(Block.Type.HEADING, Block.Type.BLOCKQUOTE, Block.Type.LIST_ITEM);
  }

  @Test
  void parsesValidEpub2AndItsLegacyCoverDeclaration() throws Exception {
    var epub = EpubFixtureFactory.epub2(temporaryDirectory.resolve("legacy.epub"));

    var parsed = parser.parse(epub);

    assertThat(parsed.status()).isEqualTo(Status.SUPPORTED);
    assertThat(parsed.metadata().title()).isEqualTo("EPUB Two");
    assertThat(parsed.metadata().author()).isEqualTo("Grace Writer");
    assertThat(parsed.metadata().language()).isEqualTo("en");
    assertThat(parsed.cover()).isNotNull();
    assertThat(parsed.sections()).extracting(section -> section.id()).containsExactly("one");
  }

  @Test
  void officialValidatorAcceptsTheSelfContainedEpub2AndEpub3Corpus() throws Exception {
    var epub2 = EpubFixtureFactory.epub2(temporaryDirectory.resolve("valid-2.epub"));
    var epub3 = EpubFixtureFactory.epub3(temporaryDirectory.resolve("valid-3.epub"), true, true);

    assertThat(new EpubCheck(epub2.toFile()).doValidate()).isZero();
    assertThat(new EpubCheck(epub3.toFile()).doValidate()).isZero();
  }

  @Test
  void reportsMissingLanguageAndCoverInsteadOfInventingDefaults() throws Exception {
    var epub = EpubFixtureFactory.epub3(temporaryDirectory.resolve("minimal.epub"), false, false);

    var parsed = parser.parse(epub);

    assertThat(parsed.metadata().language()).isNull();
    assertThat(parsed.cover()).isNull();
  }

  @Test
  void rejectsFakeEpubAndCorruptArchive() throws Exception {
    var fake = temporaryDirectory.resolve("fake.epub");
    java.nio.file.Files.writeString(fake, "not a ZIP");
    var wrongMime =
        EpubFixtureFactory.rawZip(
            temporaryDirectory.resolve("wrong.epub"),
            Map.of("mimetype", "text/plain".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> parser.parse(fake)).hasMessageContaining("INVALID_EPUB");
    assertThatThrownBy(() -> parser.parse(wrongMime)).hasMessageContaining("Invalid EPUB mimetype");
  }

  @Test
  void rejectsZipSlipAndAbsolutePathsBeforeParsingContent() throws Exception {
    var traversal =
        EpubFixtureFactory.rawZip(
            temporaryDirectory.resolve("traversal.epub"),
            Map.of(
                "mimetype",
                "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "../payload",
                new byte[] {1}));
    var absolute =
        EpubFixtureFactory.rawZip(
            temporaryDirectory.resolve("absolute.epub"),
            Map.of(
                "mimetype",
                "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "C:/payload",
                new byte[] {1}));

    assertThatThrownBy(() -> parser.parse(traversal)).hasMessageContaining("path traversal");
    assertThatThrownBy(() -> parser.parse(absolute)).hasMessageContaining("absolute path");
  }

  @Test
  void rejectsControlledCompressionBombByExpansionRatio() throws Exception {
    var compressed = new byte[40_000];
    var epub =
        EpubFixtureFactory.rawZip(
            temporaryDirectory.resolve("ratio.epub"),
            Map.of(
                "mimetype",
                "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "bomb.txt",
                compressed));
    var strict =
        new EpubSpikeParser(new EpubSpikeLimits(1_000_000, 20, 100_000, 200_000, 10, 1_000));

    assertThatThrownBy(() -> strict.parse(epub)).hasMessageContaining("compression ratio");
  }

  @Test
  void rejectsDoctypeAndExternalEntityWithoutResolvingIt() throws Exception {
    var maliciousContainer =
        "<?xml version=\"1.0\"?><!DOCTYPE container [<!ENTITY xxe SYSTEM \"file:///secret\">]>"
            + "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles>"
            + "<rootfile full-path=\"&xxe;\"/></rootfiles></container>";
    var epub =
        EpubFixtureFactory.rawZip(
            temporaryDirectory.resolve("xxe.epub"),
            Map.of(
                "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "META-INF/container.xml", maliciousContainer.getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> parser.parse(epub)).hasMessageContaining("Unsafe or invalid XML");
  }

  @Test
  void stripsExecutableAndRemoteXhtmlWhilePreservingSemanticBlocks() throws Exception {
    var body =
        "<h1 onclick=\"attack()\">Safe heading</h1><p>One <a href=\"https://evil.invalid\">link</a>.</p>"
            + "<script>attack()</script><iframe src=\"https://evil.invalid\"></iframe><form><input/></form>"
            + "<p><img src=\"https://evil.invalid/pixel\" onerror=\"attack()\"/>Final text.</p>";
    var epub =
        EpubFixtureFactory.custom(
            temporaryDirectory.resolve("hostile.epub"),
            EpubFixtureFactory.simpleOpf("chapter.xhtml"),
            Map.of(
                "OPS/chapter.xhtml",
                EpubFixtureFactory.xhtml("Hostile", body).getBytes(StandardCharsets.UTF_8)));

    var blocks = parser.parse(epub).sections().getFirst().blocks();

    assertThat(blocks)
        .extracting(Block::text)
        .containsExactly("Safe heading", "One link.", "Final text.");
    assertThat(blocks).extracting(Block::text).allMatch(text -> !text.contains("attack"));
  }

  @Test
  void reportsEncryptedSpineAsUnsupportedDrmWithoutReadingItsXhtml() throws Exception {
    var encryption =
        "<?xml version=\"1.0\"?><encryption xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" "
            + "xmlns:enc=\"http://www.w3.org/2001/04/xmlenc#\"><enc:EncryptedData><enc:CipherData>"
            + "<enc:CipherReference URI=\"OPS/chapter.xhtml\"/></enc:CipherData></enc:EncryptedData></encryption>";
    var epub =
        EpubFixtureFactory.custom(
            temporaryDirectory.resolve("drm.epub"),
            EpubFixtureFactory.simpleOpf("chapter.xhtml"),
            Map.of(
                "OPS/chapter.xhtml",
                new byte[] {0, 1, 2},
                "META-INF/encryption.xml",
                encryption.getBytes(StandardCharsets.UTF_8)));

    assertThat(parser.parse(epub).status()).isEqualTo(Status.UNSUPPORTED_DRM);
  }

  @Test
  void detectsTheZipEncryptionFlagBeforeOpeningEntries() throws Exception {
    var epub =
        EpubFixtureFactory.epub3(temporaryDirectory.resolve("encrypted-zip.epub"), false, true);
    var bytes = java.nio.file.Files.readAllBytes(epub);
    for (int index = 0; index < bytes.length - 10; index++) {
      if (bytes[index] == 'P'
          && bytes[index + 1] == 'K'
          && ((bytes[index + 2] == 1 && bytes[index + 3] == 2)
              || (bytes[index + 2] == 3 && bytes[index + 3] == 4))) {
        var flagOffset = bytes[index + 2] == 1 ? 8 : 6;
        bytes[index + flagOffset] |= 1;
      }
    }
    java.nio.file.Files.write(epub, bytes);

    assertThat(parser.parse(epub).status()).isEqualTo(Status.UNSUPPORTED_DRM);
  }

  @Test
  void enforcesEntryCountIndividualExpandedSizeAndTotalExpandedSize() throws Exception {
    var many =
        EpubFixtureFactory.rawZip(
            temporaryDirectory.resolve("many.epub"),
            Map.of(
                "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "one", new byte[2],
                "two", new byte[2]));
    var large =
        EpubFixtureFactory.rawZip(
            temporaryDirectory.resolve("large-entry.epub"),
            Map.of(
                "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "large", randomBytes(200)));
    var total =
        EpubFixtureFactory.rawZip(
            temporaryDirectory.resolve("large-total.epub"),
            Map.of(
                "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII),
                "one", randomBytes(80),
                "two", randomBytes(80)));

    assertThatThrownBy(
            () ->
                new EpubSpikeParser(new EpubSpikeLimits(10_000, 2, 1_000, 2_000, 1_000, 1_000))
                    .parse(many))
        .hasMessageContaining("too many entries");
    assertThatThrownBy(
            () ->
                new EpubSpikeParser(new EpubSpikeLimits(10_000, 20, 100, 2_000, 1_000, 1_000))
                    .parse(large))
        .hasMessageContaining("Entry exceeds expanded");
    assertThatThrownBy(
            () ->
                new EpubSpikeParser(new EpubSpikeLimits(10_000, 20, 100, 150, 1_000, 1_000))
                    .parse(total))
        .hasMessageContaining("Archive exceeds expanded");
  }

  @Test
  void rejectsExternalManifestResourcesAndInvalidCoverBytes() throws Exception {
    var external =
        EpubFixtureFactory.custom(
            temporaryDirectory.resolve("remote.epub"),
            EpubFixtureFactory.simpleOpf("https://evil.invalid/chapter.xhtml"),
            Map.of());
    var coverOpf =
        EpubFixtureFactory.simpleOpf("chapter.xhtml")
            .replace(
                "</manifest>",
                "<item id=\"cover\" href=\"cover.png\" media-type=\"image/png\" properties=\"cover-image\"/></manifest>");
    var badCover =
        EpubFixtureFactory.custom(
            temporaryDirectory.resolve("bad-cover.epub"),
            coverOpf,
            Map.of(
                "OPS/chapter.xhtml",
                EpubFixtureFactory.xhtml("x", "<p>x</p>").getBytes(StandardCharsets.UTF_8),
                "OPS/cover.png",
                new byte[] {1, 2, 3}));

    assertThatThrownBy(() -> parser.parse(external)).hasMessageContaining("External manifest");
    assertThatThrownBy(() -> parser.parse(badCover)).hasMessageContaining("Cover bytes");
  }

  @Test
  void chunkingIsDeterministicKeepsShortChapterAndSplitsLongAndGiantParagraphs() {
    var chunker = new PrototypeChunker();
    var shortChapter = List.of(new Block(Block.Type.PARAGRAPH, words(80, "short")));
    var longChapter =
        java.util.stream.IntStream.range(0, 24)
            .mapToObj(index -> new Block(Block.Type.PARAGRAPH, words(100, "p" + index)))
            .toList();
    var giant = List.of(new Block(Block.Type.PARAGRAPH, sentences(1_500)));

    var first = chunker.chunk(longChapter, Locale.ENGLISH);
    var second = chunker.chunk(longChapter, Locale.ENGLISH);

    assertThat(chunker.chunk(shortChapter, Locale.ENGLISH)).hasSize(1);
    assertThat(first).isEqualTo(second).hasSizeGreaterThan(1);
    assertThat(first)
        .allMatch(chunk -> chunk.wordCount() <= 1_200 && chunk.utf8Bytes() <= 80 * 1024);
    assertThat(chunker.chunk(giant, Locale.ENGLISH))
        .hasSizeGreaterThan(1)
        .allMatch(chunk -> chunk.wordCount() <= 1_200 && chunk.utf8Bytes() <= 80 * 1024);
  }

  private String words(int count, String prefix) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(index -> prefix + index)
        .collect(java.util.stream.Collectors.joining(" "));
  }

  private String sentences(int words) {
    return java.util.stream.IntStream.range(0, words / 10)
        .mapToObj(index -> words(10, "sentence" + index) + ".")
        .collect(java.util.stream.Collectors.joining(" "));
  }

  private byte[] randomBytes(int count) {
    var bytes = new byte[count];
    new java.util.Random(7).nextBytes(bytes);
    return bytes;
  }
}
