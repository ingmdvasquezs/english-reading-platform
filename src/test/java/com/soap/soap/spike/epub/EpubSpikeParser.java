package com.soap.soap.spike.epub;

import com.soap.soap.spike.epub.EpubSpikeModel.Block;
import com.soap.soap.spike.epub.EpubSpikeModel.Cover;
import com.soap.soap.spike.epub.EpubSpikeModel.Metadata;
import com.soap.soap.spike.epub.EpubSpikeModel.Parsed;
import com.soap.soap.spike.epub.EpubSpikeModel.Section;
import com.soap.soap.spike.epub.EpubSpikeModel.Status;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.w3c.dom.Document;

final class EpubSpikeParser {

  private static final String CONTAINER = "META-INF/container.xml";
  private static final String ENCRYPTION = "META-INF/encryption.xml";
  private static final Set<String> BLOCK_TAGS =
      Set.of("h1", "h2", "h3", "h4", "h5", "h6", "p", "blockquote", "li");

  private final EpubSpikeLimits limits;

  EpubSpikeParser() {
    this(EpubSpikeLimits.defaults());
  }

  EpubSpikeParser(EpubSpikeLimits limits) {
    this.limits = limits;
  }

  Parsed parse(Path epub) {
    var started = Instant.now();
    try {
      validateArchiveSize(epub);
      if (hasZipEncryptionFlag(epub)) {
        return new Parsed(
            Status.UNSUPPORTED_DRM,
            null,
            null,
            List.of(),
            0,
            Duration.between(started, Instant.now()).toMillis());
      }
      try (var zip = new ZipFile(epub.toFile(), StandardCharsets.UTF_8)) {
        var entries = validateEntries(zip);
        validateMimetype(zip);
        var packagePath = packagePath(readRequired(zip, CONTAINER));
        var opf = parseXml(readRequired(zip, packagePath));
        var manifest = manifest(opf, packagePath);
        var spineIds = spine(opf);
        if (spineIds.isEmpty()) throw invalid("OPF spine is empty");
        if (hasEncryptedSpine(zip, packagePath, manifest, spineIds)) {
          return new Parsed(
              Status.UNSUPPORTED_DRM,
              metadata(opf, packagePath),
              null,
              List.of(),
              manifest.size(),
              Duration.between(started, Instant.now()).toMillis());
        }
        var sections = new ArrayList<Section>();
        for (var id : spineIds) {
          var item = manifest.get(id);
          if (item == null) throw invalid("Spine references missing manifest item: " + id);
          if (!isXhtml(item.mediaType())) continue;
          var blocks = extractBlocks(readRequired(zip, item.path()));
          sections.add(new Section(id, item.path(), blocks));
        }
        if (sections.isEmpty()) throw invalid("Spine contains no XHTML content");
        return new Parsed(
            Status.SUPPORTED,
            metadata(opf, packagePath),
            extractCover(zip, opf, manifest).orElse(null),
            sections,
            manifest.size(),
            Duration.between(started, Instant.now()).toMillis());
      }
    } catch (EpubSpikeException exception) {
      throw exception;
    } catch (ZipException exception) {
      throw new EpubSpikeException("INVALID_EPUB: corrupt ZIP", exception);
    } catch (Exception exception) {
      throw new EpubSpikeException("INVALID_EPUB: " + exception.getMessage(), exception);
    }
  }

  private boolean hasZipEncryptionFlag(Path archive) throws IOException {
    try (var input = new RandomAccessFile(archive.toFile(), "r")) {
      for (long offset = 0; offset <= input.length() - 10; offset++) {
        input.seek(offset);
        if (input.readUnsignedByte() != 'P' || input.readUnsignedByte() != 'K') continue;
        var third = input.readUnsignedByte();
        var fourth = input.readUnsignedByte();
        var flagOffset = third == 1 && fourth == 2 ? 8 : third == 3 && fourth == 4 ? 6 : -1;
        if (flagOffset < 0) continue;
        input.seek(offset + flagOffset);
        var flags = input.readUnsignedByte() | input.readUnsignedByte() << 8;
        if ((flags & 1) != 0) return true;
      }
      return false;
    }
  }

  private void validateArchiveSize(Path epub) throws IOException {
    if (!Files.isRegularFile(epub)) throw invalid("Input is not a regular file");
    if (Files.size(epub) > limits.maxArchiveBytes()) throw invalid("Archive exceeds byte limit");
  }

  private Map<String, ZipEntry> validateEntries(ZipFile zip) {
    var entries = new HashMap<String, ZipEntry>();
    var canonicalNames = new HashSet<String>();
    long expanded = 0;
    int count = 0;
    var iterator = zip.entries();
    while (iterator.hasMoreElements()) {
      var entry = iterator.nextElement();
      count++;
      if (count > limits.maxEntries()) throw invalid("Archive has too many entries");
      var name = safeEntryName(entry.getName());
      if (!canonicalNames.add(name.toLowerCase(Locale.ROOT))) {
        throw invalid("Archive has duplicate entry names");
      }
      var size = entry.getSize();
      var compressed = entry.getCompressedSize();
      if (size < 0 || compressed < 0) throw invalid("Entry has unknown size");
      if (size > limits.maxEntryBytes()) throw invalid("Entry exceeds expanded byte limit");
      expanded = Math.addExact(expanded, size);
      if (expanded > limits.maxExpandedBytes())
        throw invalid("Archive exceeds expanded byte limit");
      if (size > 0 && compressed == 0) throw invalid("Entry has invalid compression metadata");
      if (compressed > 0 && (double) size / compressed > limits.maxCompressionRatio()) {
        throw invalid("Entry exceeds compression ratio limit");
      }
      entries.put(name, entry);
    }
    return entries;
  }

  private String safeEntryName(String raw) {
    var name = raw.replace('\\', '/');
    if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*")) {
      throw invalid("Archive contains absolute path");
    }
    var normalized = Paths.get(name).normalize().toString().replace('\\', '/');
    if (normalized.equals("..") || normalized.startsWith("../")) {
      throw invalid("Archive contains path traversal");
    }
    return normalized;
  }

  private void validateMimetype(ZipFile zip) throws IOException {
    var entry = zip.getEntry("mimetype");
    if (entry == null) throw invalid("Missing EPUB mimetype entry");
    var value = new String(readLimited(zip, entry), StandardCharsets.US_ASCII);
    if (!"application/epub+zip".equals(value)) throw invalid("Invalid EPUB mimetype");
  }

  private byte[] readRequired(ZipFile zip, String name) throws IOException {
    var entry = zip.getEntry(safeEntryName(name));
    if (entry == null || entry.isDirectory()) throw invalid("Missing archive entry: " + name);
    return readLimited(zip, entry);
  }

  private byte[] readLimited(ZipFile zip, ZipEntry entry) throws IOException {
    try (InputStream input = zip.getInputStream(entry)) {
      return input.readNBytes(Math.toIntExact(limits.maxEntryBytes()) + 1);
    }
  }

  private String packagePath(byte[] containerBytes) {
    var document = parseXml(containerBytes);
    var rootfiles = document.getElementsByTagNameNS("*", "rootfile");
    if (rootfiles.getLength() == 0) throw invalid("Container has no rootfile");
    var path = ((org.w3c.dom.Element) rootfiles.item(0)).getAttribute("full-path");
    return safeEntryName(path);
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
      return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    } catch (Exception exception) {
      throw new EpubSpikeException("Unsafe or invalid XML", exception);
    }
  }

  private Metadata metadata(Document opf, String packagePath) {
    return new Metadata(
        firstText(opf, "title").orElse(null),
        firstText(opf, "creator").orElse(null),
        firstText(opf, "language").map(this::normalizeLanguage).orElse(null),
        packagePath);
  }

  private Optional<String> firstText(Document document, String localName) {
    var elements = document.getElementsByTagNameNS("*", localName);
    if (elements.getLength() == 0) return Optional.empty();
    var value = elements.item(0).getTextContent().strip();
    return value.isEmpty() ? Optional.empty() : Optional.of(value);
  }

  private String normalizeLanguage(String raw) {
    var tag = raw.strip().replace('_', '-');
    var locale = Locale.forLanguageTag(tag);
    if (locale.getLanguage().isBlank() || "und".equalsIgnoreCase(tag)) return null;
    return locale.toLanguageTag();
  }

  private Map<String, ManifestItem> manifest(Document opf, String packagePath) {
    var base = parent(packagePath);
    var result = new HashMap<String, ManifestItem>();
    var items = opf.getElementsByTagNameNS("*", "item");
    for (int index = 0; index < items.getLength(); index++) {
      var element = (org.w3c.dom.Element) items.item(index);
      var id = element.getAttribute("id");
      var href = element.getAttribute("href");
      if (id.isBlank() || href.isBlank()) throw invalid("Manifest item misses id or href");
      if (isExternal(href)) throw invalid("External manifest resource is not supported");
      var path = resolve(base, href);
      result.put(
          id,
          new ManifestItem(
              id, path, element.getAttribute("media-type"), element.getAttribute("properties")));
    }
    return result;
  }

  private List<String> spine(Document opf) {
    var result = new ArrayList<String>();
    var refs = opf.getElementsByTagNameNS("*", "itemref");
    for (int index = 0; index < refs.getLength(); index++) {
      var idref = ((org.w3c.dom.Element) refs.item(index)).getAttribute("idref");
      if (!idref.isBlank()) result.add(idref);
    }
    return result;
  }

  private boolean hasEncryptedSpine(
      ZipFile zip, String packagePath, Map<String, ManifestItem> manifest, List<String> spineIds)
      throws IOException {
    var entry = zip.getEntry(ENCRYPTION);
    if (entry == null) return false;
    var encryption = parseXml(readLimited(zip, entry));
    var encrypted = new HashSet<String>();
    var references = encryption.getElementsByTagNameNS("*", "CipherReference");
    for (int index = 0; index < references.getLength(); index++) {
      var uri = ((org.w3c.dom.Element) references.item(index)).getAttribute("URI");
      if (!uri.isBlank()) encrypted.add(resolve("", uri));
    }
    for (var id : spineIds) {
      var item = manifest.get(id);
      if (item != null && encrypted.contains(item.path())) return true;
    }
    return false;
  }

  private Optional<Cover> extractCover(
      ZipFile zip, Document opf, Map<String, ManifestItem> manifest) throws IOException {
    ManifestItem cover =
        manifest.values().stream()
            .filter(item -> hasToken(item.properties(), "cover-image"))
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
    if (!cover.mediaType().startsWith("image/")) throw invalid("Cover is not an image");
    var bytes = readRequired(zip, cover.path());
    try (var input = new ByteArrayInputStream(bytes)) {
      var image = ImageIO.read(input);
      if (image == null) throw invalid("Cover bytes are not a supported image");
      long pixels = (long) image.getWidth() * image.getHeight();
      if (pixels > limits.maxImagePixels()) throw invalid("Cover dimensions exceed limit");
    }
    return Optional.of(new Cover(cover.mediaType(), bytes));
  }

  private List<Block> extractBlocks(byte[] xhtmlBytes) {
    var document =
        Jsoup.parse(new String(xhtmlBytes, StandardCharsets.UTF_8), "", Parser.xmlParser());
    document
        .select("script,style,iframe,frame,object,embed,form,input,button,video,audio,svg")
        .remove();
    document.select("*").forEach(this::removeUnsafeAttributes);
    var blocks = new ArrayList<Block>();
    for (var element : document.getAllElements()) {
      var tag = element.normalName();
      if (!BLOCK_TAGS.contains(tag)) continue;
      if (hasBlockAncestor(element)) continue;
      var text = element.text().strip();
      if (!text.isEmpty()) blocks.add(new Block(type(tag), text));
    }
    return blocks;
  }

  private void removeUnsafeAttributes(Element element) {
    var attributes = new ArrayList<>(element.attributes().asList());
    for (var attribute : attributes) {
      var key = attribute.getKey().toLowerCase(Locale.ROOT);
      var value = attribute.getValue().strip();
      if (key.startsWith("on") || isRemoteAttribute(key, value))
        element.removeAttr(attribute.getKey());
    }
  }

  private boolean isRemoteAttribute(String key, String value) {
    if (!(key.equals("href") || key.equals("src") || key.equals("xlink:href"))) return false;
    return isExternal(value) || value.toLowerCase(Locale.ROOT).startsWith("javascript:");
  }

  private boolean hasBlockAncestor(Element element) {
    for (var parent = element.parent(); parent != null; parent = parent.parent()) {
      if (BLOCK_TAGS.contains(parent.normalName())) return true;
    }
    return false;
  }

  private Block.Type type(String tag) {
    return switch (tag) {
      case "blockquote" -> Block.Type.BLOCKQUOTE;
      case "li" -> Block.Type.LIST_ITEM;
      case "p" -> Block.Type.PARAGRAPH;
      default -> Block.Type.HEADING;
    };
  }

  private boolean isXhtml(String mediaType) {
    return "application/xhtml+xml".equalsIgnoreCase(mediaType)
        || "text/html".equalsIgnoreCase(mediaType);
  }

  private boolean isExternal(String value) {
    try {
      var uri = URI.create(value);
      return uri.isAbsolute() || value.startsWith("//");
    } catch (IllegalArgumentException exception) {
      throw invalid("Invalid resource URI");
    }
  }

  private String parent(String path) {
    var slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash + 1);
  }

  private String resolve(String base, String href) {
    var withoutFragment = href.split("#", 2)[0];
    return safeEntryName(Paths.get(base).resolve(withoutFragment).normalize().toString());
  }

  private boolean hasToken(String values, String expected) {
    return List.of(values.strip().split("\\s+")).contains(expected);
  }

  private EpubSpikeException invalid(String reason) {
    return new EpubSpikeException("INVALID_EPUB: " + reason);
  }

  private record ManifestItem(String id, String path, String mediaType, String properties) {}
}
