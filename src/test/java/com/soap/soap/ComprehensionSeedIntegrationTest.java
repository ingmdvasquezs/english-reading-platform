package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.soap.soap.application.command.AnswerSubmission;
import com.soap.soap.application.command.SubmitComprehensionAttemptCommand;
import com.soap.soap.application.exception.InvalidComprehensionSubmissionException;
import com.soap.soap.application.port.in.GetReadingComprehensionQuizPort;
import com.soap.soap.application.port.in.SubmitComprehensionAttemptPort;
import com.soap.soap.application.port.out.ComprehensionAttemptRepositoryPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserComprehensionAnswer;
import com.soap.soap.domain.model.UserComprehensionAttempt;
import com.soap.soap.domain.service.ComprehensionQuizSelectionPolicy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  @Autowired private ComprehensionAttemptRepositoryPort attemptRepository;
  @Autowired private GetReadingComprehensionQuizPort getQuizPort;
  @Autowired private SubmitComprehensionAttemptPort submitAttemptPort;
  @Autowired private ReadingProgressRepositoryPort progressRepository;
  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private UserRepositoryPort userRepository;
  @Autowired private ComprehensionQuizSelectionPolicy selectionPolicy;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private jakarta.persistence.EntityManager entityManager;

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
  void allSixAuthorizedReadingsHaveQuizzesSeededWithSixQuestionsEach() {
    SEEDED_READINGS.forEach(
        (title, readingId) -> {
          var quizOpt = quizRepository.findByReadingId(readingId);
          assertThat(quizOpt).as("Quiz for reading %s should exist", title).isPresent();
          assertThat(quizOpt.get().isAvailable()).isTrue();
          assertThat(quizOpt.get().questions())
              .as("Reading %s must have 6 questions (V25 + V26)", title)
              .hasSize(6);
        });
  }

  @Test
  void verifiesEditorialIntegrityOfAllSeededQuestionsAndOptions() {
    var report = new StringBuilder("\n=== V25 + V26 EDITORIAL VERIFICATION REPORT ===\n");

    SEEDED_READINGS.forEach(
        (title, readingId) -> {
          var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
          assertThat(quiz.questions())
              .as("Reading %s must have exactly 6 questions", title)
              .hasSize(6);

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
              .as(
                  "Reading %s must contain [FACTUAL, INFERENCE, MAIN_IDEA, FACTUAL, INFERENCE, MAIN_IDEA]",
                  title)
              .containsExactly(
                  QuestionType.FACTUAL,
                  QuestionType.INFERENCE,
                  QuestionType.MAIN_IDEA,
                  QuestionType.FACTUAL,
                  QuestionType.INFERENCE,
                  QuestionType.MAIN_IDEA);

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

    progressRepository.complete(testUser.id(), readingId, LocalDateTime.now());

    var legacyView = getQuizPort.getQuiz(readingId);
    assertThat(legacyView.available()).isTrue();
    assertThat(legacyView.selectionVersion()).isNull();
    assertThat(legacyView.questions()).hasSize(3);

    for (var q : legacyView.questions()) {
      assertThat(q.prompt()).isNotBlank();
      assertThat(q.options()).hasSize(4);
      for (var o : q.options()) {
        assertThat(o.optionId()).isNotNull();
        assertThat(o.ordinal()).isBetween(1, 4);
        assertThat(o.content()).isNotBlank();
      }
    }

    UUID submissionId = UUID.randomUUID();
    var versionedView = getQuizPort.getQuiz(readingId, submissionId);
    assertThat(versionedView.available()).isTrue();
    assertThat(versionedView.selectionVersion()).isEqualTo(2);
    assertThat(versionedView.questions()).hasSize(3);

    assertThat(versionedView.questions().get(0).ordinal()).isEqualTo(1);
    assertThat(versionedView.questions().get(0).questionType()).isEqualTo(QuestionType.FACTUAL);
    assertThat(versionedView.questions().get(1).ordinal()).isEqualTo(2);
    assertThat(versionedView.questions().get(1).questionType()).isEqualTo(QuestionType.INFERENCE);
    assertThat(versionedView.questions().get(2).ordinal()).isEqualTo(3);
    assertThat(versionedView.questions().get(2).questionType()).isEqualTo(QuestionType.MAIN_IDEA);
  }

  @Test
  @Transactional
  void scoresSubmissionCorrectlyAcrossScoringBandsForVersion1AndVersion2() {
    UUID readingId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    progressRepository.complete(testUser.id(), readingId, LocalDateTime.now());

    var quiz = quizRepository.findByReadingId(readingId).orElseThrow();

    // 1. Submit V1 (legacy: ordinal <= 3)
    UUID subIdV1 = UUID.randomUUID();
    var v1Questions = quiz.questions().stream().filter(q -> q.ordinal() <= 3).toList();
    var allCorrectV1 =
        v1Questions.stream()
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

    var result100V1 =
        submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(readingId, subIdV1, allCorrectV1, 1));
    assertThat(result100V1.correctAnswersCount()).isEqualTo(3);
    assertThat(result100V1.scorePercentage()).isEqualByComparingTo(new BigDecimal("100.00"));

    // 2. Submit V2
    UUID subIdV2 = UUID.randomUUID();
    var selectedV2 = selectionPolicy.select(quiz, testUser.id(), readingId, subIdV2, 2);

    var selectedFullQuestions =
        selectedV2.stream()
            .map(
                sq ->
                    quiz.questions().stream()
                        .filter(q -> q.id().equals(sq.id()))
                        .findFirst()
                        .orElseThrow())
            .toList();

    var partialAnswersV2 = new ArrayList<AnswerSubmission>();
    partialAnswersV2.add(
        new AnswerSubmission(
            selectedFullQuestions.get(0).id(),
            selectedFullQuestions.get(0).options().stream()
                .filter(ComprehensionOption::isCorrect)
                .findFirst()
                .orElseThrow()
                .id()));
    partialAnswersV2.add(
        new AnswerSubmission(
            selectedFullQuestions.get(1).id(),
            selectedFullQuestions.get(1).options().stream()
                .filter(ComprehensionOption::isCorrect)
                .findFirst()
                .orElseThrow()
                .id()));
    partialAnswersV2.add(
        new AnswerSubmission(
            selectedFullQuestions.get(2).id(),
            selectedFullQuestions.get(2).options().stream()
                .filter(o -> !o.isCorrect())
                .findFirst()
                .orElseThrow()
                .id()));

    var result66V2 =
        submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(readingId, subIdV2, partialAnswersV2, 2));
    assertThat(result66V2.correctAnswersCount()).isEqualTo(2);
    assertThat(result66V2.scorePercentage()).isEqualByComparingTo(new BigDecimal("66.67"));
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

    System.out.println("=== V26 DATABASE INTEGRITY REPORT ===");
    System.out.println("QUESTION_COUNT = " + questionCount);
    System.out.println("DISTINCT_QUESTION_IDS = " + distinctQuestionIds);
    System.out.println("OPTION_COUNT = " + optionCount);
    System.out.println("DISTINCT_OPTION_IDS = " + distinctOptionIds);
    System.out.println("QUESTIONS_WITHOUT_CORRECT_OPTION = " + questionsWithoutCorrect);
    System.out.println("QUESTIONS_WITH_MULTIPLE_CORRECT_OPTIONS = " + questionsWithMultipleCorrect);
    System.out.println("ORPHAN_QUESTIONS = " + orphanQuestions);
    System.out.println("ORPHAN_OPTIONS = " + orphanOptions);

    assertThat(questionCount).isEqualTo(36);
    assertThat(distinctQuestionIds).isEqualTo(36);
    assertThat(optionCount).isEqualTo(144);
    assertThat(distinctOptionIds).isEqualTo(144);
    assertThat(questionsWithoutCorrect).isZero();
    assertThat(questionsWithMultipleCorrect).isZero();
    assertThat(orphanQuestions).isZero();
    assertThat(orphanOptions).isZero();
    assertThat(emptyExplanations).isZero();

    // Verify 6 questions per reading with ordinal 1..6 and 2 of each type
    SEEDED_READINGS
        .values()
        .forEach(
            readingId -> {
              List<Integer> questionOrdinals =
                  jdbc.queryForList(
                      "SELECT ordinal FROM reading_comprehension_questions WHERE reading_id = ? ORDER BY ordinal",
                      Integer.class,
                      readingId);
              assertThat(questionOrdinals).containsExactly(1, 2, 3, 4, 5, 6);

              Integer factualCount =
                  jdbc.queryForObject(
                      "SELECT count(*) FROM reading_comprehension_questions WHERE reading_id = ? AND question_type = 'FACTUAL'",
                      Integer.class,
                      readingId);
              Integer inferenceCount =
                  jdbc.queryForObject(
                      "SELECT count(*) FROM reading_comprehension_questions WHERE reading_id = ? AND question_type = 'INFERENCE'",
                      Integer.class,
                      readingId);
              Integer mainIdeaCount =
                  jdbc.queryForObject(
                      "SELECT count(*) FROM reading_comprehension_questions WHERE reading_id = ? AND question_type = 'MAIN_IDEA'",
                      Integer.class,
                      readingId);

              assertThat(factualCount).isEqualTo(2);
              assertThat(inferenceCount).isEqualTo(2);
              assertThat(mainIdeaCount).isEqualTo(2);
            });

    // Verify 4 options per question with ordinal 1..4
    List<UUID> allQuestionIds =
        jdbc.queryForList("SELECT id FROM reading_comprehension_questions", UUID.class);
    assertThat(allQuestionIds).hasSize(36);
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

  @Test
  void motorVersion2_DeterminismAndDisplayOrdinalsAcrossReadings() {
    SEEDED_READINGS.forEach(
        (title, readingId) -> {
          var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
          UUID subId = UUID.randomUUID();

          var sel1 = selectionPolicy.select(quiz, testUser.id(), readingId, subId, 2);
          var sel2 = selectionPolicy.select(quiz, testUser.id(), readingId, subId, 2);

          assertThat(sel1).hasSize(3);
          assertThat(sel2).hasSize(3);
          for (int i = 0; i < 3; i++) {
            assertThat(sel1.get(i).id()).isEqualTo(sel2.get(i).id());
            assertThat(sel1.get(i).ordinal()).isEqualTo(i + 1);
          }

          assertThat(sel1.get(0).questionType()).isEqualTo(QuestionType.FACTUAL);
          assertThat(sel1.get(1).questionType()).isEqualTo(QuestionType.INFERENCE);
          assertThat(sel1.get(2).questionType()).isEqualTo(QuestionType.MAIN_IDEA);

          assertThat(sel1.get(0).ordinal()).isEqualTo(1);
          assertThat(sel1.get(1).ordinal()).isEqualTo(2);
          assertThat(sel1.get(2).ordinal()).isEqualTo(3);
        });
  }

  @Test
  void motorVersion1_OnlySelectsV25Questions() {
    SEEDED_READINGS.forEach(
        (title, readingId) -> {
          var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
          for (int i = 0; i < 30; i++) {
            UUID randomSubId = UUID.randomUUID();
            var selected = selectionPolicy.select(quiz, testUser.id(), readingId, randomSubId, 1);
            assertThat(selected).hasSize(3);

            for (var sq : selected) {
              var orig =
                  quiz.questions().stream()
                      .filter(q -> q.id().equals(sq.id()))
                      .findFirst()
                      .orElseThrow();
              assertThat(orig.ordinal())
                  .as("VERSION_1 can only select editorial ordinals <= 3")
                  .isLessThanOrEqualTo(3);
            }
          }
        });
  }

  @Test
  void motorVersion2_BothCandidatesReachableAcrossSubmissionIdsForEveryReading() {
    SEEDED_READINGS.forEach(
        (title, readingId) -> {
          var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
          Set<UUID> chosenQuestionIds = new HashSet<>();
          Set<String> distinctTrios = new HashSet<>();

          for (int i = 0; i < 100; i++) {
            UUID randomSubId = UUID.randomUUID();
            var selected = selectionPolicy.select(quiz, testUser.id(), readingId, randomSubId, 2);
            for (var sq : selected) {
              chosenQuestionIds.add(sq.id());
            }
            distinctTrios.add(
                selected.get(0).id() + "|" + selected.get(1).id() + "|" + selected.get(2).id());
          }

          assertThat(chosenQuestionIds)
              .as("All 6 questions of %s must be reachable in VERSION_2", title)
              .hasSize(6);

          assertThat(distinctTrios.size())
              .as("Reading %s should generate multiple distinct question combinations", title)
              .isGreaterThan(1);
        });
  }

  @Test
  @Transactional
  void submissionValidationRulesEnforcedServerSide() {
    UUID readingId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    progressRepository.complete(testUser.id(), readingId, LocalDateTime.now());

    var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
    UUID subId = UUID.randomUUID();
    var selected = selectionPolicy.select(quiz, testUser.id(), readingId, subId, 2);

    // 1. Question not assigned to this submissionId
    var nonSelectedQuestion =
        quiz.questions().stream()
            .filter(q -> selected.stream().noneMatch(sq -> sq.id().equals(q.id())))
            .findFirst()
            .orElseThrow();

    var wrongQuestionAnswers =
        List.of(
            new AnswerSubmission(
                nonSelectedQuestion.id(), nonSelectedQuestion.options().get(0).id()),
            new AnswerSubmission(selected.get(1).id(), selected.get(1).options().get(0).id()),
            new AnswerSubmission(selected.get(2).id(), selected.get(2).options().get(0).id()));

    assertThatThrownBy(
            () ->
                submitAttemptPort.submitAttempt(
                    new SubmitComprehensionAttemptCommand(
                        readingId, subId, wrongQuestionAnswers, 2)))
        .isInstanceOf(InvalidComprehensionSubmissionException.class);

    // 2. Duplicate questions
    var duplicateAnswers =
        List.of(
            new AnswerSubmission(selected.get(0).id(), selected.get(0).options().get(0).id()),
            new AnswerSubmission(selected.get(0).id(), selected.get(0).options().get(0).id()),
            new AnswerSubmission(selected.get(2).id(), selected.get(2).options().get(0).id()));

    assertThatThrownBy(
            () ->
                submitAttemptPort.submitAttempt(
                    new SubmitComprehensionAttemptCommand(readingId, subId, duplicateAnswers, 2)))
        .isInstanceOf(InvalidComprehensionSubmissionException.class);

    // 3. Question count != 3
    var twoAnswers =
        List.of(
            new AnswerSubmission(selected.get(0).id(), selected.get(0).options().get(0).id()),
            new AnswerSubmission(selected.get(1).id(), selected.get(1).options().get(0).id()));

    assertThatThrownBy(
            () ->
                submitAttemptPort.submitAttempt(
                    new SubmitComprehensionAttemptCommand(readingId, subId, twoAnswers, 2)))
        .isInstanceOf(InvalidComprehensionSubmissionException.class);

    // 4. Option belonging to a different question
    var swappedOptionAnswers =
        List.of(
            new AnswerSubmission(selected.get(0).id(), selected.get(1).options().get(0).id()),
            new AnswerSubmission(selected.get(1).id(), selected.get(1).options().get(0).id()),
            new AnswerSubmission(selected.get(2).id(), selected.get(2).options().get(0).id()));

    assertThatThrownBy(
            () ->
                submitAttemptPort.submitAttempt(
                    new SubmitComprehensionAttemptCommand(
                        readingId, subId, swappedOptionAnswers, 2)))
        .isInstanceOf(InvalidComprehensionSubmissionException.class);
  }

  @Test
  @Transactional
  void idempotencyReturnsExistingAttemptWithoutRecalculation() {
    UUID readingId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    progressRepository.complete(testUser.id(), readingId, LocalDateTime.now());

    var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
    UUID subId = UUID.randomUUID();
    var selected = selectionPolicy.select(quiz, testUser.id(), readingId, subId, 2);

    var answers =
        selected.stream()
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

    var firstResult =
        submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(readingId, subId, answers, 2));
    assertThat(firstResult.scorePercentage()).isEqualByComparingTo(new BigDecimal("100.00"));

    var secondResult =
        submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(readingId, subId, answers, 2));
    assertThat(secondResult.attemptId()).isEqualTo(firstResult.attemptId());
    assertThat(secondResult.submissionId()).isEqualTo(firstResult.submissionId());
    assertThat(secondResult.scorePercentage()).isEqualTo(firstResult.scorePercentage());
    assertThat(secondResult.submittedAt())
        .isCloseTo(firstResult.submittedAt(), within(1, ChronoUnit.MILLIS));
  }

  @Test
  @Transactional
  void historicalAttemptWithV25AnswersReturnsExactThreeHistoricalQuestions() {
    UUID readingId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    var quiz = quizRepository.findByReadingId(readingId).orElseThrow();
    assertThat(quiz.questions()).hasSize(6);

    UUID historicalAttemptId = UUID.randomUUID();
    UUID historicalSubId = UUID.randomUUID();
    var v25Questions = quiz.questions().stream().filter(q -> q.ordinal() <= 3).toList();

    var historicalAnswers =
        v25Questions.stream()
            .map(
                q ->
                    new UserComprehensionAnswer(
                        UUID.randomUUID(),
                        historicalAttemptId,
                        q.id(),
                        q.options().get(0).id(),
                        q.options().get(0).isCorrect()))
            .toList();

    var historicalAttempt =
        new UserComprehensionAttempt(
            historicalAttemptId,
            testUser.id(),
            readingId,
            historicalSubId,
            new BigDecimal("66.67"),
            2,
            3,
            LocalDateTime.now().minusDays(5),
            historicalAnswers);

    attemptRepository.recordAttempt(historicalAttempt, historicalAnswers);
    entityManager.flush();
    entityManager.clear();

    var answers =
        v25Questions.stream()
            .map(q -> new AnswerSubmission(q.id(), q.options().get(0).id()))
            .toList();

    var result =
        submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(readingId, historicalSubId, answers));

    assertThat(result.totalQuestionsCount()).isEqualTo(3);
    assertThat(result.questions()).hasSize(3);
    assertThat(result.scorePercentage()).isEqualByComparingTo(new BigDecimal("66.67"));

    var returnedQIds = result.questions().stream().map(q -> q.questionId()).toList();
    var expectedV25QIds = v25Questions.stream().map(ComprehensionQuestion::id).toList();
    assertThat(returnedQIds).containsExactlyElementsOf(expectedV25QIds);
  }
}
