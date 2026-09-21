package com.soap.soap.domain.service;

import java.util.regex.Pattern;

public final class DocumentSectionTitleSanitizer {

  private static final Pattern TECHNICAL_PREFIX_PATTERN =
      Pattern.compile(
          "^(id[-_]|idp\\d+|x_id|calibre_id|pgepubid|_id|itemId).*$", Pattern.CASE_INSENSITIVE);

  private static final Pattern ANCHOR_OR_FRAGMENT_PATTERN =
      Pattern.compile("^(#|anchor[-_]|fragment[-_]|a[-_]\\d+).*$", Pattern.CASE_INSENSITIVE);

  private static final Pattern TECHNICAL_FILE_OR_PATH_PATTERN =
      Pattern.compile(
          "^.*[/\\\\].*$|^.*\\.(xhtml|html|xml|htm|ncx|opf|jpe?g|png|gif|svg)$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern TECHNICAL_GENERATOR_ID_PATTERN =
      Pattern.compile(
          "^(htmltoc|toc|nav|cover|titlepage|title_page|page[:_\\-]?\\d+|bk\\d+.*|ch\\d+s\\d+|c\\d{1,4})$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^(urn:uuid:)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
          Pattern.CASE_INSENSITIVE);

  private DocumentSectionTitleSanitizer() {}

  public static String sanitize(String title) {
    if (title == null || title.isBlank()) {
      return null;
    }
    var normalized = title.strip();
    if (TECHNICAL_PREFIX_PATTERN.matcher(normalized).matches()
        || ANCHOR_OR_FRAGMENT_PATTERN.matcher(normalized).matches()
        || TECHNICAL_FILE_OR_PATH_PATTERN.matcher(normalized).matches()
        || TECHNICAL_GENERATOR_ID_PATTERN.matcher(normalized).matches()
        || UUID_PATTERN.matcher(normalized).matches()) {
      return null;
    }
    return normalized;
  }
}
