package com.soap.soap.application.model;

import java.util.List;

public record ParsedDocument(
    String title,
    String author,
    String declaredLanguage,
    List<ParsedDocumentSection> sections,
    ParsedDocumentCover cover) {
  public ParsedDocument {
    sections = List.copyOf(sections);
  }
}
