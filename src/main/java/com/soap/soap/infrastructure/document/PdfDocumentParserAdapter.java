package com.soap.soap.infrastructure.document;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.application.model.ParsedDocumentSection;
import com.soap.soap.application.port.out.DocumentParserPort;
import com.soap.soap.domain.model.DocumentFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfDocumentParserAdapter implements DocumentParserPort {
  private static final int MINIMUM_TEXT_LETTERS = 10;
  private static final int TITLE_SCAN_LINES = 12;
  private static final java.util.Set<String> NON_EDITORIAL_METADATA =
      java.util.Set.of(
          "anonymous", "unknown", "untitled", "unspecified", "microsoft word", "document");
  private final DocumentImportLimits limits;

  @Override
  public ParsedDocument parse(Path source, DocumentFormat format) {
    if (format != DocumentFormat.PDF) throw invalid("Unsupported document format");
    try {
      validateSource(source);
      try (var document = Loader.loadPDF(source.toFile())) {
        if (document.isEncrypted()) {
          throw new DocumentImportException(
              Reason.PDF_PASSWORD_PROTECTED, "Encrypted PDF is unsupported");
        }
        var pageCount = document.getNumberOfPages();
        if (pageCount == 0) throw invalid("PDF contains no pages");
        if (pageCount > limits.maxPdfPages()) throw security("PDF has too many pages");
        var pages = extractPages(document);
        if (pages.stream().mapToInt(this::letterCount).sum() < MINIMUM_TEXT_LETTERS) {
          throw new DocumentImportException(
              Reason.PDF_SCANNED_NOT_SUPPORTED, "PDF contains no useful extractable text");
        }
        var repeatedEdges = repeatedEdges(pages);
        var sections = new ArrayList<ParsedDocumentSection>();
        for (int index = 0; index < pages.size(); index++) {
          var blocks = cleanPage(pages.get(index), repeatedEdges);
          if (!blocks.isEmpty()) {
            sections.add(new ParsedDocumentSection(null, "page:" + (index + 1), blocks));
          }
        }
        if (sections.isEmpty()) {
          throw new DocumentImportException(
              Reason.PDF_SCANNED_NOT_SUPPORTED, "PDF contains no useful extractable text");
        }
        var information = document.getDocumentInformation();
        var metadataTitle = editorialMetadata(information.getTitle());
        return new ParsedDocument(
            metadataTitle != null ? metadataTitle : firstPageTitle(pages.getFirst()),
            editorialMetadata(information.getAuthor()),
            normalizeLanguage(document.getDocumentCatalog().getLanguage()),
            sections,
            null);
      }
    } catch (DocumentImportException exception) {
      throw exception;
    } catch (InvalidPasswordException exception) {
      throw new DocumentImportException(
          Reason.PDF_PASSWORD_PROTECTED, "Password-protected PDF is unsupported", exception);
    } catch (IOException | RuntimeException exception) {
      throw new DocumentImportException(Reason.INVALID_PDF, "Invalid PDF", exception);
    }
  }

  private void validateSource(Path source) throws IOException {
    if (source == null || !Files.isRegularFile(source)) throw invalid("Source is not a file");
    var size = Files.size(source);
    if (size == 0) throw invalid("PDF is empty");
    if (size > limits.maxSourceBytes()) {
      throw new DocumentImportException(Reason.FILE_TOO_LARGE, "PDF exceeds source byte limit");
    }
    try (var input = Files.newInputStream(source)) {
      var magic = input.readNBytes(5);
      if (magic.length != 5
          || magic[0] != '%'
          || magic[1] != 'P'
          || magic[2] != 'D'
          || magic[3] != 'F'
          || magic[4] != '-') throw invalid("PDF signature is missing");
    }
  }

  private List<String> extractPages(PDDocument document) throws IOException {
    var stripper = new PDFTextStripper();
    stripper.setSortByPosition(true);
    var pages = new ArrayList<String>();
    var total = 0;
    for (int page = 1; page <= document.getNumberOfPages(); page++) {
      stripper.setStartPage(page);
      stripper.setEndPage(page);
      var text = stripper.getText(document);
      total = Math.addExact(total, text.length());
      if (total > limits.maxExtractedCharacters()) {
        throw security("PDF extracted text exceeds character limit");
      }
      pages.add(text);
    }
    return pages;
  }

  private Map<String, Integer> repeatedEdges(List<String> pages) {
    var counts = new HashMap<String, Integer>();
    if (pages.size() < 3) return counts;
    for (var page : pages) {
      var lines = meaningfulLines(page);
      if (lines.isEmpty()) continue;
      countEdge(counts, lines.getFirst());
      if (lines.size() > 1) countEdge(counts, lines.getLast());
    }
    var threshold = Math.max(3, (int) Math.ceil(pages.size() * 0.6));
    counts.entrySet().removeIf(entry -> entry.getValue() < threshold);
    return counts;
  }

  private void countEdge(Map<String, Integer> counts, String line) {
    var key = normalizeLine(line).toLowerCase(Locale.ROOT);
    if (key.length() >= 3 && key.length() <= 120 && !pageNumber(key)) {
      counts.merge(key, 1, Integer::sum);
    }
  }

  private List<String> cleanPage(String raw, Map<String, Integer> repeatedEdges) {
    var lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    var filtered = new ArrayList<String>();
    for (var rawLine : lines) {
      var line = normalizeLine(rawLine);
      var key = line.toLowerCase(Locale.ROOT);
      if (pageNumber(key) || repeatedEdges.containsKey(key)) continue;
      filtered.add(line);
    }
    var blocks = new ArrayList<String>();
    var current = new StringBuilder();
    for (int index = 0; index < filtered.size(); index++) {
      var line = filtered.get(index);
      if (line.isBlank()) {
        finish(blocks, current);
        continue;
      }
      if (current.length() > 1
          && current.charAt(current.length() - 1) == '-'
          && Character.isLowerCase(current.charAt(current.length() - 2))
          && Character.isLowerCase(line.charAt(0))) {
        current.setLength(current.length() - 1);
        current.append(line);
      } else {
        if (current.length() > 0) current.append(' ');
        current.append(line);
      }
    }
    finish(blocks, current);
    return blocks;
  }

  private List<String> meaningfulLines(String page) {
    return page.lines().map(this::normalizeLine).filter(value -> !value.isBlank()).toList();
  }

  private String normalizeLine(String value) {
    return value == null ? "" : value.strip().replaceAll("[\\t \\x0B\\f]+", " ");
  }

  private boolean pageNumber(String value) {
    return value.matches("(?i)(page\\s+)?\\d+(\\s+(of|/)\\s*\\d+)?");
  }

  private void finish(List<String> blocks, StringBuilder current) {
    var value = current.toString().strip();
    if (!value.isEmpty()) blocks.add(value);
    current.setLength(0);
  }

  private int letterCount(String value) {
    return (int) value.codePoints().filter(Character::isLetter).count();
  }

  private String textOrNull(String value) {
    if (value == null) return null;
    var clean = value.strip().replaceAll("\\s+", " ");
    return clean.isEmpty() ? null : clean;
  }

  private String editorialMetadata(String value) {
    var clean = textOrNull(value);
    if (clean == null) return null;
    var semantic =
        clean.toLowerCase(Locale.ROOT).replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
    if (NON_EDITORIAL_METADATA.contains(semantic)
        || semantic.endsWith(".pdf")
        || semantic.endsWith(".doc")
        || semantic.endsWith(".docx")) return null;
    return clean;
  }

  private String firstPageTitle(String firstPage) {
    return meaningfulLines(firstPage).stream()
        .limit(TITLE_SCAN_LINES)
        .filter(this::editorialHeading)
        .findFirst()
        .orElse(null);
  }

  private boolean editorialHeading(String line) {
    if (line.length() < 4 || line.length() > 120) return false;
    var lower = line.toLowerCase(Locale.ROOT);
    if (lower.matches(".*\\bpage\\s+\\d+\\b.*")
        || lower.matches("^(chapter|part|section)\\b.*")
        || lower.matches("^(a|an)\\s+(short|brief)\\b.*(sample|reading|document).*$")
        || line.matches(".*[.!?;:]$")) return false;
    var words = line.split("\\s+");
    if (words.length < 2 || words.length > 12) return false;
    int editorialWords = 0;
    int capitalizedWords = 0;
    for (var word : words) {
      var letters = word.replaceAll("[^\\p{L}]", "");
      if (letters.length() < 2) continue;
      var wordLower = letters.toLowerCase(Locale.ROOT);
      if (java.util.Set.of("a", "an", "the", "of", "and", "or", "in", "on", "to", "for")
          .contains(wordLower)) continue;
      editorialWords++;
      if (Character.isUpperCase(letters.codePointAt(0))) capitalizedWords++;
    }
    return editorialWords >= 2 && capitalizedWords * 10 >= editorialWords * 7;
  }

  private String normalizeLanguage(String value) {
    if (value == null || value.isBlank()) return null;
    var locale = Locale.forLanguageTag(value.strip().replace('_', '-'));
    return locale.getLanguage().isBlank() || "und".equals(locale.toLanguageTag())
        ? null
        : locale.toLanguageTag();
  }

  private DocumentImportException invalid(String message) {
    return new DocumentImportException(Reason.INVALID_PDF, message);
  }

  private DocumentImportException security(String message) {
    return new DocumentImportException(Reason.SECURITY_LIMIT_EXCEEDED, message);
  }
}
