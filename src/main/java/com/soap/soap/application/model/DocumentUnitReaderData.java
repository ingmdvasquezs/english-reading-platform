package com.soap.soap.application.model;

import com.soap.soap.domain.model.DocumentProgressStatus;
import java.util.List;
import java.util.UUID;

public record DocumentUnitReaderData(
    UUID documentId,
    UUID sectionId,
    String sectionTitle,
    int sectionOrdinal,
    int totalSections,
    UUID unitId,
    int sectionUnitOrdinal,
    int sectionUnitCount,
    int globalOrdinal,
    int totalUnits,
    UUID previousUnitId,
    UUID nextUnitId,
    String content,
    List<ReaderToken> tokens,
    DocumentProgressStatus progressStatus) {
  public DocumentUnitReaderData {
    tokens = List.copyOf(tokens);
  }
}
