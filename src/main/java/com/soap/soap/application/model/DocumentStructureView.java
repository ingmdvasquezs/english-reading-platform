package com.soap.soap.application.model;

import java.util.List;
import java.util.UUID;

public record DocumentStructureView(
    UUID documentId, UUID firstUnitId, List<Section> sections, long totalUnits) {
  public DocumentStructureView {
    sections = List.copyOf(sections);
  }

  public record Section(UUID id, int ordinal, String title, UUID firstUnitId, long unitCount) {}
}
