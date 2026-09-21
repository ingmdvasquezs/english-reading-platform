package com.soap.soap.infrastructure.document;

import com.adobe.epubcheck.api.EpubCheck;
import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.application.model.ParsedDocumentCover;
import com.soap.soap.application.model.ParsedDocumentSection;
import com.soap.soap.application.port.out.DocumentParserPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.service.DocumentSectionTitleSanitizer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipMethod;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

@Component
@RequiredArgsConstructor
public class EpubDocumentParserAdapter implements DocumentParserPort {
  private static final String CONTAINER = "META-INF/container.xml";
  private static final String ENCRYPTION = "META-INF/encryption.xml";
  private static final Set<String> BLOCK_TAGS =
      Set.of("h1", "h2", "h3", "h4", "h5", "h6", "p", "blockquote", "li");

  private final DocumentImportLimits limits;

  @Override
  public ParsedDocument parse(Path source, DocumentFormat format) {
    if (format != DocumentFormat.EPUB) {
      throw new DocumentImportException(Reason.INVALID_EPUB, "Unsupported document format");
    }
    try {
      validateSource(source);
      try (var zip = ZipFile.builder().setPath(source).get()) {
        validateEntries(zip);
        validateMimetype(zip);
        var packagePath = packagePath(readRequired(zip, CONTAINER));
        var opf = parseXml(readRequired(zip, packagePath));
        var manifest = manifest(opf, packagePath);
        var spine = spine(opf);
        if (spine.isEmpty()) throw invalid("OPF spine is empty");
        rejectDrm(zip, manifest, spine);
        var sections = sections(zip, manifest, spine);
        if (sections.isEmpty()) throw invalid("Spine contains no XHTML content");
        validateOfficially(source);
        return new ParsedDocument(
            firstText(opf, "title").orElse("Untitled document"),
            firstText(opf, "creator").orElse(null),
            firstText(opf, "language").map(this::normalizeLanguage).orElse(null),
            sections,
            extractCover(zip, opf, manifest).orElse(null));
      }
    } catch (DocumentImportException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw new DocumentImportException(Reason.INVALID_EPUB, "Invalid EPUB", exception);
    }
  }

  private void validateSource(Path source) throws IOException {
    if (source == null || !Files.isRegularFile(source)) throw invalid("Source is not a file");
    if (Files.size(source) > limits.maxSourceBytes()) {
      throw new DocumentImportException(Reason.FILE_TOO_LARGE, "EPUB exceeds source byte limit");
    }
  }

  private void validateEntries(ZipFile zip) {
    var names = new HashSet<String>();
    long expanded = 0;
    int count = 0;
    var entries = zip.getEntries();
    while (entries.hasMoreElements()) {
      var entry = entries.nextElement();
      count++;
      if (count > limits.maxZipEntries()) throw security("EPUB has too many ZIP entries");
      var name = safeName(entry.getName());
      if (!names.add(name.toLowerCase(Locale.ROOT))) throw security("Duplicate ZIP entry");
      if (entry.getGeneralPurposeBit().usesEncryption()) {
        throw new DocumentImportException(Reason.UNSUPPORTED_DRM, "Encrypted EPUB is unsupported");
      }
      var size = entry.getSize();
      var compressed = entry.getCompressedSize();
      if (size < 0 || compressed < 0) throw security("ZIP entry has unknown size");
      if (size > limits.maxEntryBytes()) throw security("ZIP entry exceeds byte limit");
      try {
        expanded = Math.addExact(expanded, size);
      } catch (ArithmeticException exception) {
        throw security("EPUB expanded size overflow");
      }
      if (expanded > limits.maxExpandedBytes()) throw security("EPUB expanded size exceeds limit");
      if (size > 0 && compressed == 0) throw security("Invalid ZIP compression metadata");
      if (compressed > 0 && (double) size / compressed > limits.maxCompressionRatio()) {
        throw security("ZIP compression ratio exceeds limit");
      }
    }
  }

  private void validateMimetype(ZipFile zip) throws IOException {
    var physicalEntries = zip.getEntriesInPhysicalOrder();
    if (!physicalEntries.hasMoreElements()
        || !"mimetype".equals(physicalEntries.nextElement().getName())) {
      throw invalid("EPUB mimetype must be the first ZIP entry");
    }
    var entry = zip.getEntry("mimetype");
    if (entry == null || entry.getMethod() != ZipMethod.STORED.getCode()) {
      throw invalid("EPUB mimetype must be stored without compression");
    }
    var value = new String(readLimited(zip, entry), StandardCharsets.US_ASCII);
    if (!"application/epub+zip".equals(value)) throw invalid("Invalid EPUB mimetype");
  }

  private String packagePath(byte[] bytes) {
    var document = parseXml(bytes);
    var rootfiles = document.getElementsByTagNameNS("*", "rootfile");
    if (rootfiles.getLength() == 0) throw invalid("Container has no rootfile");
    return safeName(((org.w3c.dom.Element) rootfiles.item(0)).getAttribute("full-path"));
  }

  private Document parseXml(byte[] bytes) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
      factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
      return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    } catch (Exception exception) {
      throw invalid("Unsafe or invalid XML", exception);
    }
  }

  private Map<String, ManifestItem> manifest(Document opf, String packagePath) {
    var base = parent(packagePath);
    var result = new HashMap<String, ManifestItem>();
    var items = opf.getElementsByTagNameNS("*", "item");
    for (int index = 0; index < items.getLength(); index++) {
      var element = (org.w3c.dom.Element) items.item(index);
      var id = element.getAttribute("id");
      var href = element.getAttribute("href");
      if (id.isBlank() || href.isBlank()) throw invalid("Manifest item lacks id or href");
      if (external(href)) throw security("External manifest resource is forbidden");
      if (result.put(
              id,
              new ManifestItem(
                  resolve(base, href),
                  element.getAttribute("media-type"),
                  element.getAttribute("properties")))
          != null) throw invalid("Duplicate manifest id");
    }
    return result;
  }

  private List<String> spine(Document opf) {
    var result = new ArrayList<String>();
    var items = opf.getElementsByTagNameNS("*", "itemref");
    for (int index = 0; index < items.getLength(); index++) {
      var id = ((org.w3c.dom.Element) items.item(index)).getAttribute("idref");
      if (!id.isBlank()) result.add(id);
    }
    return result;
  }

  private void rejectDrm(ZipFile zip, Map<String, ManifestItem> manifest, List<String> spine)
      throws IOException {
    var entry = zip.getEntry(ENCRYPTION);
    if (entry == null) return;
    var encryption = parseXml(readLimited(zip, entry));
    var encrypted = new HashSet<String>();
    var references = encryption.getElementsByTagNameNS("*", "CipherReference");
    for (int index = 0; index < references.getLength(); index++) {
      var uri = ((org.w3c.dom.Element) references.item(index)).getAttribute("URI");
      if (!uri.isBlank()) encrypted.add(resolve("", uri));
    }
    if (spine.stream()
        .map(manifest::get)
        .filter(java.util.Objects::nonNull)
        .anyMatch(item -> encrypted.contains(item.path()))) {
      throw new DocumentImportException(
          Reason.UNSUPPORTED_DRM, "DRM-protected EPUB is unsupported");
    }
  }

  private List<ParsedDocumentSection> sections(
      ZipFile zip, Map<String, ManifestItem> manifest, List<String> spine) throws IOException {
    var result = new ArrayList<ParsedDocumentSection>();
    for (var id : spine) {
      var item = manifest.get(id);
      if (item == null) throw invalid("Spine references a missing manifest item");
      if (!isXhtml(item.mediaType())) continue;
      var content = xhtml(readRequired(zip, item.path()));
      if (!content.blocks().isEmpty()) {
        result.add(new ParsedDocumentSection(content.title(), item.path(), content.blocks()));
      }
    }
    return result;
  }

  private ParsedXhtml xhtml(byte[] xhtml) {
    var document = Jsoup.parse(new String(xhtml, StandardCharsets.UTF_8), "", Parser.xmlParser());
    document
        .select("script,style,iframe,frame,object,embed,form,input,button,video,audio,svg")
        .remove();
    document.select("*").forEach(this::removeUnsafeAttributes);
    var result = new ArrayList<String>();
    for (var element : document.getAllElements()) {
      if (!BLOCK_TAGS.contains(element.normalName()) || hasBlockAncestor(element)) continue;
      var text = element.text().strip();
      if (!text.isEmpty()) result.add(text);
    }
    var heading = document.selectFirst("h1,h2,h3,h4,h5,h6");
    var headTitle = document.selectFirst("head > title");
    var headingCandidate =
        heading != null ? DocumentSectionTitleSanitizer.sanitize(textOrNull(heading)) : null;
    var title =
        headingCandidate != null
            ? headingCandidate
            : DocumentSectionTitleSanitizer.sanitize(textOrNull(headTitle));
    return new ParsedXhtml(title, result);
  }

  private String textOrNull(Element element) {
    if (element == null) return null;
    var text = element.text().strip();
    return text.isEmpty() ? null : text;
  }

  private void removeUnsafeAttributes(Element element) {
    for (var attribute : new ArrayList<>(element.attributes().asList())) {
      var key = attribute.getKey().toLowerCase(Locale.ROOT);
      var value = attribute.getValue().strip();
      if (key.startsWith("on")
          || ((key.equals("href") || key.equals("src") || key.equals("xlink:href"))
              && (external(value) || value.toLowerCase(Locale.ROOT).startsWith("javascript:")))) {
        element.removeAttr(attribute.getKey());
      }
    }
  }

  private Optional<ParsedDocumentCover> extractCover(
      ZipFile zip, Document opf, Map<String, ManifestItem> manifest) throws IOException {
    var cover =
        manifest.values().stream()
            .filter(item -> tokens(item.properties()).contains("cover-image"))
            .findFirst()
            .orElse(null);
    if (cover == null) {
      var metas = opf.getElementsByTagNameNS("*", "meta");
      for (int index = 0; index < metas.getLength(); index++) {
        var meta = (org.w3c.dom.Element) metas.item(index);
        if ("cover".equalsIgnoreCase(meta.getAttribute("name"))) {
          cover = manifest.get(meta.getAttribute("content"));
          break;
        }
      }
    }
    if (cover == null) return Optional.empty();
    try {
      if (!Set.of("image/jpeg", "image/png", "image/webp")
          .contains(cover.mediaType().toLowerCase(Locale.ROOT))) {
        throw invalid("Cover media type is unsupported");
      }
      var bytes = readRequired(zip, cover.path());
      if (bytes.length == 0) throw invalid("Cover is empty");
      if (bytes.length > limits.maxCoverBytes()) throw security("Cover exceeds byte limit");
      validateCoverBytes(cover.mediaType(), bytes);
      return Optional.of(new ParsedDocumentCover(cover.mediaType(), bytes));
    } catch (DocumentImportException exception) {
      if (exception.reason() == Reason.INVALID_EPUB) return Optional.empty();
      throw exception;
    }
  }

  private void validateCoverBytes(String mediaType, byte[] bytes) {
    if ("image/webp".equalsIgnoreCase(mediaType)) {
      if (bytes.length < 30 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WEBP"))
        throw invalid("Cover bytes are invalid");
      var dimensions = webpDimensions(bytes);
      validateCoverPixels(dimensions[0], dimensions[1]);
      return;
    }
    try (var input = new ByteArrayInputStream(bytes)) {
      var image = ImageIO.read(input);
      if (image == null) throw invalid("Cover bytes are invalid");
      var expected = "image/png".equalsIgnoreCase(mediaType) ? "png" : "jpeg";
      try (var formatInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(formatInput);
        if (!readers.hasNext()
            || !readers.next().getFormatName().toLowerCase(Locale.ROOT).contains(expected)) {
          throw invalid("Cover media type does not match its content");
        }
      }
      validateCoverPixels(image.getWidth(), image.getHeight());
    } catch (IOException exception) {
      throw invalid("Cover bytes are invalid", exception);
    }
  }

  private int[] webpDimensions(byte[] bytes) {
    var chunk = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
    if ("VP8X".equals(chunk)) {
      return new int[] {1 + littleEndian24(bytes, 24), 1 + littleEndian24(bytes, 27)};
    }
    if ("VP8L".equals(chunk) && bytes[20] == 0x2f) {
      int bits = littleEndian32(bytes, 21);
      return new int[] {1 + (bits & 0x3fff), 1 + ((bits >> 14) & 0x3fff)};
    }
    if ("VP8 ".equals(chunk)
        && bytes.length >= 30
        && (bytes[23] & 0xff) == 0x9d
        && (bytes[24] & 0xff) == 0x01
        && (bytes[25] & 0xff) == 0x2a) {
      return new int[] {littleEndian16(bytes, 26) & 0x3fff, littleEndian16(bytes, 28) & 0x3fff};
    }
    throw invalid("Cover WebP header is invalid");
  }

  private void validateCoverPixels(int width, int height) {
    if (width <= 0 || height <= 0 || (long) width * height > limits.maxCoverPixels()) {
      throw security("Cover dimensions exceed limit");
    }
  }

  private boolean ascii(byte[] bytes, int offset, String value) {
    if (offset + value.length() > bytes.length) return false;
    for (int index = 0; index < value.length(); index++) {
      if ((char) bytes[offset + index] != value.charAt(index)) return false;
    }
    return true;
  }

  private int littleEndian16(byte[] bytes, int offset) {
    return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
  }

  private int littleEndian24(byte[] bytes, int offset) {
    return littleEndian16(bytes, offset) | ((bytes[offset + 2] & 0xff) << 16);
  }

  private int littleEndian32(byte[] bytes, int offset) {
    return littleEndian24(bytes, offset) | ((bytes[offset + 3] & 0xff) << 24);
  }

  private void validateOfficially(Path source) {
    // EPUBCheck is an additional standards diagnostic. Some safely recoverable inputs (for
    // example a missing language later supplied by the caller, or hostile markup that we strip)
    // are intentionally accepted after the security and structural checks above.
    new EpubCheck(source.toFile()).doValidate();
  }

  private byte[] readRequired(ZipFile zip, String name) throws IOException {
    var entry = zip.getEntry(safeName(name));
    if (entry == null || entry.isDirectory()) throw invalid("Missing EPUB entry");
    return readLimited(zip, entry);
  }

  private byte[] readLimited(ZipFile zip, ZipArchiveEntry entry) throws IOException {
    try (InputStream input = zip.getInputStream(entry)) {
      var bytes = input.readNBytes(Math.toIntExact(limits.maxEntryBytes()) + 1);
      if (bytes.length > limits.maxEntryBytes()) throw security("ZIP entry exceeds byte limit");
      return bytes;
    }
  }

  private Optional<String> firstText(Document document, String name) {
    var elements = document.getElementsByTagNameNS("*", name);
    if (elements.getLength() == 0) return Optional.empty();
    var value = elements.item(0).getTextContent().strip();
    return value.isEmpty() ? Optional.empty() : Optional.of(value);
  }

  private String normalizeLanguage(String raw) {
    var locale = Locale.forLanguageTag(raw.strip().replace('_', '-'));
    return locale.getLanguage().isBlank() || "und".equals(locale.toLanguageTag())
        ? null
        : locale.toLanguageTag();
  }

  private String safeName(String raw) {
    var name = raw.replace('\\', '/');
    if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*")) throw security("Absolute ZIP path");
    var normalized = Paths.get(name).normalize().toString().replace('\\', '/');
    if (normalized.equals("..") || normalized.startsWith("../"))
      throw security("ZIP path traversal");
    return normalized;
  }

  private String resolve(String base, String href) {
    return safeName(Paths.get(base).resolve(href.split("#", 2)[0]).normalize().toString());
  }

  private boolean external(String value) {
    try {
      return URI.create(value).isAbsolute() || value.startsWith("//");
    } catch (IllegalArgumentException exception) {
      throw security("Invalid resource URI");
    }
  }

  private boolean hasBlockAncestor(Element element) {
    for (var parent = element.parent(); parent != null; parent = parent.parent()) {
      if (BLOCK_TAGS.contains(parent.normalName())) return true;
    }
    return false;
  }

  private boolean isXhtml(String mediaType) {
    return "application/xhtml+xml".equalsIgnoreCase(mediaType)
        || "text/html".equalsIgnoreCase(mediaType);
  }

  private String parent(String path) {
    var slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash + 1);
  }

  private List<String> tokens(String value) {
    return value == null || value.isBlank() ? List.of() : List.of(value.strip().split("\\s+"));
  }

  private DocumentImportException invalid(String message) {
    return new DocumentImportException(Reason.INVALID_EPUB, message);
  }

  private DocumentImportException invalid(String message, Throwable cause) {
    return new DocumentImportException(Reason.INVALID_EPUB, message, cause);
  }

  private DocumentImportException security(String message) {
    return new DocumentImportException(Reason.SECURITY_LIMIT_EXCEEDED, message);
  }

  private record ManifestItem(String path, String mediaType, String properties) {}

  private record ParsedXhtml(String title, List<String> blocks) {}
}
