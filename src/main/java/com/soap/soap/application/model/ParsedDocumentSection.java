package com.soap.soap.application.model;

import java.util.List;

public record ParsedDocumentSection(String title, String sourceLocator, List<String> blocks) {
  public ParsedDocumentSection {
    blocks = List.copyOf(blocks);
  }
}
