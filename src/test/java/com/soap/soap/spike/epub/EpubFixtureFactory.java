package com.soap.soap.spike.epub;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

final class EpubFixtureFactory {

  private EpubFixtureFactory() {}

  static Path epub3(Path path, boolean cover, boolean language) throws IOException {
    var entries = baseEntries(epub3Opf(cover, language));
    entries.put(
        "OPS/nav.xhtml",
        bytes(
            xhtml(
                "Contents",
                "<nav epub:type=\"toc\"><ol><li><a href=\"chapter-1.xhtml\">First</a></li>"
                    + "<li><a href=\"chapter-2.xhtml\">Second</a></li></ol></nav>")));
    entries.put(
        "OPS/chapter-1.xhtml",
        bytes(
            xhtml("First", "<h1>First</h1><p>Café — naïve readers.</p><p>Second paragraph.</p>")));
    entries.put(
        "OPS/chapter-2.xhtml",
        bytes(
            xhtml(
                "Second",
                "<h2>Second</h2><blockquote>Quoted text.</blockquote><ul><li>Item one</li></ul>")));
    if (cover) entries.put("OPS/cover.png", png(8, 6));
    return write(path, entries);
  }

  static Path epub2(Path path) throws IOException {
    var entries = baseEntries(epub2Opf());
    entries.put(
        "OPS/toc.ncx",
        bytes(
            "<?xml version=\"1.0\"?><ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">"
                + "<head><meta name=\"dtb:uid\" content=\"urn:test:epub2\"/></head><docTitle><text>EPUB Two</text></docTitle>"
                + "<navMap><navPoint id=\"n1\" playOrder=\"1\"><navLabel><text>One</text></navLabel>"
                + "<content src=\"one.xhtml\"/></navPoint></navMap></ncx>"));
    entries.put("OPS/one.xhtml", bytes(xhtml("One", "<h1>One</h1><p>EPUB two paragraph.</p>")));
    entries.put("OPS/cover.png", png(5, 5));
    return write(path, entries);
  }

  static Path custom(Path path, String opf, Map<String, byte[]> additional) throws IOException {
    var entries = baseEntries(opf);
    entries.putAll(additional);
    return write(path, entries);
  }

  static Path rawZip(Path path, Map<String, byte[]> entries) throws IOException {
    return write(path, entries);
  }

  static String xhtml(String title, String body) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
        + "<head><title>"
        + title
        + "</title></head><body>"
        + body
        + "</body></html>";
  }

  static String simpleOpf(String chapterHref) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\">"
        + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">urn:test</dc:identifier>"
        + "<dc:title>Test</dc:title><dc:language>en</dc:language><meta property=\"dcterms:modified\">2026-01-01T00:00:00Z</meta></metadata>"
        + "<manifest><item id=\"c\" href=\""
        + chapterHref
        + "\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"c\"/></spine></package>";
  }

  private static LinkedHashMap<String, byte[]> baseEntries(String opf) {
    var entries = new LinkedHashMap<String, byte[]>();
    entries.put("mimetype", bytes("application/epub+zip"));
    entries.put(
        "META-INF/container.xml",
        bytes(
            "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"OPS/package.opf\" media-type=\"application/oebps-package+xml\"/>"
                + "</rootfiles></container>"));
    entries.put("OPS/package.opf", bytes(opf));
    return entries;
  }

  private static String epub3Opf(boolean cover, boolean language) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\">"
        + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"bookid\">urn:test:epub3</dc:identifier>"
        + "<dc:title>EPUB Three</dc:title><dc:creator>Ada Author</dc:creator>"
        + (language ? "<dc:language>en-US</dc:language>" : "")
        + "<meta property=\"dcterms:modified\">2026-01-01T00:00:00Z</meta></metadata><manifest>"
        + "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
        + "<item id=\"second\" href=\"chapter-2.xhtml\" media-type=\"application/xhtml+xml\"/>"
        + "<item id=\"first\" href=\"chapter-1.xhtml\" media-type=\"application/xhtml+xml\"/>"
        + (cover
            ? "<item id=\"cover\" href=\"cover.png\" media-type=\"image/png\" properties=\"cover-image\"/>"
            : "")
        + "</manifest><spine><itemref idref=\"first\"/><itemref idref=\"second\"/></spine></package>";
  }

  private static String epub2Opf() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"id\">"
        + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">urn:test:epub2</dc:identifier>"
        + "<dc:title>EPUB Two</dc:title><dc:creator>Grace Writer</dc:creator><dc:language>en</dc:language>"
        + "<meta name=\"cover\" content=\"cover\"/></metadata><manifest>"
        + "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>"
        + "<item id=\"one\" href=\"one.xhtml\" media-type=\"application/xhtml+xml\"/>"
        + "<item id=\"cover\" href=\"cover.png\" media-type=\"image/png\"/></manifest>"
        + "<spine toc=\"ncx\"><itemref idref=\"one\"/></spine></package>";
  }

  static byte[] png(int width, int height) throws IOException {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.BLUE);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  static byte[] jpeg(int width, int height) throws IOException {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpeg", output);
    return output.toByteArray();
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static Path write(Path path, Map<String, byte[]> entries) throws IOException {
    Files.createDirectories(path.getParent());
    try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
      for (var item : entries.entrySet()) {
        var entry = new ZipEntry(item.getKey());
        if ("mimetype".equals(item.getKey())) {
          var crc = new CRC32();
          crc.update(item.getValue());
          entry.setMethod(ZipEntry.STORED);
          entry.setSize(item.getValue().length);
          entry.setCompressedSize(item.getValue().length);
          entry.setCrc(crc.getValue());
        }
        output.putNextEntry(entry);
        output.write(item.getValue());
        output.closeEntry();
      }
    }
    return path;
  }
}
