package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.command.UpdateReadingProgressCommand;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.generated.UpdateReadingProgressRequest;
import com.soap.soap.infrastructure.soap.generated.UpdateReadingProgressResponse;
import org.springframework.stereotype.Component;

@Component
public class UpdateReadingProgressSoapMapper extends SoapMapperSupport {
  public UpdateReadingProgressCommand toCommand(UpdateReadingProgressRequest request) {
    return new UpdateReadingProgressCommand(
        parseUuid(request.getReadingId(), "readingId"),
        ReadingProgressStatus.valueOf(request.getProgressStatus().value()),
        request.getCurrentPartOrdinal(),
        request.getPaginationVersion());
  }

  public UpdateReadingProgressResponse toResponse(ReadingProgress progress) {
    var response = new UpdateReadingProgressResponse();
    response.setReadingId(progress.readingId().toString());
    response.setProgressStatus(ReadingProgressStatusType.fromValue(progress.status().name()));
    response.setStartedAt(toXmlDate(progress.startedAt()));
    if (progress.completedAt() != null) {
      response.setCompletedAt(toXmlDate(progress.completedAt()));
    }
    response.setCurrentPartOrdinal(progress.currentPartOrdinal());
    response.setPaginationVersion(progress.paginationVersion());
    return response;
  }
}
