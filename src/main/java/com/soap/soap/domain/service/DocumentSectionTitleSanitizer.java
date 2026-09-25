package com.soap.soap.domain.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class DocumentSectionTitleSanitizer {

  private static final Set<String> EXACT_TECHNICAL_NAMES =
      Set.of("htmltoc", "toc", "nav", "cover", "titlepage", "title_page");

  private static final Set<String> TECHNICAL_EXTENSIONS =
      Set.of("xhtml", "html", "xml", "htm", "ncx", "opf", "jpeg", "jpg", "png", "gif", "svg");

  private static final Pattern CHAPTER_SECTION_PATTERN = Pattern.compile("^ch\\d+s\\d+$");

  private static final Pattern UUID_PATTERN =
      Pattern.compile("^(urn:uuid:)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  private DocumentSectionTitleSanitizer() {}

  public static String sanitize(String title) {
    if (title == null || title.isBlank()) {
      return null;
    }
    var normalized = title.strip();
    var lower = normalized.toLowerCase(Locale.ROOT);

    if (isTechnicalPathOrFile(normalized, lower)
        || isTechnicalPrefix(lower)
        || isAnchorOrFragment(lower)
        || isTechnicalGeneratorId(lower)
        || UUID_PATTERN.matcher(lower).matches()) {
      return null;
    }
    return normalized;
  }

  private static boolean isTechnicalPathOrFile(String raw, String lower) {
    if (raw.indexOf('/') >= 0 || raw.indexOf('\\') >= 0) {
      return true;
    }
    int dot = lower.lastIndexOf('.');
    if (dot >= 0 && dot < lower.length() - 1) {
      String ext = lower.substring(dot + 1);
      return TECHNICAL_EXTENSIONS.contains(ext);
    }
    return false;
  }

  private static boolean isTechnicalPrefix(String lower) {
    if (lower.startsWith("id-")
        || lower.startsWith("id_")
        || lower.startsWith("x_id")
        || lower.startsWith("calibre_id")
        || lower.startsWith("pgepubid")
        || lower.startsWith("_id")
        || lower.startsWith("itemid")) {
      return true;
    }
    if (lower.startsWith("idp") && lower.length() > 3) {
      return Character.isDigit(lower.charAt(3));
    }
    return false;
  }

  private static boolean isAnchorOrFragment(String lower) {
    if (lower.startsWith("#")
        || lower.startsWith("anchor-")
        || lower.startsWith("anchor_")
        || lower.startsWith("fragment-")
        || lower.startsWith("fragment_")) {
      return true;
    }
    if ((lower.startsWith("a-") || lower.startsWith("a_")) && lower.length() > 2) {
      return Character.isDigit(lower.charAt(2));
    }
    return false;
  }

  private static boolean isTechnicalGeneratorId(String lower) {
    if (EXACT_TECHNICAL_NAMES.contains(lower)) {
      return true;
    }
    if (isPageIdentifier(lower)) {
      return true;
    }
    if (lower.startsWith("bk") && lower.length() > 2 && Character.isDigit(lower.charAt(2))) {
      return true;
    }
    if (CHAPTER_SECTION_PATTERN.matcher(lower).matches()) {
      return true;
    }
    return isCNumber(lower);
  }

  private static boolean isPageIdentifier(String lower) {
    if (!lower.startsWith("page")) {
      return false;
    }
    int idx = 4;
    if (idx < lower.length()) {
      char delimiter = lower.charAt(idx);
      if (delimiter == ':' || delimiter == '_' || delimiter == '-') {
        idx++;
      }
    }
    if (idx >= lower.length()) {
      return false;
    }
    for (int i = idx; i < lower.length(); i++) {
      if (!Character.isDigit(lower.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCNumber(String lower) {
    if (lower.length() < 2 || lower.length() > 5 || lower.charAt(0) != 'c') {
      return false;
    }
    for (int i = 1; i < lower.length(); i++) {
      if (!Character.isDigit(lower.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
