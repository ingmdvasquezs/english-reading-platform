package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.EditorialPublicationException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import com.soap.soap.application.port.in.PublishPlatformReadingPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.service.EditorialQuizValidator;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.RightsStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PublishPlatformReadingUseCase implements PublishPlatformReadingPort {

  private final ReadingRepositoryPort readings;
  private final ComprehensionQuizRepositoryPort comprehensionQuizzes;
  private final ReadingWordFrequencyRepositoryPort wordFrequencies;
  private final LanguageAvailabilityPolicy languageAvailabilityPolicy;
  private final EditorialQuizValidator quizValidator;

  @Override
  @Transactional
  public Reading publish(UUID readingId) {
    if (readingId == null) {
      throw new IllegalArgumentException("Reading ID must not be null");
    }

    var reading =
        readings.findById(readingId).orElseThrow(() -> new ReadingNotFoundException(readingId));

    if (reading.origin() != ReadingOrigin.PLATFORM) {
      throw new EditorialPublicationException(
          "Only PLATFORM readings can be published, found origin: " + reading.origin());
    }

    if (reading.editorialStatus() != EditorialStatus.DRAFT) {
      throw new EditorialPublicationException(
          "Only DRAFT readings can be published, current status: " + reading.editorialStatus());
    }

    if (reading.coverKey() == null || reading.coverKey().isBlank()) {
      throw new EditorialPublicationException("Cover key is required for publication");
    }

    if (reading.rightsStatus() == null || reading.rightsStatus() == RightsStatus.UNKNOWN) {
      throw new EditorialPublicationException(
          "Rights status must be confirmed (cannot be UNKNOWN) for publication");
    }

    languageAvailabilityPolicy.requireContentLanguageEnabled(reading.language());

    validateEditorialMetadata(reading);

    var quizOpt = comprehensionQuizzes.findByReadingId(readingId);
    if (quizOpt.isEmpty()) {
      throw new EditorialPublicationException(
          "Comprehension quiz bank is missing for reading: " + readingId);
    }
    quizValidator.validateDomainQuestions(quizOpt.get().questions());

    if (!wordFrequencies.existsByReadingId(readingId)) {
      throw new EditorialPublicationException(
          "Lexical word frequencies must be indexed before publication for reading: " + readingId);
    }

    var publishedReading =
        new Reading(
            reading.id(),
            reading.user(),
            reading.title(),
            reading.content(),
            reading.language(),
            reading.createdAt(),
            reading.origin(),
            reading.editorialLevel(),
            reading.category(),
            reading.coverKey(),
            EditorialStatus.PUBLISHED,
            reading.shortDescription(),
            reading.contentType(),
            reading.countryCode(),
            reading.region(),
            reading.sourceKind(),
            reading.rightsStatus(),
            reading.adaptationKind(),
            reading.sourceLanguage(),
            reading.sourceTitle(),
            reading.sourceAuthor(),
            reading.sourceUrl(),
            reading.sourceNotes(),
            reading.adaptationGroupKey(),
            reading.coverAttribution(),
            reading.accessTier(),
            reading.discoveryTopic());

    return readings.save(publishedReading);
  }

  private void validateEditorialMetadata(Reading reading) {
    if (reading.content() == null || reading.content().isBlank()) {
      throw new EditorialPublicationException("Reading content must not be blank");
    }
    if (reading.editorialLevel() == null) {
      throw new EditorialPublicationException("Editorial level must not be null");
    }
    if (reading.category() == null || reading.category().isBlank()) {
      throw new EditorialPublicationException("Category must not be blank");
    }
    if (reading.shortDescription() == null || reading.shortDescription().isBlank()) {
      throw new EditorialPublicationException("Short description must not be blank");
    }
    if (reading.contentType() == null) {
      throw new EditorialPublicationException("Content type must not be null");
    }
    if (reading.region() == null) {
      throw new EditorialPublicationException("Region must not be null");
    }
    if (reading.sourceKind() == null) {
      throw new EditorialPublicationException("Source kind must not be null");
    }
    if (reading.adaptationKind() == null) {
      throw new EditorialPublicationException("Adaptation kind must not be null");
    }
    if (reading.adaptationKind() == AdaptationKind.TRANSLATED_ADAPTATION
        && reading.sourceLanguage() == null) {
      throw new EditorialPublicationException("Translated adaptation requires source language");
    }
    if (reading.accessTier() == null) {
      throw new EditorialPublicationException("Access tier must not be null");
    }
  }
}
