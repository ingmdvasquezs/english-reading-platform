package com.soap.soap.application.service;

import com.soap.soap.application.command.EditorialOptionCommand;
import com.soap.soap.application.command.EditorialQuestionCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.QuestionType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Shared validation service for editorial comprehension quiz banks.
 *
 * <p>Enforces:
 *
 * <ul>
 *   <li>Exactly 6 questions with strictly specified ordinals and types: 1 = FACTUAL, 2 = INFERENCE,
 *       3 = MAIN_IDEA, 4 = FACTUAL, 5 = INFERENCE, 6 = MAIN_IDEA
 *   <li>Per question: non-blank prompt, non-blank explanation
 *   <li>Per question: exactly 4 options with ordinals 1, 2, 3, 4 (no duplicates, no gaps)
 *   <li>Per question: non-blank option content
 *   <li>Per question: exactly ONE correct option (rejects 0 or 2+ correct answers)
 * </ul>
 */
@Service
public class EditorialQuizValidator {

  private static final Map<Integer, QuestionType> REQUIRED_TYPES_BY_ORDINAL =
      Map.of(
          1, QuestionType.FACTUAL,
          2, QuestionType.INFERENCE,
          3, QuestionType.MAIN_IDEA,
          4, QuestionType.FACTUAL,
          5, QuestionType.INFERENCE,
          6, QuestionType.MAIN_IDEA);

  public void validateCommands(List<EditorialQuestionCommand> questions) {
    if (questions == null || questions.size() != 6) {
      throw new InvalidApplicationArgumentException(
          "Quiz bank must contain exactly 6 questions, found: "
              + (questions == null ? 0 : questions.size()));
    }

    Set<Integer> seenOrdinals = new HashSet<>();
    for (var q : questions) {
      if (q == null) {
        throw new InvalidApplicationArgumentException("Quiz question must not be null");
      }
      int ordinal = q.ordinal();
      if (!REQUIRED_TYPES_BY_ORDINAL.containsKey(ordinal)) {
        throw new InvalidApplicationArgumentException(
            "Question ordinal must be between 1 and 6, found: " + ordinal);
      }
      if (!seenOrdinals.add(ordinal)) {
        throw new InvalidApplicationArgumentException("Duplicate question ordinal: " + ordinal);
      }
      var expectedType = REQUIRED_TYPES_BY_ORDINAL.get(ordinal);
      if (q.questionType() != expectedType) {
        throw new InvalidApplicationArgumentException(
            "Question "
                + ordinal
                + " must be of type "
                + expectedType
                + ", found: "
                + q.questionType());
      }
      if (q.prompt() == null || q.prompt().isBlank()) {
        throw new InvalidApplicationArgumentException(
            "Question " + ordinal + " prompt must not be blank");
      }
      if (q.explanation() == null || q.explanation().isBlank()) {
        throw new InvalidApplicationArgumentException(
            "Question " + ordinal + " explanation must not be blank");
      }
      validateCommandOptions(ordinal, q.options());
    }

    if (seenOrdinals.size() != 6) {
      throw new InvalidApplicationArgumentException(
          "Quiz bank must contain all ordinals from 1 to 6");
    }
  }

  private void validateCommandOptions(int questionOrdinal, List<EditorialOptionCommand> options) {
    if (options == null || options.size() != 4) {
      throw new InvalidApplicationArgumentException(
          "Question "
              + questionOrdinal
              + " must have exactly 4 options, found: "
              + (options == null ? 0 : options.size()));
    }

    Set<Integer> seenOptionOrdinals = new HashSet<>();
    int correctCount = 0;

    for (var opt : options) {
      if (opt == null) {
        throw new InvalidApplicationArgumentException(
            "Question " + questionOrdinal + " option must not be null");
      }
      int optOrdinal = opt.ordinal();
      if (optOrdinal < 1 || optOrdinal > 4) {
        throw new InvalidApplicationArgumentException(
            "Question "
                + questionOrdinal
                + " option ordinal must be between 1 and 4, found: "
                + optOrdinal);
      }
      if (!seenOptionOrdinals.add(optOrdinal)) {
        throw new InvalidApplicationArgumentException(
            "Question " + questionOrdinal + " has duplicate option ordinal: " + optOrdinal);
      }
      if (opt.content() == null || opt.content().isBlank()) {
        throw new InvalidApplicationArgumentException(
            "Question " + questionOrdinal + " option " + optOrdinal + " content must not be blank");
      }
      if (opt.isCorrect()) {
        correctCount++;
      }
    }

    if (seenOptionOrdinals.size() != 4) {
      throw new InvalidApplicationArgumentException(
          "Question " + questionOrdinal + " must contain option ordinals 1, 2, 3, and 4");
    }
    if (correctCount != 1) {
      throw new InvalidApplicationArgumentException(
          "Question "
              + questionOrdinal
              + " must have exactly 1 correct option, found: "
              + correctCount);
    }
  }

  public void validateDomainQuestions(List<ComprehensionQuestion> questions) {
    if (questions == null || questions.size() != 6) {
      throw new InvalidApplicationArgumentException(
          "Quiz bank must contain exactly 6 questions, found: "
              + (questions == null ? 0 : questions.size()));
    }

    Set<Integer> seenOrdinals = new HashSet<>();
    for (var q : questions) {
      if (q == null) {
        throw new InvalidApplicationArgumentException("Quiz question must not be null");
      }
      int ordinal = q.ordinal();
      if (!REQUIRED_TYPES_BY_ORDINAL.containsKey(ordinal)) {
        throw new InvalidApplicationArgumentException(
            "Question ordinal must be between 1 and 6, found: " + ordinal);
      }
      if (!seenOrdinals.add(ordinal)) {
        throw new InvalidApplicationArgumentException("Duplicate question ordinal: " + ordinal);
      }
      var expectedType = REQUIRED_TYPES_BY_ORDINAL.get(ordinal);
      if (q.questionType() != expectedType) {
        throw new InvalidApplicationArgumentException(
            "Question "
                + ordinal
                + " must be of type "
                + expectedType
                + ", found: "
                + q.questionType());
      }
      if (q.prompt() == null || q.prompt().isBlank()) {
        throw new InvalidApplicationArgumentException(
            "Question " + ordinal + " prompt must not be blank");
      }
      if (q.explanation() == null || q.explanation().isBlank()) {
        throw new InvalidApplicationArgumentException(
            "Question " + ordinal + " explanation must not be blank");
      }
      validateDomainOptions(ordinal, q.options());
    }

    if (seenOrdinals.size() != 6) {
      throw new InvalidApplicationArgumentException(
          "Quiz bank must contain all ordinals from 1 to 6");
    }
  }

  private void validateDomainOptions(int questionOrdinal, List<ComprehensionOption> options) {
    if (options == null || options.size() != 4) {
      throw new InvalidApplicationArgumentException(
          "Question "
              + questionOrdinal
              + " must have exactly 4 options, found: "
              + (options == null ? 0 : options.size()));
    }

    Set<Integer> seenOptionOrdinals = new HashSet<>();
    int correctCount = 0;

    for (var opt : options) {
      if (opt == null) {
        throw new InvalidApplicationArgumentException(
            "Question " + questionOrdinal + " option must not be null");
      }
      int optOrdinal = opt.ordinal();
      if (optOrdinal < 1 || optOrdinal > 4) {
        throw new InvalidApplicationArgumentException(
            "Question "
                + questionOrdinal
                + " option ordinal must be between 1 and 4, found: "
                + optOrdinal);
      }
      if (!seenOptionOrdinals.add(optOrdinal)) {
        throw new InvalidApplicationArgumentException(
            "Question " + questionOrdinal + " has duplicate option ordinal: " + optOrdinal);
      }
      if (opt.content() == null || opt.content().isBlank()) {
        throw new InvalidApplicationArgumentException(
            "Question " + questionOrdinal + " option " + optOrdinal + " content must not be blank");
      }
      if (opt.isCorrect()) {
        correctCount++;
      }
    }

    if (seenOptionOrdinals.size() != 4) {
      throw new InvalidApplicationArgumentException(
          "Question " + questionOrdinal + " must contain option ordinals 1, 2, 3, and 4");
    }
    if (correctCount != 1) {
      throw new InvalidApplicationArgumentException(
          "Question "
              + questionOrdinal
              + " must have exactly 1 correct option, found: "
              + correctCount);
    }
  }
}
