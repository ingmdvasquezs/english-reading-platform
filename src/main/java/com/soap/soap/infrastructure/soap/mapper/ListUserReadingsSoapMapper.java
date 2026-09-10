package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.infrastructure.soap.generated.ListUserReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListUserReadingsResponse;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import org.springframework.stereotype.Component;

@Component
public class ListUserReadingsSoapMapper extends SoapMapperSupport {
  public PageRequest toPageRequest(ListUserReadingsRequest request) {
    return new PageRequest(request.getPage(), request.getSize());
  }

  public ListUserReadingsResponse toResponse(PageResult<ReadingSummary> page) {
    var response = new ListUserReadingsResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream()
        .map(
            summary -> {
              var result = new com.soap.soap.infrastructure.soap.generated.ReadingSummaryType();
              result.setReadingId(summary.id().toString());
              result.setTitle(summary.title());
              result.setLanguage(summary.language());
              result.setCreatedAt(toXmlDate(summary.createdAt()));
              result.setUniqueWords(summary.uniqueWords());
              result.setKnownWords(summary.knownWords());
              result.setLearningWords(summary.learningWords());
              result.setExplicitNewWords(summary.explicitNewWords());
              result.setIgnoredWords(summary.ignoredWords());
              result.setUnclassifiedWords(summary.unclassifiedWords());
              result.setVocabularyFitPercentage(summary.vocabularyFitPercentage());
              result.setClassificationConfidencePercentage(
                  summary.classificationConfidencePercentage());
              if (summary.progressStatus() != null) {
                result.setProgressStatus(
                    ReadingProgressStatusType.fromValue(summary.progressStatus().name()));
              }
              return result;
            })
        .forEach(response.getReadings()::add);
    return response;
  }
}
