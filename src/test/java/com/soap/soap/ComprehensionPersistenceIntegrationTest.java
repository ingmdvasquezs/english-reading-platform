package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.HistoricalQuizMutationException;
import com.soap.soap.application.port.out.ComprehensionAttemptRepositoryPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserComprehensionAnswer;
import com.soap.soap.domain.model.UserComprehensionAttempt;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionOptionEntity;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionQuestionEntity;
import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import com.soap.soap.infrastructure.persistence.repository.JpaComprehensionOptionRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaComprehensionQuestionRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaUserComprehensionAnswerRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaUserComprehensionAttemptRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class ComprehensionPersistenceIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ComprehensionQuizRepositoryPort quizRepository;
  @Autowired private ComprehensionAttemptRepositoryPort attemptRepository;
  @Autowired private JpaComprehensionQuestionRepository jpaQuestionRepository;
  @Autowired private JpaComprehensionOptionRepository jpaOptionRepository;
  @Autowired private JpaUserComprehensionAttemptRepository jpaAttemptRepository;
  @Autowired private JpaUserComprehensionAnswerRepository jpaAnswerRepository;
  @Autowired private JpaReadingRepository jpaReadingRepository;
  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private UserRepositoryPort userRepository;
  @Autowired private EntityManager entityManager;

  @Autowired
  private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  private User testUser;
  private Reading testReading;

  @BeforeEach
  void setUp() {
    testUser =
        userRepository.save(
            new User(
                null,
                "Comprehension User",
                "comp-user-" + UUID.randomUUID() + "@example.com",
                "hash",
                null));

    testReading =
        readingRepository.save(
            new Reading(
                null,
                null,
                "Platform Reading " + UUID.randomUUID(),
                "Some engaging content for testing comprehension.",
                "en",
                null,
                ReadingOrigin.PLATFORM,
                EditorialLevel.A1,
                "Daily Life & Relationships",
                com.soap.soap.domain.model.EditorialStatus.PUBLISHED));
  }

  @Test
  @Transactional
  void savesAndLoadsComprehensionQuizWithQuestionsAndOptions() {
    var readingRef = entityManager.getReference(ReadingEntity.class, testReading.id());

    UUID qId = UUID.randomUUID();
    var qEntity = new ComprehensionQuestionEntity();
    qEntity.setId(qId);
    qEntity.setReading(readingRef);
    qEntity.setOrdinal(1);
    qEntity.setQuestionType(QuestionType.FACTUAL.name());
    qEntity.setPrompt("What is the story about?");
    qEntity.setExplanation("The story is about a daily life scenario.");
    qEntity.setCreatedAt(LocalDateTime.now());

    UUID o1Id = UUID.randomUUID();
    var o1 = new ComprehensionOptionEntity();
    o1.setId(o1Id);
    o1.setQuestion(qEntity);
    o1.setOrdinal(1);
    o1.setContent("Daily life");
    o1.setCorrect(true);

    UUID o2Id = UUID.randomUUID();
    var o2 = new ComprehensionOptionEntity();
    o2.setId(o2Id);
    o2.setQuestion(qEntity);
    o2.setOrdinal(2);
    o2.setContent("Space travel");
    o2.setCorrect(false);

    UUID o3Id = UUID.randomUUID();
    var o3 = new ComprehensionOptionEntity();
    o3.setId(o3Id);
    o3.setQuestion(qEntity);
    o3.setOrdinal(3);
    o3.setContent("Cooking soup");
    o3.setCorrect(false);

    UUID o4Id = UUID.randomUUID();
    var o4 = new ComprehensionOptionEntity();
    o4.setId(o4Id);
    o4.setQuestion(qEntity);
    o4.setOrdinal(4);
    o4.setContent("Deep sea diving");
    o4.setCorrect(false);

    qEntity.getOptions().addAll(List.of(o1, o2, o3, o4));
    jpaQuestionRepository.save(qEntity);
    entityManager.flush();
    entityManager.clear();

    var quizOpt = quizRepository.findByReadingId(testReading.id());
    assertThat(quizOpt).isPresent();
    var quiz = quizOpt.get();
    assertThat(quiz.isAvailable()).isTrue();
    assertThat(quiz.questions()).hasSize(1);

    var q = quiz.questions().get(0);
    assertThat(q.prompt()).isEqualTo("What is the story about?");
    assertThat(q.questionType()).isEqualTo(QuestionType.FACTUAL);
    assertThat(q.options()).hasSize(4);
    assertThat(q.options().stream().filter(ComprehensionOption::isCorrect).count()).isEqualTo(1);
  }

  @Test
  @Transactional
  void enforcesSingleCorrectOptionIndexAtDatabaseLevel() {
    var readingRef = entityManager.getReference(ReadingEntity.class, testReading.id());

    UUID qId = UUID.randomUUID();
    var qEntity = new ComprehensionQuestionEntity();
    qEntity.setId(qId);
    qEntity.setReading(readingRef);
    qEntity.setOrdinal(1);
    qEntity.setQuestionType(QuestionType.FACTUAL.name());
    qEntity.setPrompt("Prompt");
    qEntity.setExplanation("Explanation");
    qEntity.setCreatedAt(LocalDateTime.now());
    jpaQuestionRepository.save(qEntity);
    entityManager.flush();

    var o1 = new ComprehensionOptionEntity();
    o1.setId(UUID.randomUUID());
    o1.setQuestion(qEntity);
    o1.setOrdinal(1);
    o1.setContent("Correct 1");
    o1.setCorrect(true);
    jpaOptionRepository.save(o1);
    entityManager.flush();

    var o2 = new ComprehensionOptionEntity();
    o2.setId(UUID.randomUUID());
    o2.setQuestion(qEntity);
    o2.setOrdinal(2);
    o2.setContent("Correct 2 (Violates uk_rc_options_single_correct)");
    o2.setCorrect(true);

    assertThatThrownBy(
            () -> {
              jpaOptionRepository.save(o2);
              entityManager.flush();
            })
        .hasMessageContaining("uk_rc_options_single_correct");
  }

  @Test
  void handlesConcurrentAttemptsWithSameSubmissionIdSafely() throws Exception {
    UUID submissionId = UUID.randomUUID();
    int threadsCount = 8;
    var executor = Executors.newFixedThreadPool(threadsCount);
    List<Callable<Integer>> tasks = new ArrayList<>();

    for (int i = 0; i < threadsCount; i++) {
      tasks.add(
          () -> {
            try {
              return transactionTemplate.execute(
                  status ->
                      jpaAttemptRepository.insertAttemptIfAbsent(
                          UUID.randomUUID(),
                          testUser.id(),
                          testReading.id(),
                          submissionId,
                          new BigDecimal("75.00"),
                          3,
                          4,
                          LocalDateTime.now()));
            } catch (Exception ex) {
              return -1;
            }
          });
    }

    List<Future<Integer>> results = executor.invokeAll(tasks);
    executor.shutdown();

    int successfulInserts = 0;
    int conflictingInserts = 0;

    for (var future : results) {
      int res = future.get();
      if (res == 1) {
        successfulInserts++;
      } else if (res == 0) {
        conflictingInserts++;
      }
    }

    // Exactly one thread successfully inserted, and all others encountered conflict cleanly
    // (returned 0)
    assertThat(successfulInserts).isEqualTo(1);
    assertThat(conflictingInserts).isEqualTo(threadsCount - 1);

    var recordedOpt =
        attemptRepository.findByUserIdAndReadingIdAndSubmissionId(
            testUser.id(), testReading.id(), submissionId);
    assertThat(recordedOpt).isPresent();
    assertThat(recordedOpt.get().scorePercentage()).isEqualTo(new BigDecimal("75.00"));
  }

  @Test
  @Transactional
  void protectsHistoricalAnswersWhenQuestionDeletionIsAttempted() {
    var readingRef = entityManager.getReference(ReadingEntity.class, testReading.id());

    UUID qId = UUID.randomUUID();
    var qEntity = new ComprehensionQuestionEntity();
    qEntity.setId(qId);
    qEntity.setReading(readingRef);
    qEntity.setOrdinal(1);
    qEntity.setQuestionType(QuestionType.FACTUAL.name());
    qEntity.setPrompt("Sample question");
    qEntity.setExplanation("Sample explanation");
    qEntity.setCreatedAt(LocalDateTime.now());

    UUID optId = UUID.randomUUID();
    var o1 = new ComprehensionOptionEntity();
    o1.setId(optId);
    o1.setQuestion(qEntity);
    o1.setOrdinal(1);
    o1.setContent("Option 1");
    o1.setCorrect(true);
    qEntity.getOptions().add(o1);

    jpaQuestionRepository.save(qEntity);
    entityManager.flush();

    UUID attemptId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    var attempt =
        new UserComprehensionAttempt(
            attemptId,
            testUser.id(),
            testReading.id(),
            submissionId,
            new BigDecimal("100.00"),
            1,
            1,
            LocalDateTime.now(),
            List.of());

    var answer = new UserComprehensionAnswer(UUID.randomUUID(), attemptId, qId, optId, true);

    attemptRepository.recordAttempt(attempt, List.of(answer));
    entityManager.flush();

    // Now attempt to delete the question directly: it MUST be rejected by PostgreSQL foreign key
    // constraint (RESTRICT / NO ACTION)
    assertThatThrownBy(
            () -> {
              jpaQuestionRepository.deleteById(qId);
              entityManager.flush();
            })
        .hasMessageContaining("user_comprehension_answers");
  }

  @Test
  @Transactional
  void deletesReadingAndAllComprehensionDataViaCascadeWithoutReferentialConflict() {
    var readingRef = entityManager.getReference(ReadingEntity.class, testReading.id());

    UUID qId = UUID.randomUUID();
    var qEntity = new ComprehensionQuestionEntity();
    qEntity.setId(qId);
    qEntity.setReading(readingRef);
    qEntity.setOrdinal(1);
    qEntity.setQuestionType(QuestionType.FACTUAL.name());
    qEntity.setPrompt("Cascade question");
    qEntity.setExplanation("Cascade explanation");
    qEntity.setCreatedAt(LocalDateTime.now());

    UUID optId = UUID.randomUUID();
    var o1 = new ComprehensionOptionEntity();
    o1.setId(optId);
    o1.setQuestion(qEntity);
    o1.setOrdinal(1);
    o1.setContent("Cascade option 1");
    o1.setCorrect(true);
    qEntity.getOptions().add(o1);

    jpaQuestionRepository.save(qEntity);
    entityManager.flush();

    UUID attemptId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    var attempt =
        new UserComprehensionAttempt(
            attemptId,
            testUser.id(),
            testReading.id(),
            submissionId,
            new BigDecimal("100.00"),
            1,
            1,
            LocalDateTime.now(),
            List.of());

    var answer = new UserComprehensionAnswer(UUID.randomUUID(), attemptId, qId, optId, true);
    attemptRepository.recordAttempt(attempt, List.of(answer));
    entityManager.flush();
    entityManager.clear();

    assertThat(jpaQuestionRepository.findById(qId)).isPresent();
    assertThat(jpaOptionRepository.findById(optId)).isPresent();
    assertThat(jpaAttemptRepository.findById(attemptId)).isPresent();
    assertThat(jpaAnswerRepository.findById(answer.id())).isPresent();

    // Delete reading: PostgreSQL triggers cascade deletion of questions, options, attempts, and
    // answers
    jpaReadingRepository.deleteById(testReading.id());
    entityManager.flush();
    entityManager.clear();

    assertThat(jpaReadingRepository.findById(testReading.id())).isEmpty();
    assertThat(jpaQuestionRepository.findById(qId)).isEmpty();
    assertThat(jpaOptionRepository.findById(optId)).isEmpty();
    assertThat(jpaAttemptRepository.findById(attemptId)).isEmpty();
    assertThat(jpaAnswerRepository.findById(answer.id())).isEmpty();
  }

  @Test
  @Transactional
  void testA_and_B_sameStructureAndContentChangesPreserveQuestionAndOptionIds() {
    var q1 =
        createValidDomainQuestion(
            testReading.id(), 1, "Original Prompt 1", "Original Exp 1", List.of(1, 2, 3, 4), 1);
    var q2 =
        createValidDomainQuestion(
            testReading.id(), 2, "Original Prompt 2", "Original Exp 2", List.of(1, 2, 3, 4), 2);

    quizRepository.replaceQuestions(testReading.id(), List.of(q1, q2));
    entityManager.flush();
    entityManager.clear();

    var existingQuestions =
        jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(testReading.id());
    assertThat(existingQuestions).hasSize(2);
    UUID originalQ1Id = existingQuestions.get(0).getId();
    UUID originalQ2Id = existingQuestions.get(1).getId();

    List<UUID> q1OptIds =
        existingQuestions.get(0).getOptions().stream()
            .map(ComprehensionOptionEntity::getId)
            .toList();
    List<UUID> q2OptIds =
        existingQuestions.get(1).getOptions().stream()
            .map(ComprehensionOptionEntity::getId)
            .toList();

    // Update: same structure, changed prompts/explanations and option contents/correctness
    var updatedQ1 =
        createValidDomainQuestion(
            testReading.id(), 1, "Updated Prompt 1", "Updated Exp 1", List.of(1, 2, 3, 4), 2);
    var updatedQ2 =
        createValidDomainQuestion(
            testReading.id(), 2, "Updated Prompt 2", "Updated Exp 2", List.of(1, 2, 3, 4), 1);

    quizRepository.replaceQuestions(testReading.id(), List.of(updatedQ1, updatedQ2));
    entityManager.flush();
    entityManager.clear();

    var reloadedQuestions =
        jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(testReading.id());
    assertThat(reloadedQuestions).hasSize(2);

    // Question IDs strictly preserved
    assertThat(reloadedQuestions.get(0).getId()).isEqualTo(originalQ1Id);
    assertThat(reloadedQuestions.get(0).getPrompt()).isEqualTo("Updated Prompt 1");
    assertThat(reloadedQuestions.get(0).getExplanation()).isEqualTo("Updated Exp 1");

    assertThat(reloadedQuestions.get(1).getId()).isEqualTo(originalQ2Id);
    assertThat(reloadedQuestions.get(1).getPrompt()).isEqualTo("Updated Prompt 2");
    assertThat(reloadedQuestions.get(1).getExplanation()).isEqualTo("Updated Exp 2");

    // Option IDs strictly preserved
    for (int i = 0; i < 4; i++) {
      assertThat(reloadedQuestions.get(0).getOptions().get(i).getId()).isEqualTo(q1OptIds.get(i));
      assertThat(reloadedQuestions.get(0).getOptions().get(i).getContent())
          .isEqualTo("Updated Prompt 1 - Opt " + (i + 1));
      assertThat(reloadedQuestions.get(0).getOptions().get(i).isCorrect()).isEqualTo((i + 1) == 2);

      assertThat(reloadedQuestions.get(1).getOptions().get(i).getId()).isEqualTo(q2OptIds.get(i));
      assertThat(reloadedQuestions.get(1).getOptions().get(i).getContent())
          .isEqualTo("Updated Prompt 2 - Opt " + (i + 1));
      assertThat(reloadedQuestions.get(1).getOptions().get(i).isCorrect()).isEqualTo((i + 1) == 1);
    }
  }

  @Test
  @Transactional
  void testC_questionCountChangesWhenHistoricalAnswersReferenceQuizFailsSafely() {
    var q1 =
        createValidDomainQuestion(testReading.id(), 1, "Prompt 1", "Exp 1", List.of(1, 2, 3, 4), 1);
    var q2 =
        createValidDomainQuestion(testReading.id(), 2, "Prompt 2", "Exp 2", List.of(1, 2, 3, 4), 1);

    quizRepository.replaceQuestions(testReading.id(), List.of(q1, q2));
    entityManager.flush();
    entityManager.clear();

    var existingQuestions =
        jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(testReading.id());
    UUID q2Id = existingQuestions.get(1).getId();
    UUID opt21Id = existingQuestions.get(1).getOptions().get(0).getId();

    // Record user attempt and answer referencing Question 2
    UUID attemptId = UUID.randomUUID();
    var attempt =
        new UserComprehensionAttempt(
            attemptId,
            testUser.id(),
            testReading.id(),
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            1,
            1,
            LocalDateTime.now(),
            List.of());
    var answer = new UserComprehensionAnswer(UUID.randomUUID(), attemptId, q2Id, opt21Id, true);
    attemptRepository.recordAttempt(attempt, List.of(answer));
    entityManager.flush();
    entityManager.clear();

    // Attempt to reduce questions to only Q1 (omitting Q2 which has historical answer)
    assertThatThrownBy(() -> quizRepository.replaceQuestions(testReading.id(), List.of(q1)))
        .isInstanceOf(HistoricalQuizMutationException.class)
        .hasMessageContaining("historical user comprehension answers reference them");

    entityManager.clear();
    // Verify no questions deleted and historical answers intact
    assertThat(jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(testReading.id())).hasSize(2);
    assertThat(jpaAnswerRepository.findById(answer.id())).isPresent();
  }

  @Test
  @Transactional
  void testD_optionCountChangesWhenHistoricalAnswersReferenceOptionsFailsSafely() {
    var q1 =
        createValidDomainQuestion(testReading.id(), 1, "Prompt 1", "Exp 1", List.of(1, 2, 3, 4), 1);

    quizRepository.replaceQuestions(testReading.id(), List.of(q1));
    entityManager.flush();
    entityManager.clear();

    var existingQuestions =
        jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(testReading.id());
    UUID q1Id = existingQuestions.get(0).getId();
    UUID opt3Id = existingQuestions.get(0).getOptions().get(2).getId();

    // Record user attempt and answer selecting option 3
    UUID attemptId = UUID.randomUUID();
    var attempt =
        new UserComprehensionAttempt(
            attemptId,
            testUser.id(),
            testReading.id(),
            UUID.randomUUID(),
            BigDecimal.ZERO,
            0,
            1,
            LocalDateTime.now(),
            List.of());
    var answer = new UserComprehensionAnswer(UUID.randomUUID(), attemptId, q1Id, opt3Id, false);
    attemptRepository.recordAttempt(attempt, List.of(answer));
    entityManager.flush();
    entityManager.clear();

    // Attempt to update Q1 with ordinals 1, 2, 4, 5 (omitting option 3 which has historical answer)
    var incomingQ1WithoutOpt3 =
        createValidDomainQuestion(testReading.id(), 1, "Prompt 1", "Exp 1", List.of(1, 2, 4, 5), 1);

    assertThatThrownBy(
            () -> quizRepository.replaceQuestions(testReading.id(), List.of(incomingQ1WithoutOpt3)))
        .isInstanceOf(HistoricalQuizMutationException.class)
        .hasMessageContaining("historical user comprehension answers reference them");

    entityManager.clear();
    // Verify options intact and answer intact
    var reloaded = jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(testReading.id());
    assertThat(reloaded.get(0).getOptions()).hasSize(4);
    assertThat(jpaAnswerRepository.findById(answer.id())).isPresent();
  }

  @Test
  @Transactional
  void testE_noHistoricalAnswersStructuralChangeSafelyDeletesAndAdds() {
    var q1 =
        createValidDomainQuestion(
            testReading.id(), 1, "Original Prompt 1", "Original Exp 1", List.of(1, 2, 3, 4), 1);
    var q2 =
        createValidDomainQuestion(
            testReading.id(),
            2,
            "Original Prompt 2 to delete",
            "Original Exp 2",
            List.of(1, 2, 3, 4),
            1);

    quizRepository.replaceQuestions(testReading.id(), List.of(q1, q2));
    entityManager.flush();
    entityManager.clear();

    var existing = jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(testReading.id());
    UUID originalQ1Id = existing.get(0).getId();
    UUID originalOpt1Id = existing.get(0).getOptions().get(0).getId();
    UUID originalOpt2Id = existing.get(0).getOptions().get(1).getId();
    UUID originalQ2Id = existing.get(1).getId();

    // Structural change with NO historical answers:
    // Q1 keeps options 1 and 2, replaces 3 and 4 with 5 and 6.
    // Q2 removed.
    // Q3 added.
    var modifiedQ1 =
        createValidDomainQuestion(
            testReading.id(), 1, "Prompt 1 updated", "Exp 1 updated", List.of(1, 2, 5, 6), 1);

    var newQ3 =
        createValidDomainQuestion(
            testReading.id(), 3, "Prompt 3 new", "Exp 3 new", List.of(1, 2, 3, 4), 1);

    quizRepository.replaceQuestions(testReading.id(), List.of(modifiedQ1, newQ3));
    entityManager.flush();
    entityManager.clear();

    var reloaded = jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(testReading.id());
    assertThat(reloaded).hasSize(2);

    // Q1 preserved ID
    var reloadedQ1 = reloaded.get(0);
    assertThat(reloadedQ1.getId()).isEqualTo(originalQ1Id);
    assertThat(reloadedQ1.getPrompt()).isEqualTo("Prompt 1 updated");
    assertThat(reloadedQ1.getOptions()).hasSize(4);

    // Q1 Option 1 and 2 preserved IDs
    assertThat(reloadedQ1.getOptions().get(0).getId()).isEqualTo(originalOpt1Id);
    assertThat(reloadedQ1.getOptions().get(1).getId()).isEqualTo(originalOpt2Id);

    // Q1 Option 5 and 6 are newly created
    assertThat(reloadedQ1.getOptions().get(2).getOrdinal()).isEqualTo(5);
    assertThat(reloadedQ1.getOptions().get(3).getOrdinal()).isEqualTo(6);

    // Q2 is completely deleted from repository
    assertThat(jpaQuestionRepository.findById(originalQ2Id)).isEmpty();

    // Q3 is created with a distinct ID
    var reloadedQ3 = reloaded.get(1);
    assertThat(reloadedQ3.getOrdinal()).isEqualTo(3);
    assertThat(reloadedQ3.getId()).isNotEqualTo(originalQ1Id).isNotEqualTo(originalQ2Id);
    assertThat(reloadedQ3.getPrompt()).isEqualTo("Prompt 3 new");
  }

  @Test
  void testF_transactionRollbackPreventsPartialQuizMutation() {
    UUID rId = testReading.id();

    // Step 1: initialize quiz with 2 questions in a committed transaction
    transactionTemplate.executeWithoutResult(
        status -> {
          var q1 =
              createValidDomainQuestion(
                  rId, 1, "Original Prompt 1", "Exp 1", List.of(1, 2, 3, 4), 1);
          var q2 =
              createValidDomainQuestion(
                  rId, 2, "Original Prompt 2", "Exp 2", List.of(1, 2, 3, 4), 1);

          quizRepository.replaceQuestions(rId, List.of(q1, q2));

          var questions = jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(rId);
          UUID q2Id = questions.get(1).getId();
          UUID opt21Id = questions.get(1).getOptions().get(0).getId();

          UUID attemptId = UUID.randomUUID();
          var attempt =
              new UserComprehensionAttempt(
                  attemptId,
                  testUser.id(),
                  rId,
                  UUID.randomUUID(),
                  new BigDecimal("100.00"),
                  1,
                  1,
                  LocalDateTime.now(),
                  List.of());
          var answer =
              new UserComprehensionAnswer(UUID.randomUUID(), attemptId, q2Id, opt21Id, true);
          attemptRepository.recordAttempt(attempt, List.of(answer));
        });

    // Step 2: Attempt replacement that modifies Q1 prompt to PROMPT_SHOULD_ROLLBACK but omits Q2
    // which has an answer -> must fail and rollback!
    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      var modifiedQ1 =
                          createValidDomainQuestion(
                              rId, 1, "PROMPT_SHOULD_ROLLBACK", "Exp 1", List.of(1, 2, 3, 4), 1);

                      quizRepository.replaceQuestions(rId, List.of(modifiedQ1));
                    }))
        .isInstanceOf(HistoricalQuizMutationException.class);

    // Step 3: Verify in a new transaction that Q1 prompt was rolled back and Q2 still exists
    transactionTemplate.executeWithoutResult(
        status -> {
          var questions = jpaQuestionRepository.findByReadingIdOrderByOrdinalAsc(rId);
          assertThat(questions).hasSize(2);
          assertThat(questions.get(0).getPrompt()).isEqualTo("Original Prompt 1");
          assertThat(questions.get(1).getPrompt()).isEqualTo("Original Prompt 2");
        });
  }

  private ComprehensionQuestion createValidDomainQuestion(
      UUID readingId,
      int ordinal,
      String prompt,
      String explanation,
      List<Integer> optionOrdinals,
      int correctOrdinal) {
    UUID qId = UUID.randomUUID();
    var options =
        optionOrdinals.stream()
            .map(
                ord ->
                    new ComprehensionOption(
                        UUID.randomUUID(),
                        qId,
                        ord,
                        prompt + " - Opt " + ord,
                        ord == correctOrdinal))
            .toList();
    return new ComprehensionQuestion(
        qId,
        readingId,
        ordinal,
        QuestionType.FACTUAL,
        prompt,
        explanation,
        LocalDateTime.now(),
        options);
  }
}
