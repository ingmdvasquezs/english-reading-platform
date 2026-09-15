package com.soap.soap.infrastructure.editorial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EditorialManifestDto(
    @JsonProperty("schemaVersion") Integer schemaVersion,
    @JsonProperty("reading") ReadingMetadataDto reading,
    // Top-level fallbacks if reading block is not nested
    @JsonProperty("title") String title,
    @JsonProperty("content") String content,
    @JsonProperty("language") String language,
    @JsonProperty("editorialLevel") String editorialLevel,
    @JsonProperty("category") String category,
    @JsonProperty("shortDescription") String shortDescription,
    @JsonProperty("contentType") String contentType,
    @JsonProperty("countryCode") String countryCode,
    @JsonProperty("region") String region,
    @JsonProperty("sourceKind") String sourceKind,
    @JsonProperty("rightsStatus") String rightsStatus,
    @JsonProperty("adaptationKind") String adaptationKind,
    @JsonProperty("sourceLanguage") String sourceLanguage,
    @JsonProperty("sourceTitle") String sourceTitle,
    @JsonProperty("sourceAuthor") String sourceAuthor,
    @JsonProperty("sourceUrl") String sourceUrl,
    @JsonProperty("sourceNotes") String sourceNotes,
    @JsonProperty("adaptationGroupKey") String adaptationGroupKey,
    @JsonProperty("coverKey") String coverKey,
    @JsonProperty("coverAttribution") String coverAttribution,
    @JsonProperty("accessTier") String accessTier,
    @JsonProperty("comprehensionQuiz") List<QuestionDto> comprehensionQuiz) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ReadingMetadataDto(
      @JsonProperty("title") String title,
      @JsonProperty("content") String content,
      @JsonProperty("language") String language,
      @JsonProperty("editorialLevel") String editorialLevel,
      @JsonProperty("category") String category,
      @JsonProperty("shortDescription") String shortDescription,
      @JsonProperty("contentType") String contentType,
      @JsonProperty("countryCode") String countryCode,
      @JsonProperty("region") String region,
      @JsonProperty("sourceKind") String sourceKind,
      @JsonProperty("rightsStatus") String rightsStatus,
      @JsonProperty("adaptationKind") String adaptationKind,
      @JsonProperty("sourceLanguage") String sourceLanguage,
      @JsonProperty("sourceTitle") String sourceTitle,
      @JsonProperty("sourceAuthor") String sourceAuthor,
      @JsonProperty("sourceUrl") String sourceUrl,
      @JsonProperty("sourceNotes") String sourceNotes,
      @JsonProperty("adaptationGroupKey") String adaptationGroupKey,
      @JsonProperty("coverKey") String coverKey,
      @JsonProperty("coverAttribution") String coverAttribution,
      @JsonProperty("accessTier") String accessTier) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record QuestionDto(
      @JsonProperty("ordinal") Integer ordinal,
      @JsonProperty("questionType") String questionType,
      @JsonProperty("prompt") String prompt,
      @JsonProperty("explanation") String explanation,
      @JsonProperty("options") List<OptionDto> options) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OptionDto(
      @JsonProperty("ordinal") Integer ordinal,
      @JsonProperty("content") String content,
      @JsonProperty("isCorrect") Boolean isCorrect) {}
}
