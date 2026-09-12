package com.soap.soap.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.QuestionType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComprehensionQuizSelectionPolicyTest {

  private ComprehensionQuizSelectionPolicy policy;
  private UUID userId;
  private UUID readingId;
  private UUID submissionId;

  @BeforeEach
  void setUp() {
    policy = new ComprehensionQuizSelectionPolicy();
    userId = UUID.randomUUID();
    readingId = UUID.randomUUID();
    submissionId = UUID.randomUUID();
  }

  private ComprehensionQuestion createQuestion(UUID readingId, int ordinal, QuestionType type) {
    UUID qId = UUID.randomUUID();
    return new ComprehensionQuestion(
        qId,
        readingId,
        ordinal,
        type,
        "Prompt for " + type + " ordinal " + ordinal,
        "Explanation for " + ordinal,
        LocalDateTime.now(),
        List.of(
            new ComprehensionOption(UUID.randomUUID(), qId, 1, "Opt 1", true),
            new ComprehensionOption(UUID.randomUUID(), qId, 2, "Opt 2", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 3, "Opt 3", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 4, "Opt 4", false)));
  }

  private ComprehensionQuiz createV25QuizBank(UUID readingId) {
    return new ComprehensionQuiz(
        readingId,
        List.of(
            createQuestion(readingId, 1, QuestionType.FACTUAL),
            createQuestion(readingId, 2, QuestionType.INFERENCE),
            createQuestion(readingId, 3, QuestionType.MAIN_IDEA)));
  }

  private ComprehensionQuiz createV26SixQuestionBank(UUID readingId) {
    return new ComprehensionQuiz(
        readingId,
        List.of(
            createQuestion(readingId, 1, QuestionType.FACTUAL),
            createQuestion(readingId, 2, QuestionType.INFERENCE),
            createQuestion(readingId, 3, QuestionType.MAIN_IDEA),
            createQuestion(readingId, 4, QuestionType.FACTUAL),
            createQuestion(readingId, 5, QuestionType.INFERENCE),
            createQuestion(readingId, 6, QuestionType.MAIN_IDEA)));
  }

  @Test
  void selectionIsDeterministicForSameInput() {
    var quiz = createV26SixQuestionBank(readingId);

    var selection1 = policy.select(quiz, userId, readingId, submissionId, 1);
    var selection2 = policy.select(quiz, userId, readingId, submissionId, 1);

    assertThat(selection1).hasSize(3);
    assertThat(selection2).hasSize(3);
    for (int i = 0; i < 3; i++) {
      assertThat(selection1.get(i).id()).isEqualTo(selection2.get(i).id());
      assertThat(selection1.get(i).ordinal()).isEqualTo(selection2.get(i).ordinal());
      assertThat(selection1.get(i).questionType()).isEqualTo(selection2.get(i).questionType());
    }
  }

  @Test
  void selectionOutputHasCanonicalDisplayOrdinals123() {
    var quiz = createV25QuizBank(readingId);

    var selected = policy.select(quiz, userId, readingId, submissionId, 1);

    assertThat(selected).hasSize(3);
    assertThat(selected.get(0).ordinal()).isEqualTo(1);
    assertThat(selected.get(0).questionType()).isEqualTo(QuestionType.FACTUAL);
    assertThat(selected.get(1).ordinal()).isEqualTo(2);
    assertThat(selected.get(1).questionType()).isEqualTo(QuestionType.INFERENCE);
    assertThat(selected.get(2).ordinal()).isEqualTo(3);
    assertThat(selected.get(2).questionType()).isEqualTo(QuestionType.MAIN_IDEA);
  }

  @Test
  void selectionIgnoresInputOrderOfQuestionsInQuiz() {
    var questions =
        new ArrayList<>(
            List.of(
                createQuestion(readingId, 1, QuestionType.FACTUAL),
                createQuestion(readingId, 2, QuestionType.INFERENCE),
                createQuestion(readingId, 3, QuestionType.MAIN_IDEA),
                createQuestion(readingId, 4, QuestionType.FACTUAL),
                createQuestion(readingId, 5, QuestionType.INFERENCE),
                createQuestion(readingId, 6, QuestionType.MAIN_IDEA)));

    var quizForward = new ComprehensionQuiz(readingId, questions);
    var selectedForward = policy.select(quizForward, userId, readingId, submissionId, 2);

    Collections.reverse(questions);
    var quizReversed = new ComprehensionQuiz(readingId, questions);
    var selectedReversed = policy.select(quizReversed, userId, readingId, submissionId, 2);

    assertThat(selectedForward).hasSize(3);
    assertThat(selectedReversed).hasSize(3);
    for (int i = 0; i < 3; i++) {
      assertThat(selectedForward.get(i).id()).isEqualTo(selectedReversed.get(i).id());
    }
  }

  @Test
  void version1IgnoresOrdinalsGreaterThan3() {
    var q1 = createQuestion(readingId, 1, QuestionType.FACTUAL);
    var q2 = createQuestion(readingId, 2, QuestionType.INFERENCE);
    var q3 = createQuestion(readingId, 3, QuestionType.MAIN_IDEA);
    var q4 = createQuestion(readingId, 4, QuestionType.FACTUAL);
    var q5 = createQuestion(readingId, 5, QuestionType.INFERENCE);
    var q6 = createQuestion(readingId, 6, QuestionType.MAIN_IDEA);

    var quiz = new ComprehensionQuiz(readingId, List.of(q1, q2, q3, q4, q5, q6));

    // Even across 50 different submission IDs, VERSION_1 must always select q1, q2, q3
    for (int i = 0; i < 50; i++) {
      UUID randomSubId = UUID.randomUUID();
      var selected = policy.select(quiz, userId, readingId, randomSubId, 1);
      assertThat(selected.get(0).id()).isEqualTo(q1.id());
      assertThat(selected.get(1).id()).isEqualTo(q2.id());
      assertThat(selected.get(2).id()).isEqualTo(q3.id());
    }
  }

  @Test
  void version2CanSelectQuestionsFromFullBankOf6Questions() {
    var q1 = createQuestion(readingId, 1, QuestionType.FACTUAL);
    var q2 = createQuestion(readingId, 2, QuestionType.INFERENCE);
    var q3 = createQuestion(readingId, 3, QuestionType.MAIN_IDEA);
    var q4 = createQuestion(readingId, 4, QuestionType.FACTUAL);
    var q5 = createQuestion(readingId, 5, QuestionType.INFERENCE);
    var q6 = createQuestion(readingId, 6, QuestionType.MAIN_IDEA);

    var quiz = new ComprehensionQuiz(readingId, List.of(q1, q2, q3, q4, q5, q6));

    Set<UUID> chosenQuestionIds = new HashSet<>();
    Set<String> distinctTrios = new HashSet<>();

    for (int i = 0; i < 100; i++) {
      UUID randomSubId = UUID.randomUUID();
      var selected = policy.select(quiz, userId, readingId, randomSubId, 2);
      assertThat(selected).hasSize(3);
      assertThat(selected.get(0).questionType()).isEqualTo(QuestionType.FACTUAL);
      assertThat(selected.get(1).questionType()).isEqualTo(QuestionType.INFERENCE);
      assertThat(selected.get(2).questionType()).isEqualTo(QuestionType.MAIN_IDEA);

      chosenQuestionIds.add(selected.get(0).id());
      chosenQuestionIds.add(selected.get(1).id());
      chosenQuestionIds.add(selected.get(2).id());

      distinctTrios.add(
          selected.get(0).id() + "|" + selected.get(1).id() + "|" + selected.get(2).id());
    }

    // Both candidate questions for each type must be chosen across iterations
    assertThat(chosenQuestionIds).contains(q1.id(), q4.id());
    assertThat(chosenQuestionIds).contains(q2.id(), q5.id());
    assertThat(chosenQuestionIds).contains(q3.id(), q6.id());
    // Multiple distinct combinations are generated
    assertThat(distinctTrios.size()).isGreaterThan(1);
  }

  @Test
  void throwsExceptionWhenSelectionVersionIsUnsupported() {
    var quiz = createV25QuizBank(readingId);

    assertThatThrownBy(() -> policy.select(quiz, userId, readingId, submissionId, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported selectionVersion: 0");

    assertThatThrownBy(() -> policy.select(quiz, userId, readingId, submissionId, 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported selectionVersion: 3");
  }

  @Test
  void throwsExceptionWhenMissingRequiredQuestionType() {
    // Missing MAIN_IDEA
    var incompleteQuiz =
        new ComprehensionQuiz(
            readingId,
            List.of(
                createQuestion(readingId, 1, QuestionType.FACTUAL),
                createQuestion(readingId, 2, QuestionType.INFERENCE)));

    assertThatThrownBy(() -> policy.select(incompleteQuiz, userId, readingId, submissionId, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No candidate questions found for type MAIN_IDEA");
  }

  @Test
  void throwsExceptionWhenNullArguments() {
    var quiz = createV25QuizBank(readingId);

    assertThatThrownBy(() -> policy.select(null, userId, readingId, submissionId, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> policy.select(quiz, null, readingId, submissionId, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> policy.select(quiz, userId, null, submissionId, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> policy.select(quiz, userId, readingId, null, 1))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void differentReadingIdProducesDifferentSelection() {
    var quiz = createV26SixQuestionBank(readingId);
    Set<String> distinctSelections = new HashSet<>();
    for (int i = 0; i < 50; i++) {
      UUID otherReadingId = UUID.randomUUID();
      var selected = policy.select(quiz, userId, otherReadingId, submissionId, 2);
      distinctSelections.add(
          selected.get(0).id() + "|" + selected.get(1).id() + "|" + selected.get(2).id());
    }
    assertThat(distinctSelections.size()).isGreaterThan(1);
  }

  @Test
  void differentUserIdProducesDifferentSelection() {
    var quiz = createV26SixQuestionBank(readingId);
    Set<String> distinctSelections = new HashSet<>();
    for (int i = 0; i < 50; i++) {
      UUID otherUserId = UUID.randomUUID();
      var selected = policy.select(quiz, otherUserId, readingId, submissionId, 2);
      distinctSelections.add(
          selected.get(0).id() + "|" + selected.get(1).id() + "|" + selected.get(2).id());
    }
    assertThat(distinctSelections.size()).isGreaterThan(1);
  }

  @Test
  void selectionVersionIsPartOfSeed() {
    var q1 = createQuestion(readingId, 1, QuestionType.FACTUAL);
    var q2 = createQuestion(readingId, 2, QuestionType.FACTUAL);
    var q3 = createQuestion(readingId, 2, QuestionType.INFERENCE);
    var q4 = createQuestion(readingId, 3, QuestionType.MAIN_IDEA);
    var quiz = new ComprehensionQuiz(readingId, List.of(q1, q2, q3, q4));

    boolean foundDifference = false;
    for (int i = 0; i < 50; i++) {
      UUID testSubId = UUID.randomUUID();
      var selV1 = policy.select(quiz, userId, readingId, testSubId, 1);
      var selV2 = policy.select(quiz, userId, readingId, testSubId, 2);
      if (!selV1.get(0).id().equals(selV2.get(0).id())) {
        foundDifference = true;
        break;
      }
    }
    assertThat(foundDifference)
        .as("Selection must differ between V1 and V2 because selectionVersion is part of the seed")
        .isTrue();
  }

  @Test
  void candidateCountIsDynamicAndSupportsMoreThanTwoCandidates() {
    var q1 = createQuestion(readingId, 1, QuestionType.FACTUAL);
    var q2 = createQuestion(readingId, 2, QuestionType.FACTUAL);
    var q3 = createQuestion(readingId, 4, QuestionType.FACTUAL);
    var q4 = createQuestion(readingId, 3, QuestionType.INFERENCE);
    var q5 = createQuestion(readingId, 5, QuestionType.MAIN_IDEA);
    var quiz = new ComprehensionQuiz(readingId, List.of(q1, q2, q3, q4, q5));

    Set<UUID> chosenFactuals = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      UUID randomSubId = UUID.randomUUID();
      var selected = policy.select(quiz, userId, readingId, randomSubId, 2);
      chosenFactuals.add(selected.get(0).id());
    }

    assertThat(chosenFactuals).containsExactlyInAnyOrder(q1.id(), q2.id(), q3.id());
  }
}
