package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingHistoryItem;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingHistoryRequest;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingHistoryResponse;
import com.soap.soap.infrastructure.soap.generated.PlatformReadingHistoryItemType;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import org.springframework.stereotype.Component;

@Component
public class ListPlatformReadingHistorySoapMapper extends SoapMapperSupport {
  public PageRequest toPageRequest(ListPlatformReadingHistoryRequest request) {
    return new PageRequest(request.getPage(), request.getSize());
  }

  public ListPlatformReadingHistoryResponse toResponse(
      PageResult<PlatformReadingHistoryItem> page) {
    var response = new ListPlatformReadingHistoryResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream().map(this::toItem).forEach(response.getReadings()::add);
    return response;
  }

  private PlatformReadingHistoryItemType toItem(PlatformReadingHistoryItem item) {
    var result = new PlatformReadingHistoryItemType();
    result.setReadingId(item.readingId().toString());
    result.setTitle(item.title());
    result.setEditorialLevel(EditorialLevelType.fromValue(item.editorialLevel().name()));
    result.setCategory(item.category());
    result.setCoverKey(item.coverKey());
    result.setProgressStatus(ReadingProgressStatusType.fromValue(item.progressStatus().name()));
    return result;
  }
}
