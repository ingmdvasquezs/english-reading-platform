package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.command.AnswerSubmission;
import com.soap.soap.application.command.SubmitComprehensionAttemptCommand;
import com.soap.soap.application.port.in.GetReadingComprehensionQuizPort;
import com.soap.soap.application.port.in.SubmitComprehensionAttemptPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class ComprehensionSeedIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final Map<String, UUID> SEEDED_READINGS =
      Map.of(
          "A1 - The Lost Blue Scarf", UUID.fromString("20000000-0000-0000-0000-000000000001"),
          "A2 - A Quiet Morning by the River",
              UUID.fromString("20000000-0000-0000-0000-000000000006"),
          "B1 - Learning to Ask Better Questions",
              UUID.fromString("20000000-0000-0000-0000-000000000011"),
          "B2 - The Cost of Constant Attention",
              UUID.fromString("20000000-0000-0000-0000-000000000013"),
          "C1 - The Museum of Unfinished Things",
              UUID.fromString("20000000-0000-0000-0000-000000000017"),
          "C2 - The Inheritance of Dust", UUID.fromString("30000000-0000-0000-0000-000000000048"));

  @Autowired private ComprehensionQuizRepositoryPort quizRepository;
  @Autowired private GetReadingComprehensionQuizPort getQuizPort;
  @Autowired private SubmitComprehensionAttemptPort submitAttemptPort;
  @Autowired private ReadingProgressRepositoryPort progressRepository;
  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private UserRepositoryPort userRepository;
  @Autowired private JdbcTemplate jdbc;

  private User testUser;

  @BeforeEach
  void setUp() {
    testUser =
        userRepository.save(
            new User(
                null,
                "Seed Test User",
                "seed-test-" + UUID.randomUUID() + "@example.com",
                "hash",
                null));

    var now = java.time.Instant.now();
    var jwt =
        new org.springframework.security.oauth2.jwt.Jwt(
            "token",
            now,
            now.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", testUser.id().toString()));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, jwt, List.of()));
  }

  @Test
  void allSixAuthorizedReadingsHaveQuizzesSeeded() {
    SEEDED_READINGS.forEach(
        (title, readingId) -> {
          var quizOpt = quizRepository.findByReadingId(readingId);
          assertThat(quizOpt).as("Quiz for reading %s should exist", title).isPresent();
          assertThat(quizOpt.get().isAvailable()).isTrue();
        });
  }

  @Test
  void verifiesEditorialIntegrityOfAllSeededQuestionsAndOptions() {
    var report = new StringBuilder("\n=== V25 EDITORIAL VERIFICATION REPORT ===\n");

    SEEDED_READINGS.forEach(
        (title, readingId) -> {
          var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
          assertThat(quiz.questions())
              .as("Reading %s must have exactly 3 questions", title)
              .hasSize(3);

          int totalOptions = 0;
          int correctOptionsCount = 0;

          var types = new ArrayList<QuestionType>();

          for (int qIndex = 0; qIndex < quiz.questions().size(); qIndex++) {
            ComprehensionQuestion q = quiz.questions().get(qIndex);
            assertThat(q.ordinal()).isEqualTo(qIndex + 1);
            assertThat(q.prompt()).isNotBlank();
            assertThat(q.explanation()).isNotBlank();
            types.add(q.questionType());

            assertThat(q.options())
                .as("Question %d in %s must have 4 options", q.ordinal(), title)
                .hasSize(4);

            totalOptions += q.options().size();

            int correctInQuestion = 0;
            for (int oIndex = 0; oIndex < q.options().size(); oIndex++) {
              ComprehensionOption opt = q.options().get(oIndex);
              assertThat(opt.ordinal()).isEqualTo(oIndex + 1);
              assertThat(opt.content()).isNotBlank();
              if (opt.isCorrect()) {
                correctInQuestion++;
                correctOptionsCount++;
              }
            }

            assertThat(correctInQuestion)
                .as("Question %d in %s must have exactly 1 correct option", q.ordinal(), title)
                .isEqualTo(1);
          }

          assertThat(types)
              .as("Reading %s must contain [FACTUAL, INFERENCE, MAIN_IDEA]", title)
              .containsExactly(
                  QuestionType.FACTUAL, QuestionType.INFERENCE, QuestionType.MAIN_IDEA);

          report
              .append(title)
              .append("\nquestions: ")
              .append(quiz.questions().size())
              .append("\noptions: ")
              .append(totalOptions)
              .append("\ncorrect-options: ")
              .append(correctOptionsCount)
              .append("\n\n");
        });

    System.out.println(report.toString());
  }

  @Test
  void preSubmitQuizViewDoesNotExposeCorrectAnswersOrExplanations() {
    UUID readingId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    // Mark as completed so quiz view is accessible
    progressRepository.complete(testUser.id(), readingId, LocalDateTime.now());

    var quizView = getQuizPort.getQuiz(readingId);
    assertThat(quizView.available()).isTrue();
    assertThat(quizView.questions()).hasSize(3);

    for (var q : quizView.questions()) {
      assertThat(q.prompt()).isNotBlank();
      assertThat(q.options()).hasSize(4);
      // Pre-submit DTO ComprehensionQuizOptionView only contains optionId, ordinal, content
      // and structurally has no isCorrect or explanation
      for (var o : q.options()) {
        assertThat(o.optionId()).isNotNull();
        assertThat(o.ordinal()).isBetween(1, 4);
        assertThat(o.content()).isNotBlank();
      }
    }
  }

  @Test
  @Transactional
  void scoresSubmissionCorrectlyAcrossScoringBands() {
    UUID readingId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    progressRepository.complete(testUser.id(), readingId, LocalDateTime.now());

    var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
    var questions = quiz.questions();

    // 1. All correct: 3 / 3 -> 100.00%
    var allCorrectAnswers =
        questions.stream()
            .map(
                q ->
                    new AnswerSubmission(
                        q.id(),
                        q.options().stream()
                            .filter(ComprehensionOption::isCorrect)
                            .findFirst()
                            .orElseThrow()
                            .id()))
            .toList();

    var result100 =
        submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(readingId, UUID.randomUUID(), allCorrectAnswers));
    assertThat(result100.correctAnswersCount()).isEqualTo(3);
    assertThat(result100.scorePercentage()).isEqualByComparingTo(new BigDecimal("100.00"));

    // 2. None correct: 0 / 3 -> 0.00%
    var noneCorrectAnswers =
        questions.stream()
            .map(
                q ->
                    new AnswerSubmission(
                        q.id(),
                        q.options().stream()
                            .filter(o -> !o.isCorrect())
                            .findFirst()
                            .orElseThrow()
                            .id()))
            .toList();

    var result0 =
        submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(
                readingId, UUID.randomUUID(), noneCorrectAnswers));
    assertThat(result0.correctAnswersCount()).isEqualTo(0);
    assertThat(result0.scorePercentage()).isEqualByComparingTo(new BigDecimal("0.00"));

    // 3. Partial: 2 / 3 -> 66.67%
    var partialAnswers = new ArrayList<AnswerSubmission>();
    // q0: correct
    partialAnswers.add(
        new AnswerSubmission(
            questions.get(0).id(),
            questions.get(0).options().stream()
                .filter(ComprehensionOption::isCorrect)
                .findFirst()
                .orElseThrow()
                .id()));
    // q1: correct
    partialAnswers.add(
        new AnswerSubmission(
            questions.get(1).id(),
            questions.get(1).options().stream()
                .filter(ComprehensionOption::isCorrect)
                .findFirst()
                .orElseThrow()
                .id()));
    // q2: incorrect
    partialAnswers.add(
        new AnswerSubmission(
            questions.get(2).id(),
            questions.get(2).options().stream()
                .filter(o -> !o.isCorrect())
                .findFirst()
                .orElseThrow()
                .id()));

    var result66 =
        submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(readingId, UUID.randomUUID(), partialAnswers));
    assertThat(result66.correctAnswersCount()).isEqualTo(2);
    assertThat(result66.scorePercentage()).isEqualByComparingTo(new BigDecimal("66.67"));
  }

  @Test
  void verifiesDatabaseIntegrityAtSqlLevel() {
    Integer questionCount =
        jdbc.queryForObject("SELECT count(*) FROM reading_comprehension_questions", Integer.class);
    Integer distinctQuestionIds =
        jdbc.queryForObject(
            "SELECT count(DISTINCT id) FROM reading_comprehension_questions", Integer.class);
    Integer optionCount =
        jdbc.queryForObject("SELECT count(*) FROM reading_comprehension_options", Integer.class);
    Integer distinctOptionIds =
        jdbc.queryForObject(
            "SELECT count(DISTINCT id) FROM reading_comprehension_options", Integer.class);

    Integer questionsWithoutCorrect =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM reading_comprehension_questions q
            WHERE NOT EXISTS (
                SELECT 1 FROM reading_comprehension_options o
                WHERE o.question_id = q.id AND o.is_correct = true
            )
            """,
            Integer.class);

    Integer questionsWithMultipleCorrect =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM (
                SELECT question_id FROM reading_comprehension_options
                WHERE is_correct = true
                GROUP BY question_id
                HAVING count(*) > 1
            ) sub
            """,
            Integer.class);

    Integer orphanQuestions =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM reading_comprehension_questions q
            WHERE NOT EXISTS (
                SELECT 1 FROM readings r WHERE r.id = q.reading_id
            )
            """,
            Integer.class);

    Integer orphanOptions =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM reading_comprehension_options o
            WHERE NOT EXISTS (
                SELECT 1 FROM reading_comprehension_questions q WHERE q.id = o.question_id
            )
            """,
            Integer.class);

    Integer emptyExplanations =
        jdbc.queryForObject(
            "SELECT count(*) FROM reading_comprehension_questions WHERE explanation IS NULL OR trim(explanation) = ''",
            Integer.class);

    System.out.println("=== DATABASE INTEGRITY REPORT ===");
    System.out.println("QUESTION_COUNT = " + questionCount);
    System.out.println("DISTINCT_QUESTION_IDS = " + distinctQuestionIds);
    System.out.println("OPTION_COUNT = " + optionCount);
    System.out.println("DISTINCT_OPTION_IDS = " + distinctOptionIds);
    System.out.println("QUESTIONS_WITHOUT_CORRECT_OPTION = " + questionsWithoutCorrect);
    System.out.println("QUESTIONS_WITH_MULTIPLE_CORRECT_OPTIONS = " + questionsWithMultipleCorrect);
    System.out.println("ORPHAN_QUESTIONS = " + orphanQuestions);
    System.out.println("ORPHAN_OPTIONS = " + orphanOptions);

    assertThat(questionCount).isEqualTo(18);
    assertThat(distinctQuestionIds).isEqualTo(18);
    assertThat(optionCount).isEqualTo(72);
    assertThat(distinctOptionIds).isEqualTo(72);
    assertThat(questionsWithoutCorrect).isZero();
    assertThat(questionsWithMultipleCorrect).isZero();
    assertThat(orphanQuestions).isZero();
    assertThat(orphanOptions).isZero();
    assertThat(emptyExplanations).isZero();

    // Verify 3 questions per reading with ordinal 1..3
    SEEDED_READINGS
        .values()
        .forEach(
            readingId -> {
              List<Integer> questionOrdinals =
                  jdbc.queryForList(
                      "SELECT ordinal FROM reading_comprehension_questions WHERE reading_id = ? ORDER BY ordinal",
                      Integer.class,
                      readingId);
              assertThat(questionOrdinals).containsExactly(1, 2, 3);
            });

    // Verify 4 options per question with ordinal 1..4
    List<UUID> allQuestionIds =
        jdbc.queryForList("SELECT id FROM reading_comprehension_questions", UUID.class);
    assertThat(allQuestionIds).hasSize(18);
    allQuestionIds.forEach(
        questionId -> {
          List<Integer> optionOrdinals =
              jdbc.queryForList(
                  "SELECT ordinal FROM reading_comprehension_options WHERE question_id = ? ORDER BY ordinal",
                  Integer.class,
                  questionId);
          assertThat(optionOrdinals).containsExactly(1, 2, 3, 4);
        });
  }
}
