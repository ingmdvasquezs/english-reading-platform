package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.DiscoveryCountrySummary;
import com.soap.soap.application.model.DiscoveryHeroImageSummary;
import com.soap.soap.application.model.DiscoveryHomeResult;
import com.soap.soap.application.model.DiscoveryShelfResult;
import com.soap.soap.application.model.DiscoveryTopicSummary;
import com.soap.soap.application.model.GetDiscoveryHomeQuery;
import com.soap.soap.application.model.LatinAmericaDiscoveryResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.infrastructure.soap.generated.ContinueReadingItemType;
import com.soap.soap.infrastructure.soap.generated.DiscoveryCountrySummaryType;
import com.soap.soap.infrastructure.soap.generated.DiscoveryHeroImageType;
import com.soap.soap.infrastructure.soap.generated.DiscoveryRegionDetailsType;
import com.soap.soap.infrastructure.soap.generated.DiscoveryShelfType;
import com.soap.soap.infrastructure.soap.generated.DiscoveryTopicSummaryType;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryHomeRequest;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryHomeResponse;
import com.soap.soap.infrastructure.soap.generated.LatinAmericaDiscoveryType;
import com.soap.soap.infrastructure.soap.generated.ReadingOriginType;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.generated.RecommendationReasonCodeType;
import com.soap.soap.infrastructure.soap.generated.RecommendedPlatformReadingType;
import org.springframework.stereotype.Component;

@Component
public class GetDiscoveryHomeSoapMapper extends SoapMapperSupport {

  public GetDiscoveryHomeQuery toQuery(GetDiscoveryHomeRequest request) {
    if (request == null) {
      return new GetDiscoveryHomeQuery(10, 8, 8);
    }
    int maxContinue = request.getMaxContinueReading() != null ? request.getMaxContinueReading() : 0;
    int maxForYou = request.getMaxForYou() != null ? request.getMaxForYou() : 0;
    int maxShelf = request.getMaxShelfReadings() != null ? request.getMaxShelfReadings() : 0;
    return new GetDiscoveryHomeQuery(maxContinue, maxForYou, maxShelf);
  }

  public GetDiscoveryHomeResponse toResponse(DiscoveryHomeResult result) {
    var response = new GetDiscoveryHomeResponse();
    if (result == null) {
      return response;
    }

    if (result.continueReading() != null) {
      for (ContinueReadingItem item : result.continueReading()) {
        response.getContinueReading().add(toContinueReadingItemType(item));
      }
    }

    if (result.forYou() != null) {
      for (RecommendedPlatformReading reading : result.forYou()) {
        response.getForYou().add(toRecommendedPlatformReadingType(reading));
      }
    }

    if (result.latinAmerica() != null) {
      response.setLatinAmerica(toLatinAmericaDiscoveryType(result.latinAmerica()));
    }

    if (result.shelves() != null) {
      for (DiscoveryShelfResult shelf : result.shelves()) {
        response.getShelves().add(toDiscoveryShelfType(shelf));
      }
    }

    return response;
  }

  private ContinueReadingItemType toContinueReadingItemType(ContinueReadingItem item) {
    var result = new ContinueReadingItemType();
    result.setReadingId(item.readingId().toString());
    result.setTitle(item.title());
    if (item.origin() != null) {
      result.setOrigin(ReadingOriginType.fromValue(item.origin().name()));
    }
    if (item.progressStatus() != null) {
      result.setProgressStatus(ReadingProgressStatusType.fromValue(item.progressStatus().name()));
    }
    result.setCoverKey(item.coverKey());
    if (item.editorialLevel() != null) {
      result.setEditorialLevel(EditorialLevelType.fromValue(item.editorialLevel().name()));
    }
    result.setCategory(item.category());
    result.setStartedAt(toXmlDate(item.startedAt()));
    result.setShortDescription(item.shortDescription());
    result.setProgressPercentage(item.progressPercentage());
    return result;
  }

  private RecommendedPlatformReadingType toRecommendedPlatformReadingType(
      RecommendedPlatformReading reading) {
    var result = new RecommendedPlatformReadingType();
    result.setReadingId(reading.readingId().toString());
    result.setTitle(reading.title());
    result.setLanguage(reading.language());
    if (reading.editorialLevel() != null) {
      result.setEditorialLevel(EditorialLevelType.fromValue(reading.editorialLevel().name()));
    }
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
    return result;
  }

  private LatinAmericaDiscoveryType toLatinAmericaDiscoveryType(
      LatinAmericaDiscoveryResult latinAmerica) {
    var type = new LatinAmericaDiscoveryType();
    if (latinAmerica.region() != null) {
      var regionType = new DiscoveryRegionDetailsType();
      regionType.setKey(latinAmerica.region().key());
      regionType.setDisplayName(latinAmerica.region().displayName());
      regionType.setSubtitle(latinAmerica.region().subtitle());
      type.setRegion(regionType);
    }
    if (latinAmerica.countries() != null) {
      for (DiscoveryCountrySummary country : latinAmerica.countries()) {
        type.getCountries().add(toCountrySummaryType(country));
      }
    }
    type.setDefaultCountryCode(latinAmerica.defaultCountryCode());
    type.setDefaultTopicKey(latinAmerica.defaultTopicKey());
    if (latinAmerica.readings() != null) {
      for (RecommendedPlatformReading reading : latinAmerica.readings()) {
        type.getReadings().add(toRecommendedPlatformReadingType(reading));
      }
    }
    return type;
  }

  private DiscoveryCountrySummaryType toCountrySummaryType(DiscoveryCountrySummary country) {
    var type = new DiscoveryCountrySummaryType();
    type.setCountryCode(country.countryCode());
    type.setDisplayName(country.displayName());
    type.setTagline(country.tagline());
    type.setDescription(country.description());
    type.setDisplayOrder(country.displayOrder());
    type.setReadingCount(country.readingCount());

    if (country.heroImages() != null) {
      for (DiscoveryHeroImageSummary img : country.heroImages()) {
        var imgType = new DiscoveryHeroImageType();
        imgType.setAssetKey(img.assetKey());
        imgType.setLocation(img.location());
        imgType.setAlt(img.alt());
        imgType.setDisplayOrder(img.displayOrder());
        type.getHeroImages().add(imgType);
      }
    }

    if (country.topics() != null) {
      for (DiscoveryTopicSummary topic : country.topics()) {
        var topicType = new DiscoveryTopicSummaryType();
        topicType.setKey(topic.key());
        topicType.setDisplayName(topic.displayName());
        topicType.setDisplayOrder(topic.displayOrder());
        topicType.setReadingCount(topic.readingCount());
        type.getTopics().add(topicType);
      }
    }
    return type;
  }

  private DiscoveryShelfType toDiscoveryShelfType(DiscoveryShelfResult shelf) {
    var type = new DiscoveryShelfType();
    type.setKey(shelf.key());
    type.setTitle(shelf.title());
    type.setDescription(shelf.description());
    type.setDisplayOrder(shelf.displayOrder());
    type.setCoverKey(shelf.coverKey());
    type.setType(shelf.type());
    type.setTotalReadings(shelf.totalReadings());
    if (shelf.readings() != null) {
      for (RecommendedPlatformReading reading : shelf.readings()) {
        type.getReadings().add(toRecommendedPlatformReadingType(reading));
      }
    }
    return type;
  }
}
