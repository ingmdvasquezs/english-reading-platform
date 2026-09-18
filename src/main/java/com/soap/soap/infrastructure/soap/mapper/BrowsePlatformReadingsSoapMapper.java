package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.BrowsePlatformReadingsQuery;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.domain.model.EditorialCategory;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.infrastructure.soap.generated.BrowsePlatformReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.BrowsePlatformReadingsResponse;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.generated.RecommendationReasonCodeType;
import com.soap.soap.infrastructure.soap.generated.RecommendedPlatformReadingType;
import org.springframework.stereotype.Component;

@Component
public class BrowsePlatformReadingsSoapMapper extends SoapMapperSupport {

  public BrowsePlatformReadingsQuery toQuery(BrowsePlatformReadingsRequest request) {
    if (request == null) {
      throw new InvalidApplicationArgumentException("Browse request must not be null");
    }

    EditorialCategory category = null;
    if (request.getCategory() != null && !request.getCategory().isBlank()) {
      try {
        category = EditorialCategory.fromString(request.getCategory());
      } catch (IllegalArgumentException e) {
        throw new InvalidApplicationArgumentException(
            "Unknown editorial category: " + request.getCategory());
      }
    }

    EditorialLevel level = null;
    if (request.getEditorialLevel() != null) {
      try {
        level = EditorialLevel.valueOf(request.getEditorialLevel().value());
      } catch (IllegalArgumentException e) {
        throw new InvalidApplicationArgumentException(
            "Unknown editorial level: " + request.getEditorialLevel());
      }
    }

    String collectionKey =
        request.getCollectionKey() != null && !request.getCollectionKey().isBlank()
            ? request.getCollectionKey().trim()
            : null;

    String countryCode =
        request.getCountryCode() != null && !request.getCountryCode().isBlank()
            ? request.getCountryCode().trim().toUpperCase()
            : null;
    if (countryCode != null && !countryCode.matches("^[A-Z]{2}$")) {
      throw new InvalidApplicationArgumentException(
          "Country code must be 2 uppercase ISO letters: " + request.getCountryCode());
    }

    com.soap.soap.domain.model.DiscoveryTopic discoveryTopic = null;
    if (request.getDiscoveryTopic() != null && !request.getDiscoveryTopic().isBlank()) {
      try {
        discoveryTopic =
            com.soap.soap.domain.model.DiscoveryTopic.fromString(request.getDiscoveryTopic());
      } catch (IllegalArgumentException e) {
        throw new InvalidApplicationArgumentException(
            "Unknown discovery topic: " + request.getDiscoveryTopic());
      }
    }

    return new BrowsePlatformReadingsQuery(
        collectionKey,
        category,
        level,
        countryCode,
        discoveryTopic,
        new PageRequest(request.getPage(), request.getSize()));
  }

  public BrowsePlatformReadingsResponse toResponse(PageResult<RecommendedPlatformReading> page) {
    var response = new BrowsePlatformReadingsResponse();
    response.setPage(page.page());
    response.setSize(page.size());
    response.setTotalElements(page.totalElements());
    page.content().stream().map(this::toReading).forEach(response.getReadings()::add);
    return response;
  }

  public RecommendedPlatformReadingType toReading(RecommendedPlatformReading reading) {
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
    if (reading.reasonCode() != null) {
      result.setReasonCode(RecommendationReasonCodeType.fromValue(reading.reasonCode().name()));
    }
    result.setCoverKey(reading.coverKey());
    result.setShortDescription(reading.shortDescription());
    result.setCountryCode(reading.countryCode());
    if (reading.discoveryTopic() != null) {
      result.setDiscoveryTopic(reading.discoveryTopic().name());
    }
    return result;
  }
}
