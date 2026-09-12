package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.port.out.ComprehensionAttemptRepositoryPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
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
                "Daily Life",
                null));
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
}
