package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.UserVocabularyPage;
import com.soap.soap.application.model.VocabularyQuery;
import com.soap.soap.application.model.VocabularySummary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.infrastructure.soap.generated.ListUserVocabularyRequest;
import com.soap.soap.infrastructure.soap.generated.ListUserVocabularyResponse;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;
import com.soap.soap.infrastructure.soap.generated.VocabularySummaryType;
import org.springframework.stereotype.Component;

@Component
public class ListUserVocabularySoapMapper extends VocabularySoapMapperSupport {

  public VocabularyQuery toQuery(ListUserVocabularyRequest request) {
    var pageRequest = new PageRequest(request.getPage(), request.getSize());
    var status = toOptionalStatus(request.getStatus());
    return new VocabularyQuery(pageRequest, status, request.getSearch());
  }

  public ListUserVocabularyResponse toResponse(UserVocabularyPage result) {
    var response = new ListUserVocabularyResponse();
    var page = result.page();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    if (result.summary() != null) {
      response.setSummary(toSummaryType(result.summary()));
    }
    page.content().stream().map(this::toEntry).forEach(response.getEntries()::add);
    return response;
  }

  private VocabularyStatus toOptionalStatus(VocabularyStatusType status) {
    return status != null ? VocabularyStatus.valueOf(status.value()) : null;
  }

  private VocabularySummaryType toSummaryType(VocabularySummary summary) {
    var type = new VocabularySummaryType();
    type.setTotalCount(summary.totalCount());
    type.setNewCount(summary.newCount());
    type.setLearningCount(summary.learningCount());
    type.setKnownCount(summary.knownCount());
    type.setIgnoredCount(summary.ignoredCount());
    return type;
  }
}
