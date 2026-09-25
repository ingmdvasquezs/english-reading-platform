package com.soap.soap.application.usecase;

import com.soap.soap.application.command.EditorialQuestionCommand;
import com.soap.soap.application.command.UpdatePublishedEditorialContentCommand;
import com.soap.soap.application.exception.EditorialContentUpdateException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.UpdatePublishedEditorialContentResult;
import com.soap.soap.application.port.in.UpdatePublishedEditorialContentPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.service.EditorialQuizValidator;
import com.soap.soap.application.service.ReadingLexicalIndexer;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.ReadingOrigin;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdatePublishedEditorialContentUseCase implements UpdatePublishedEditorialContentPort {

  private final ReadingRepositoryPort readings;
  private final ComprehensionQuizRepositoryPort comprehensionQuizzes;
  private final ReadingWordFrequencyRepositoryPort wordFrequencyRepository;
  private final ReadingLexicalIndexer lexicalIndexer;
  private final EditorialQuizValidator quizValidator;
  private final Clock clock;

  @Override
  @Transactional
  public UpdatePublishedEditorialContentResult updateContent(
      UpdatePublishedEditorialContentCommand command) {
    if (command == null) {
      throw new InvalidApplicationArgumentException("Command must not be null");
    }
    if (command.adaptationGroupKey() == null || command.adaptationGroupKey().isBlank()) {
      throw new InvalidApplicationArgumentException("adaptationGroupKey must not be blank");
    }
    if (command.language() == null || command.language().isBlank()) {
      throw new InvalidApplicationArgumentException("Language must not be blank");
    }
    if (command.editorialLevel() == null) {
      throw new InvalidApplicationArgumentException("Editorial level must not be null");
    }
    if (command.content() == null || command.content().isBlank()) {
      throw new InvalidApplicationArgumentException("Content must not be blank");
    }

    boolean quizUpdated = false;
    if (command.questions() != null) {
      if (command.questions().isEmpty()) {
        throw new InvalidApplicationArgumentException(
            "Comprehension quiz questions list cannot be empty when supplied; omit to preserve existing quiz");
      }
      quizValidator.validateCommands(command.questions());
      quizUpdated = true;
    }

    var languageTag = LanguageTag.of(command.language());
    var existingOpt =
        readings.findPlatformReadingByAdaptationKey(
            command.adaptationGroupKey().trim(), languageTag.value(), command.editorialLevel());

    if (existingOpt.isEmpty()) {
      throw new EditorialContentUpdateException(
          "Reading not found for adaptationGroupKey: "
              + command.adaptationGroupKey()
              + ", language: "
              + command.language()
              + ", level: "
              + command.editorialLevel());
    }

    var reading = existingOpt.get();

    if (reading.origin() != ReadingOrigin.PLATFORM) {
      throw new EditorialContentUpdateException(
          "Only PLATFORM readings can be updated via this use case (found: "
              + reading.origin()
              + ")");
    }

    if (reading.editorialStatus() != EditorialStatus.PUBLISHED) {
      throw new EditorialContentUpdateException(
          "Reading is not PUBLISHED (current status: "
              + reading.editorialStatus()
              + "). Only PUBLISHED readings may be updated.");
    }

    String normalizedExisting = normalizeTechnicalFormatting(reading.content());
    String normalizedNew = normalizeTechnicalFormatting(command.content());
    boolean contentUpdated = !normalizedExisting.equals(normalizedNew);

    if (contentUpdated) {
      var updatedReading = reading.withContent(command.content());
      readings.saveAndFlush(updatedReading);
      lexicalIndexer.indexReading(
          updatedReading.id(), updatedReading.language().value(), updatedReading.content());
    }

    if (quizUpdated) {
      var domainQuestions = toDomainQuestions(reading.id(), command.questions());
      comprehensionQuizzes.replaceQuestions(reading.id(), domainQuestions);
    }

    int lexicalFrequencyCount =
        wordFrequencyRepository.findFrequenciesByReadingId(reading.id()).size();

    return new UpdatePublishedEditorialContentResult(
        reading.id(),
        command.adaptationGroupKey().trim(),
        contentUpdated,
        quizUpdated,
        lexicalFrequencyCount);
  }

  static String normalizeTechnicalFormatting(String text) {
    if (text == null) {
      return "";
    }
    String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
    String[] lines = normalized.split("\n", -1);
    var sb = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      sb.append(lines[i].stripTrailing());
      if (i < lines.length - 1) {
        sb.append("\n");
      }
    }
    String res = sb.toString();
    while (res.endsWith("\n")) {
      res = res.substring(0, res.length() - 1);
    }
    return res;
  }

  private List<ComprehensionQuestion> toDomainQuestions(
      UUID readingId, List<EditorialQuestionCommand> questionCommands) {
    var now = LocalDateTime.now(clock);
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
