package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingsResponse;
import com.soap.soap.infrastructure.soap.generated.PlatformReadingSummaryType;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import org.springframework.stereotype.Component;

@Component
public class ListPlatformReadingsSoapMapper extends SoapMapperSupport {
  public PageRequest toPageRequest(ListPlatformReadingsRequest request) {
    return new PageRequest(request.getPage(), request.getSize());
  }

  public ListPlatformReadingsResponse toResponse(PageResult<PlatformReadingSummary> page) {
    var response = new ListPlatformReadingsResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream().map(this::toSummary).forEach(response.getReadings()::add);
    return response;
  }

  private PlatformReadingSummaryType toSummary(PlatformReadingSummary summary) {
    var result = new PlatformReadingSummaryType();
    result.setReadingId(summary.id().toString());
    result.setTitle(summary.title());
    result.setLanguage(summary.language());
    result.setEditorialLevel(EditorialLevelType.fromValue(summary.editorialLevel().name()));
    result.setCategory(summary.category());
    result.setCreatedAt(toXmlDate(summary.createdAt()));
    if (summary.progressStatus() != null) {
      result.setProgressStatus(
          ReadingProgressStatusType.fromValue(summary.progressStatus().name()));
    }
    result.setCoverKey(summary.coverKey());
    return result;
  }
}
