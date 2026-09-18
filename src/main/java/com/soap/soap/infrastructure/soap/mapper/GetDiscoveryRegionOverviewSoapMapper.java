package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.DiscoveryCountrySummary;
import com.soap.soap.application.model.DiscoveryHeroImageSummary;
import com.soap.soap.application.model.DiscoveryRegionOverviewResult;
import com.soap.soap.application.model.DiscoveryTopicSummary;
import com.soap.soap.infrastructure.soap.generated.DiscoveryCountrySummaryType;
import com.soap.soap.infrastructure.soap.generated.DiscoveryHeroImageType;
import com.soap.soap.infrastructure.soap.generated.DiscoveryRegionDetailsType;
import com.soap.soap.infrastructure.soap.generated.DiscoveryTopicSummaryType;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryRegionOverviewResponse;
import org.springframework.stereotype.Component;

@Component
public class GetDiscoveryRegionOverviewSoapMapper {

  public GetDiscoveryRegionOverviewResponse toResponse(DiscoveryRegionOverviewResult result) {
    var response = new GetDiscoveryRegionOverviewResponse();

    var regionType = new DiscoveryRegionDetailsType();
    regionType.setKey(result.region().key());
    regionType.setDisplayName(result.region().displayName());
    regionType.setSubtitle(result.region().subtitle());
    response.setRegion(regionType);

    if (result.countries() != null) {
      for (DiscoveryCountrySummary country : result.countries()) {
        response.getCountries().add(toCountrySummaryType(country));
      }
    }

    return response;
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
}
