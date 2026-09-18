package com.soap.soap.application.usecase;

import com.soap.soap.application.command.EditorialQuestionCommand;
import com.soap.soap.application.command.IngestEditorialReadingCommand;
import com.soap.soap.application.exception.EditorialIngestionException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.IngestEditorialReadingResult;
import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import com.soap.soap.application.port.in.IngestEditorialReadingPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.service.EditorialQuizValidator;
import com.soap.soap.application.service.ReadingLexicalIndexer;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IngestEditorialReadingUseCase implements IngestEditorialReadingPort {

  private final ReadingRepositoryPort readings;
  private final ComprehensionQuizRepositoryPort comprehensionQuizzes;
  private final ReadingLexicalIndexer lexicalIndexer;
  private final LanguageAvailabilityPolicy languageAvailabilityPolicy;
  private final EditorialQuizValidator quizValidator;

  @Override
  @Transactional
  public IngestEditorialReadingResult ingest(IngestEditorialReadingCommand command) {
    if (command == null) {
      throw new InvalidApplicationArgumentException("Ingestion command must not be null");
    }

    validateMetadata(command);

    var languageTag = LanguageTag.of(command.language());
    languageAvailabilityPolicy.requireContentLanguageEnabled(languageTag);

    quizValidator.validateCommands(command.questions());

    var existingOpt =
        readings.findPlatformReadingByAdaptationKey(
            command.adaptationGroupKey().trim(), languageTag.value(), command.editorialLevel());

    UUID targetReadingId = null;
    LocalDateTime targetCreatedAt = null;
    boolean created = true;

    if (existingOpt.isPresent()) {
      var existing = existingOpt.get();
      if (existing.editorialStatus() == EditorialStatus.PUBLISHED) {
        throw new EditorialIngestionException(
            "Reading is already PUBLISHED and cannot be overwritten by manifest ingestion: "
                + command.adaptationGroupKey());
      }
      if (existing.editorialStatus() == EditorialStatus.ARCHIVED) {
        throw new EditorialIngestionException(
            "Reading is ARCHIVED and cannot be overwritten by manifest ingestion: "
                + command.adaptationGroupKey());
      }
      targetReadingId = existing.id();
      targetCreatedAt = existing.createdAt();
      created = false;
    }

    LanguageTag sourceLanguageTag =
        command.sourceLanguage() != null && !command.sourceLanguage().isBlank()
            ? LanguageTag.of(command.sourceLanguage())
            : null;

    String coverKey =
        command.coverKey() != null && !command.coverKey().isBlank()
            ? command.coverKey().trim()
            : null;

    var readingToSave =
        new Reading(
            targetReadingId,
            null, // user MUST be null for PLATFORM
            command.title().trim(),
            command.content(),
            languageTag,
            targetCreatedAt != null ? targetCreatedAt : LocalDateTime.now(),
            ReadingOrigin.PLATFORM, // origin MUST be PLATFORM
            command.editorialLevel(),
            command.category().trim(),
            coverKey,
            EditorialStatus.DRAFT, // Always DRAFT on ingestion
            command.shortDescription().trim(),
            command.contentType(),
            command.countryCode() != null ? command.countryCode().trim() : null,
            command.region(),
            command.sourceKind(),
            command.rightsStatus(),
            command.adaptationKind(),
            sourceLanguageTag,
            command.sourceTitle(),
            command.sourceAuthor(),
            command.sourceUrl(),
            command.sourceNotes(),
            command.adaptationGroupKey().trim(),
            command.coverAttribution(),
            command.accessTier(),
            command.discoveryTopic());

    var savedReading = readings.saveAndFlush(readingToSave);

    var domainQuestions = toDomainQuestions(savedReading.id(), command.questions());
    comprehensionQuizzes.replaceQuestions(savedReading.id(), domainQuestions);

    lexicalIndexer.indexReading(savedReading.id(), languageTag.value(), savedReading.content());

    return new IngestEditorialReadingResult(
        savedReading.id(), created, EditorialStatus.DRAFT, domainQuestions.size());
  }

  private void validateMetadata(IngestEditorialReadingCommand command) {
    if (command.title() == null || command.title().isBlank()) {
      throw new InvalidApplicationArgumentException("Title must not be blank");
    }
    if (command.content() == null || command.content().isBlank()) {
      throw new InvalidApplicationArgumentException("Content must not be blank");
    }
    if (command.language() == null || command.language().isBlank()) {
      throw new InvalidApplicationArgumentException("Language must not be blank");
    }
    if (command.editorialLevel() == null) {
      throw new InvalidApplicationArgumentException("Editorial level must not be null");
    }
    if (command.category() == null || command.category().isBlank()) {
      throw new InvalidApplicationArgumentException("Category must not be blank");
    }
    if (command.shortDescription() == null || command.shortDescription().isBlank()) {
      throw new InvalidApplicationArgumentException("Short description must not be blank");
    }
    if (command.contentType() == null) {
      throw new InvalidApplicationArgumentException("Content type must not be null");
    }
    if (command.region() == null) {
      throw new InvalidApplicationArgumentException("Region must not be null");
    }
    if (command.sourceKind() == null) {
      throw new InvalidApplicationArgumentException("Source kind must not be null");
    }
    if (command.rightsStatus() == null) {
      throw new InvalidApplicationArgumentException("Rights status must not be null");
    }
    if (command.adaptationKind() == null) {
      throw new InvalidApplicationArgumentException("Adaptation kind must not be null");
    }
    if (command.accessTier() == null) {
      throw new InvalidApplicationArgumentException("Access tier must not be null");
    }
    if (command.adaptationGroupKey() == null || command.adaptationGroupKey().isBlank()) {
      throw new InvalidApplicationArgumentException(
          "adaptationGroupKey is required for editorial ingestion");
    }
    if (command.adaptationKind() == AdaptationKind.TRANSLATED_ADAPTATION) {
      if (command.sourceLanguage() == null || command.sourceLanguage().isBlank()) {
        throw new InvalidApplicationArgumentException(
            "Translated adaptation requires source language");
      }
    }
    if (command.countryCode() != null && !command.countryCode().isBlank()) {
      if (!command.countryCode().matches("^[A-Z]{2}$")) {
        throw new InvalidApplicationArgumentException(
            "Country code must be 2 uppercase ISO letters: " + command.countryCode());
      }
    }
  }

  private List<ComprehensionQuestion> toDomainQuestions(
      UUID readingId, List<EditorialQuestionCommand> questionCommands) {
    var now = LocalDateTime.now();
    var questions = new ArrayList<ComprehensionQuestion>();

    for (var qc : questionCommands) {
      var questionId = UUID.randomUUID();
      var options = new ArrayList<ComprehensionOption>();
      for (var oc : qc.options()) {
        options.add(
            new ComprehensionOption(
                UUID.randomUUID(), questionId, oc.ordinal(), oc.content().trim(), oc.isCorrect()));
      }
      questions.add(
          new ComprehensionQuestion(
              questionId,
              readingId,
              qc.ordinal(),
              qc.questionType(),
              qc.prompt().trim(),
              qc.explanation().trim(),
              now,
              options));
    }
    return questions;
  }
}
