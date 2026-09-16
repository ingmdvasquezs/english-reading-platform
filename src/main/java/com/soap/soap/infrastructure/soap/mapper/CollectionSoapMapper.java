package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.infrastructure.soap.generated.CollectionType;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.ListCollectionReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListCollectionReadingsResponse;
import com.soap.soap.infrastructure.soap.generated.ListCollectionsResponse;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.generated.RecommendedPlatformReadingType;
import org.springframework.stereotype.Component;

@Component
public class CollectionSoapMapper extends SoapMapperSupport {
  public ListCollectionsResponse toResponse(Iterable<ReadingCollection> collections) {
    var response = new ListCollectionsResponse();
    for (var collection : collections) {
      response.getCollections().add(toCollection(collection));
    }
    return response;
  }

  public PageRequest toPageRequest(ListCollectionReadingsRequest request) {
    return new PageRequest(request.getPage(), request.getSize());
  }

  public ListCollectionReadingsResponse toResponse(PageResult<RecommendedPlatformReading> page) {
    var response = new ListCollectionReadingsResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream().map(this::toSummary).forEach(response.getReadings()::add);
    return response;
  }

  private CollectionType toCollection(ReadingCollection collection) {
    var result = new CollectionType();
    result.setKey(collection.key());
    result.setDisplayName(collection.displayName());
    result.setDescription(collection.description());
    result.setDisplayOrder(collection.displayOrder());
    result.setCoverKey(collection.coverKey());
    return result;
  }

  private RecommendedPlatformReadingType toSummary(RecommendedPlatformReading summary) {
    var result = new RecommendedPlatformReadingType();
    result.setReadingId(summary.readingId().toString());
    result.setTitle(summary.title());
    result.setLanguage(summary.language());
    result.setEditorialLevel(EditorialLevelType.fromValue(summary.editorialLevel().name()));
    result.setCategory(summary.category());
    result.setCreatedAt(toXmlDate(summary.createdAt()));
    result.setUniqueWords(summary.uniqueWords());
    result.setKnownWords(summary.knownWords());
    result.setLearningWords(summary.learningWords());
    result.setExplicitNewWords(summary.explicitNewWords());
    result.setIgnoredWords(summary.ignoredWords());
    result.setUnclassifiedWords(summary.unclassifiedWords());
    result.setVocabularyFitPercentage(summary.vocabularyFitPercentage());
    result.setClassificationConfidencePercentage(summary.classificationConfidencePercentage());
    if (summary.progressStatus() != null) {
      result.setProgressStatus(
          ReadingProgressStatusType.fromValue(summary.progressStatus().name()));
    }
    result.setCoverKey(summary.coverKey());
    result.setShortDescription(summary.shortDescription());
    return result;
  }
}
