package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.soap.generated.AccessTierType;
import com.soap.soap.infrastructure.soap.generated.AdaptationKindType;
import com.soap.soap.infrastructure.soap.generated.EditorialContentTypeType;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.GetReadingRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingResponse;
import com.soap.soap.infrastructure.soap.generated.ReadingOriginType;
import com.soap.soap.infrastructure.soap.generated.ReadingType;
import com.soap.soap.infrastructure.soap.generated.RegionType;
import com.soap.soap.infrastructure.soap.generated.RightsStatusType;
import com.soap.soap.infrastructure.soap.generated.SourceKindType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetReadingSoapMapper extends SoapMapperSupport {
  public UUID toReadingId(GetReadingRequest request) {
    return parseUuid(request.getReadingId(), "readingId");
  }

  public GetReadingResponse toResponse(Reading reading) {
    var response = new GetReadingResponse();
    response.setReading(toReadingType(reading));
    return response;
  }

  ReadingType toReadingType(Reading reading) {
    var result = new ReadingType();
    result.setReadingId(reading.id().toString());
    if (reading.user() != null) {
      result.setUserId(reading.user().id().toString());
    }
    result.setTitle(reading.title());
    result.setContent(reading.content());
    result.setLanguage(reading.language() == null ? null : reading.language().value());
    result.setOrigin(ReadingOriginType.valueOf(reading.origin().name()));
    if (reading.editorialLevel() != null) {
      result.setEditorialLevel(EditorialLevelType.fromValue(reading.editorialLevel().name()));
    }
    result.setCategory(reading.category());
    result.setCreatedAt(toXmlDate(reading.createdAt()));
    result.setCoverKey(reading.coverKey());
    result.setShortDescription(reading.shortDescription());
    if (reading.contentType() != null) {
      result.setContentType(EditorialContentTypeType.fromValue(reading.contentType().name()));
    }
    result.setCountryCode(reading.countryCode());
    if (reading.region() != null) {
      result.setRegion(RegionType.fromValue(reading.region().name()));
    }
    if (reading.sourceKind() != null) {
      result.setSourceKind(SourceKindType.fromValue(reading.sourceKind().name()));
    }
    if (reading.rightsStatus() != null) {
      result.setRightsStatus(RightsStatusType.fromValue(reading.rightsStatus().name()));
    }
    if (reading.adaptationKind() != null) {
      result.setAdaptationKind(AdaptationKindType.fromValue(reading.adaptationKind().name()));
    }
    result.setSourceLanguage(
        reading.sourceLanguage() == null ? null : reading.sourceLanguage().value());
    result.setSourceTitle(reading.sourceTitle());
    result.setSourceAuthor(reading.sourceAuthor());
    result.setSourceUrl(reading.sourceUrl());
    result.setSourceNotes(reading.sourceNotes());
    result.setAdaptationGroupKey(reading.adaptationGroupKey());
    result.setCoverAttribution(reading.coverAttribution());
    if (reading.accessTier() != null) {
      result.setAccessTier(AccessTierType.fromValue(reading.accessTier().name()));
    }
    return result;
  }
}
