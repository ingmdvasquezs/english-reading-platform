package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.command.EditorialOptionCommand;
import com.soap.soap.application.command.EditorialQuestionCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.QuestionType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditorialQuizValidatorTest {

  private final EditorialQuizValidator validator = new EditorialQuizValidator();

  private static List<EditorialQuestionCommand> validCommands() {
    var list = new ArrayList<EditorialQuestionCommand>();
    QuestionType[] types = {
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA,
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA
    };
    for (int i = 1; i <= 6; i++) {
      var options =
          List.of(
              new EditorialOptionCommand(1, "Option A", true),
              new EditorialOptionCommand(2, "Option B", false),
              new EditorialOptionCommand(3, "Option C", false),
              new EditorialOptionCommand(4, "Option D", false));
      list.add(
          new EditorialQuestionCommand(
              i, types[i - 1], "Prompt " + i, "Explanation " + i, options));
    }
    return list;
  }

  @Test
  void acceptsValidSixQuestionCommandBank() {
    assertThatCode(() -> validator.validateCommands(validCommands())).doesNotThrowAnyException();
  }

  @Test
  void rejectsLessThanSixQuestions() {
    var commands = validCommands().subList(0, 5);
    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Quiz bank must contain exactly 6 questions");
  }

  @Test
  void rejectsMoreThanSixQuestions() {
    var commands = new ArrayList<>(validCommands());
    commands.add(
        new EditorialQuestionCommand(
            7,
            QuestionType.FACTUAL,
            "Extra prompt",
            "Extra exp",
            List.of(
                new EditorialOptionCommand(1, "A", true),
                new EditorialOptionCommand(2, "B", false),
                new EditorialOptionCommand(3, "C", false),
                new EditorialOptionCommand(4, "D", false))));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Quiz bank must contain exactly 6 questions");
  }

  @Test
  void rejectsWrongQuestionTypeForOrdinal() {
    var commands = new ArrayList<>(validCommands());
    // Ordinal 1 must be FACTUAL, change it to INFERENCE
    commands.set(
        0,
        new EditorialQuestionCommand(
            1, QuestionType.INFERENCE, "Prompt 1", "Explanation 1", commands.get(0).options()));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Question 1 must be of type FACTUAL");
  }

  @Test
  void rejectsDuplicateQuestionOrdinal() {
    var commands = new ArrayList<>(validCommands());
    commands.set(
        1,
        new EditorialQuestionCommand(
            1, QuestionType.FACTUAL, "Prompt 2", "Explanation 2", commands.get(1).options()));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Duplicate question ordinal: 1");
  }

  @Test
  void rejectsBlankPromptOrExplanation() {
    var commands = new ArrayList<>(validCommands());
    commands.set(
        0,
        new EditorialQuestionCommand(
            1, QuestionType.FACTUAL, "   ", "Exp", commands.get(0).options()));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("prompt must not be blank");

    commands.set(
        0,
        new EditorialQuestionCommand(
            1, QuestionType.FACTUAL, "Valid prompt", "", commands.get(0).options()));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("explanation must not be blank");
  }

  @Test
  void rejectsQuestionWithNotFourOptions() {
    var commands = new ArrayList<>(validCommands());
    commands.set(
        0,
        new EditorialQuestionCommand(
            1,
            QuestionType.FACTUAL,
            "Prompt 1",
            "Exp 1",
            List.of(
                new EditorialOptionCommand(1, "A", true),
                new EditorialOptionCommand(2, "B", false),
                new EditorialOptionCommand(3, "C", false))));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("must have exactly 4 options");
  }

  @Test
  void rejectsInvalidOptionOrdinals() {
    var commands = new ArrayList<>(validCommands());
    commands.set(
        0,
        new EditorialQuestionCommand(
            1,
            QuestionType.FACTUAL,
            "Prompt 1",
            "Exp 1",
            List.of(
                new EditorialOptionCommand(1, "A", true),
                new EditorialOptionCommand(2, "B", false),
                new EditorialOptionCommand(3, "C", false),
                new EditorialOptionCommand(5, "D", false))));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("option ordinal must be between 1 and 4");
  }

  @Test
  void rejectsZeroCorrectOptions() {
    var commands = new ArrayList<>(validCommands());
    commands.set(
        0,
        new EditorialQuestionCommand(
            1,
            QuestionType.FACTUAL,
            "Prompt 1",
            "Exp 1",
            List.of(
                new EditorialOptionCommand(1, "A", false),
                new EditorialOptionCommand(2, "B", false),
                new EditorialOptionCommand(3, "C", false),
                new EditorialOptionCommand(4, "D", false))));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("must have exactly 1 correct option, found: 0");
  }

  @Test
  void rejectsTwoCorrectOptions() {
    var commands = new ArrayList<>(validCommands());
    commands.set(
        0,
        new EditorialQuestionCommand(
            1,
            QuestionType.FACTUAL,
            "Prompt 1",
            "Exp 1",
            List.of(
                new EditorialOptionCommand(1, "A", true),
                new EditorialOptionCommand(2, "B", true),
                new EditorialOptionCommand(3, "C", false),
                new EditorialOptionCommand(4, "D", false))));

    assertThatThrownBy(() -> validator.validateCommands(commands))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("must have exactly 1 correct option, found: 2");
  }

  @Test
  void validatesDomainQuestionsEquivalently() {
    UUID readingId = UUID.randomUUID();
    var domainQuestions = new ArrayList<ComprehensionQuestion>();
    QuestionType[] types = {
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA,
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA
    };
    for (int i = 1; i <= 6; i++) {
      UUID qId = UUID.randomUUID();
      var options =
          List.of(
              new ComprehensionOption(UUID.randomUUID(), qId, 1, "A", true),
              new ComprehensionOption(UUID.randomUUID(), qId, 2, "B", false),
              new ComprehensionOption(UUID.randomUUID(), qId, 3, "C", false),
              new ComprehensionOption(UUID.randomUUID(), qId, 4, "D", false));
      domainQuestions.add(
          new ComprehensionQuestion(
              qId,
              readingId,
              i,
              types[i - 1],
              "Prompt " + i,
              "Explanation " + i,
              LocalDateTime.now(),
              options));
    }

    assertThatCode(() -> validator.validateDomainQuestions(domainQuestions))
        .doesNotThrowAnyException();
  }
}
