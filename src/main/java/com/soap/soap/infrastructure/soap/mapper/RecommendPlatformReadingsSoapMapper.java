package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.generated.RecommendPlatformReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.RecommendPlatformReadingsResponse;
import com.soap.soap.infrastructure.soap.generated.RecommendedPlatformReadingType;
import org.springframework.stereotype.Component;

@Component
public class RecommendPlatformReadingsSoapMapper extends SoapMapperSupport {
  public PageRequest toPageRequest(RecommendPlatformReadingsRequest request) {
    return new PageRequest(request.getPage(), request.getSize());
  }

  public RecommendPlatformReadingsResponse toResponse(PageResult<RecommendedPlatformReading> page) {
    var response = new RecommendPlatformReadingsResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream().map(this::toReading).forEach(response.getReadings()::add);
    return response;
  }

  private RecommendedPlatformReadingType toReading(RecommendedPlatformReading reading) {
    var result = new RecommendedPlatformReadingType();
    result.setReadingId(reading.readingId().toString());
    result.setTitle(reading.title());
    result.setLanguage(reading.language());
    result.setEditorialLevel(EditorialLevelType.fromValue(reading.editorialLevel().name()));
    result.setCategory(reading.category());
    result.setCreatedAt(toXmlDate(reading.createdAt()));
    result.setUniqueWords(reading.uniqueWords());
    result.setKnownWords(reading.knownWords());
    result.setLearningWords(reading.learningWords());
    result.setExplicitNewWords(reading.explicitNewWords());
    result.setIgnoredWords(reading.ignoredWords());
    result.setUnclassifiedWords(reading.unclassifiedWords());
    result.setVocabularyFitPercentage(reading.vocabularyFitPercentage());
    result.setClassificationConfidencePercentage(reading.classificationConfidencePercentage());
    if (reading.progressStatus() != null) {
      result.setProgressStatus(
          ReadingProgressStatusType.fromValue(reading.progressStatus().name()));
    }
    result.setCoverKey(reading.coverKey());
    return result;
  }
}
