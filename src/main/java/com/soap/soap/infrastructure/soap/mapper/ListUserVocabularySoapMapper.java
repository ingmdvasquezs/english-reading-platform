package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.infrastructure.soap.generated.ListUserVocabularyRequest;
import com.soap.soap.infrastructure.soap.generated.ListUserVocabularyResponse;
import org.springframework.stereotype.Component;

@Component
public class ListUserVocabularySoapMapper extends VocabularySoapMapperSupport {
  public PageRequest toPageRequest(ListUserVocabularyRequest request) {
    return new PageRequest(request.getPage(), request.getSize());
  }

  public ListUserVocabularyResponse toResponse(PageResult<UserVocabulary> page) {
    var response = new ListUserVocabularyResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream().map(this::toEntry).forEach(response.getEntries()::add);
    return response;
  }
}
