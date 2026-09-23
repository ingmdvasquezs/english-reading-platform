package com.soap.soap.application.command;

import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.domain.model.DocumentUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CompleteDocumentImportCommand(
    UUID jobId,
    UUID leaseToken,
    String workerId,
    List<DocumentSection> sections,
    List<DocumentUnit> units,
    String title,
    String author,
    String language,
    String coverAssetKey) {

  public CompleteDocumentImportCommand {
    Objects.requireNonNull(jobId, "jobId must not be null");
    Objects.requireNonNull(leaseToken, "leaseToken must not be null");
    Objects.requireNonNull(workerId, "workerId must not be null");
    Objects.requireNonNull(sections, "sections must not be null");
    Objects.requireNonNull(units, "units must not be null");
  }
}
