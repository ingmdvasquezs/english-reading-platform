package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.QuestionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComprehensionDomainAndScoringTest {

  @Test
  void calculatesDeterministicScorePercentageWithScaleTwoAndHalfUp() {
    // 3 out of 4: 75.00
    assertThat(calculateScore(3, 4)).isEqualTo(new BigDecimal("75.00"));

    // 1 out of 3: 33.33
    assertThat(calculateScore(1, 3)).isEqualTo(new BigDecimal("33.33"));

    // 2 out of 3: 66.67
    assertThat(calculateScore(2, 3)).isEqualTo(new BigDecimal("66.67"));

    // 0 out of 3: 0.00
    assertThat(calculateScore(0, 3)).isEqualTo(new BigDecimal("0.00"));

    // 3 out of 3: 100.00
    assertThat(calculateScore(3, 3)).isEqualTo(new BigDecimal("100.00"));

    // 4 out of 4: 100.00
    assertThat(calculateScore(4, 4)).isEqualTo(new BigDecimal("100.00"));
  }

  private BigDecimal calculateScore(int correct, int total) {
    return BigDecimal.valueOf(correct)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
  }

  @Test
  void enforcesExactlyFourOptionsPerQuestion() {
    UUID qId = UUID.randomUUID();
    UUID rId = UUID.randomUUID();

    // 3 options -> throws IllegalArgumentException
    var threeOptions =
        List.of(
            new ComprehensionOption(UUID.randomUUID(), qId, 1, "A", true),
            new ComprehensionOption(UUID.randomUUID(), qId, 2, "B", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 3, "C", false));

    assertThatThrownBy(
            () ->
                new ComprehensionQuestion(
                    qId,
                    rId,
                    1,
                    QuestionType.FACTUAL,
                    "Prompt?",
                    "Explanation.",
                    LocalDateTime.now(),
                    threeOptions))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Question must have exactly 4 options");
  }

  @Test
  void enforcesExactlyOneCorrectOptionPerQuestion() {
    UUID qId = UUID.randomUUID();
    UUID rId = UUID.randomUUID();

    // 2 correct options -> throws IllegalArgumentException
    var twoCorrectOptions =
        List.of(
            new ComprehensionOption(UUID.randomUUID(), qId, 1, "A", true),
            new ComprehensionOption(UUID.randomUUID(), qId, 2, "B", true),
            new ComprehensionOption(UUID.randomUUID(), qId, 3, "C", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 4, "D", false));

    assertThatThrownBy(
            () ->
                new ComprehensionQuestion(
                    qId,
                    rId,
                    1,
                    QuestionType.FACTUAL,
                    "Prompt?",
                    "Explanation.",
                    LocalDateTime.now(),
                    twoCorrectOptions))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Question must have exactly 1 correct option");

    // 0 correct options -> throws IllegalArgumentException
    var zeroCorrectOptions =
        List.of(
            new ComprehensionOption(UUID.randomUUID(), qId, 1, "A", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 2, "B", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 3, "C", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 4, "D", false));

    assertThatThrownBy(
            () ->
                new ComprehensionQuestion(
                    qId,
                    rId,
                    1,
                    QuestionType.FACTUAL,
                    "Prompt?",
                    "Explanation.",
                    LocalDateTime.now(),
                    zeroCorrectOptions))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Question must have exactly 1 correct option");
  }

  @Test
  void validatesValidQuestionAndQuizAvailability() {
    UUID qId = UUID.randomUUID();
    UUID rId = UUID.randomUUID();

    var validOptions =
        List.of(
            new ComprehensionOption(UUID.randomUUID(), qId, 1, "A", true),
            new ComprehensionOption(UUID.randomUUID(), qId, 2, "B", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 3, "C", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 4, "D", false));

    var question =
        new ComprehensionQuestion(
            qId,
            rId,
            1,
            QuestionType.FACTUAL,
            "Prompt?",
            "Explanation.",
            LocalDateTime.now(),
            validOptions);

    assertThat(question.options()).hasSize(4);

    var quizWithQuestions = new ComprehensionQuiz(rId, List.of(question));
    assertThat(quizWithQuestions.isAvailable()).isTrue();

    var emptyQuiz = new ComprehensionQuiz(rId, List.of());
    assertThat(emptyQuiz.isAvailable()).isFalse();
  }
}
