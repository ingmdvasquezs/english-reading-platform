package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.soap.generated.ListUserReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListUserReadingsResponse;
import org.springframework.stereotype.Component;

@Component
public class ListUserReadingsSoapMapper extends SoapMapperSupport {
  private final GetReadingSoapMapper readingMapper;

  public ListUserReadingsSoapMapper(GetReadingSoapMapper readingMapper) {
    this.readingMapper = readingMapper;
  }

  public PageRequest toPageRequest(ListUserReadingsRequest request) {
    return new PageRequest(request.getPage(), request.getSize());
  }

  public ListUserReadingsResponse toResponse(PageResult<Reading> page) {
    var response = new ListUserReadingsResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream().map(readingMapper::toReadingType).forEach(response.getReadings()::add);
    return response;
  }
}
