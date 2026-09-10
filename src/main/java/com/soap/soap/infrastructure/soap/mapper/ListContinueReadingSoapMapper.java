package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.infrastructure.soap.generated.ContinueReadingItemType;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.ListContinueReadingRequest;
import com.soap.soap.infrastructure.soap.generated.ListContinueReadingResponse;
import com.soap.soap.infrastructure.soap.generated.ReadingOriginType;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import org.springframework.stereotype.Component;

@Component
public class ListContinueReadingSoapMapper extends SoapMapperSupport {
  public PageRequest toPageRequest(ListContinueReadingRequest request) {
    return new PageRequest(request.getPage(), request.getSize());
  }

  public ListContinueReadingResponse toResponse(PageResult<ContinueReadingItem> page) {
    var response = new ListContinueReadingResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream().map(this::toItem).forEach(response.getReadings()::add);
    return response;
  }

  private ContinueReadingItemType toItem(ContinueReadingItem item) {
    var result = new ContinueReadingItemType();
    result.setReadingId(item.readingId().toString());
    result.setTitle(item.title());
    result.setOrigin(ReadingOriginType.fromValue(item.origin().name()));
    result.setProgressStatus(ReadingProgressStatusType.fromValue(item.progressStatus().name()));
    result.setCoverKey(item.coverKey());
    if (item.editorialLevel() != null) {
      result.setEditorialLevel(EditorialLevelType.fromValue(item.editorialLevel().name()));
    }
    result.setCategory(item.category());
    result.setStartedAt(toXmlDate(item.startedAt()));
    return result;
  }
}
