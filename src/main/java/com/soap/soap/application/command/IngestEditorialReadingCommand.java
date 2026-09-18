package com.soap.soap.application.command;

import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import java.util.List;

public record IngestEditorialReadingCommand(
    String title,
    String content,
    String language,
    EditorialLevel editorialLevel,
    String category,
    String shortDescription,
    EditorialContentType contentType,
    String countryCode,
    EditorialRegion region,
    SourceKind sourceKind,
    RightsStatus rightsStatus,
    AdaptationKind adaptationKind,
    String sourceLanguage,
    String sourceTitle,
    String sourceAuthor,
    String sourceUrl,
    String sourceNotes,
    String adaptationGroupKey,
    String coverKey,
    String coverAttribution,
    AccessTier accessTier,
    DiscoveryTopic discoveryTopic,
    List<EditorialQuestionCommand> questions) {

  public IngestEditorialReadingCommand(
      String title,
      String content,
      String language,
      EditorialLevel editorialLevel,
      String category,
      String shortDescription,
      EditorialContentType contentType,
      String countryCode,
      EditorialRegion region,
      SourceKind sourceKind,
      RightsStatus rightsStatus,
      AdaptationKind adaptationKind,
      String sourceLanguage,
      String sourceTitle,
      String sourceAuthor,
      String sourceUrl,
      String sourceNotes,
      String adaptationGroupKey,
      String coverKey,
      String coverAttribution,
      AccessTier accessTier,
      List<EditorialQuestionCommand> questions) {
    this(
        title,
        content,
        language,
        editorialLevel,
        category,
        shortDescription,
        contentType,
        countryCode,
        region,
        sourceKind,
        rightsStatus,
        adaptationKind,
        sourceLanguage,
        sourceTitle,
        sourceAuthor,
        sourceUrl,
        sourceNotes,
        adaptationGroupKey,
        coverKey,
        coverAttribution,
        accessTier,
        null,
        questions);
  }
}
