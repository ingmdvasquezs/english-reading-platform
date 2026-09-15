package com.soap.soap.application.command;

import com.soap.soap.domain.model.EditorialLevel;
import java.util.UUID;

public record UpdatePlatformReadingProvenanceCommand(
    UUID readingId,
    String adaptationGroupKey,
    String language,
    EditorialLevel editorialLevel,
    String sourceTitle,
    String sourceAuthor,
    String sourceUrl,
    String sourceNotes) {

  public UpdatePlatformReadingProvenanceCommand(
      String adaptationGroupKey,
      String language,
      EditorialLevel editorialLevel,
      String sourceTitle,
      String sourceAuthor,
      String sourceUrl,
      String sourceNotes) {
    this(
        null,
        adaptationGroupKey,
        language,
        editorialLevel,
        sourceTitle,
        sourceAuthor,
        sourceUrl,
        sourceNotes);
  }
}
