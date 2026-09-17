package com.soap.soap.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.HistoricalQuizMutationException;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionOptionEntity;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionQuestionEntity;
import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import com.soap.soap.infrastructure.persistence.repository.JpaComprehensionQuestionRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaUserComprehensionAnswerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComprehensionQuizPersistenceAdapterTest {

  @Mock private JpaComprehensionQuestionRepository questionRepository;
  @Mock private JpaReadingRepository readingRepository;
  @Mock private JpaUserComprehensionAnswerRepository answerRepository;

  @InjectMocks private ComprehensionQuizPersistenceAdapter adapter;

  private UUID readingId;
  private ReadingEntity readingEntity;

  @BeforeEach
  void setUp() {
    readingId = UUID.randomUUID();
    readingEntity = new ReadingEntity();
    readingEntity.setId(readingId);
    when(readingRepository.findById(readingId)).thenReturn(Optional.of(readingEntity));
  }

  @Test
  @DisplayName("Same structure: question IDs and option IDs are strictly preserved")
  void sameStructurePreservesQuestionAndOptionIds() {
    UUID existingQId = UUID.randomUUID();
    var existingQ = createQuestionEntity(existingQId, 1, "Old prompt", "Old explanation");
    List<UUID> existingOptIds = new ArrayList<>();
    for (int i = 1; i <= 4; i++) {
      UUID optId = UUID.randomUUID();
      existingOptIds.add(optId);
      existingQ.getOptions().add(createOptionEntity(optId, existingQ, i, "Old opt " + i, i == 1));
    }

    when(questionRepository.findByReadingIdOrderByOrdinalAsc(readingId))
        .thenReturn(new ArrayList<>(List.of(existingQ)));

    UUID incomingQId = UUID.randomUUID();
    var incomingQ =
        createDomainQuestion(
            incomingQId,
            readingId,
            1,
            "Updated prompt",
            "Updated explanation",
            List.of(1, 2, 3, 4),
            2);

    adapter.replaceQuestions(readingId, List.of(incomingQ));

    verify(questionRepository, never()).deleteAll(anyList());
    assertThat(existingQ.getId()).isEqualTo(existingQId);
    assertThat(existingQ.getPrompt()).isEqualTo("Updated prompt");
    assertThat(existingQ.getExplanation()).isEqualTo("Updated explanation");

    for (int i = 0; i < 4; i++) {
      assertThat(existingQ.getOptions().get(i).getId()).isEqualTo(existingOptIds.get(i));
      assertThat(existingQ.getOptions().get(i).getContent())
          .isEqualTo("Content for opt " + (i + 1));
      assertThat(existingQ.getOptions().get(i).isCorrect()).isEqualTo((i + 1) == 2);
    }
  }

  @Test
  @DisplayName("Question count decreases with historical answers: operation fails safely")
  void questionCountDecreaseWithHistoricalAnswersThrowsException() {
    UUID q1Id = UUID.randomUUID();
    UUID q2Id = UUID.randomUUID();

    var q1 = createQuestionEntity(q1Id, 1, "Prompt 1", "Exp 1");
    var q2 = createQuestionEntity(q2Id, 2, "Prompt 2", "Exp 2");

    when(questionRepository.findByReadingIdOrderByOrdinalAsc(readingId))
        .thenReturn(new ArrayList<>(List.of(q1, q2)));
    when(answerRepository.existsByQuestionIdIn(List.of(q2Id))).thenReturn(true);

    var incomingQ1 =
        createDomainQuestion(
            UUID.randomUUID(), readingId, 1, "Prompt 1", "Exp 1", List.of(1, 2, 3, 4), 1);

    assertThatThrownBy(() -> adapter.replaceQuestions(readingId, List.of(incomingQ1)))
        .isInstanceOf(HistoricalQuizMutationException.class)
        .hasMessageContaining("historical user comprehension answers reference them");

    verify(questionRepository, never()).deleteAll(anyList());
  }

  @Test
  @DisplayName(
      "Option count decreases or changes with historical answers: fails safely without deleting")
  void optionCountDecreaseWithHistoricalAnswersThrowsException() {
    UUID qId = UUID.randomUUID();
    var q = createQuestionEntity(qId, 1, "Prompt", "Exp");
    UUID opt3Id = UUID.randomUUID();
    for (int i = 1; i <= 4; i++) {
      UUID optId = (i == 3) ? opt3Id : UUID.randomUUID();
      q.getOptions().add(createOptionEntity(optId, q, i, "Opt " + i, i == 1));
    }

    when(questionRepository.findByReadingIdOrderByOrdinalAsc(readingId))
        .thenReturn(new ArrayList<>(List.of(q)));
    // Suppose option ordinal 3 is missing in incoming (ordinals 1, 2, 4, 5) and has historical
    // answers
    when(answerRepository.existsBySelectedOptionIdIn(List.of(opt3Id))).thenReturn(true);

    var incomingQ =
        createDomainQuestion(
            UUID.randomUUID(), readingId, 1, "Prompt", "Exp", List.of(1, 2, 4, 5), 1);

    assertThatThrownBy(() -> adapter.replaceQuestions(readingId, List.of(incomingQ)))
        .isInstanceOf(HistoricalQuizMutationException.class)
        .hasMessageContaining("historical user comprehension answers reference them");

    verify(questionRepository, never()).deleteAll(anyList());
    assertThat(q.getOptions()).hasSize(4);
  }

  @Test
  @DisplayName(
      "No historical answers: structural changes safely delete removed items and add new items")
  void structuralChangesWithoutHistoricalAnswersSucceed() {
    UUID q1Id = UUID.randomUUID();
    UUID q2Id = UUID.randomUUID();

    var q1 = createQuestionEntity(q1Id, 1, "Prompt 1", "Exp 1");
    List<UUID> q1OptIds = new ArrayList<>();
    for (int i = 1; i <= 4; i++) {
      UUID id = UUID.randomUUID();
      q1OptIds.add(id);
      q1.getOptions().add(createOptionEntity(id, q1, i, "Old Opt " + i, i == 1));
    }

    var q2 = createQuestionEntity(q2Id, 2, "Prompt 2 to delete", "Exp 2");

    when(questionRepository.findByReadingIdOrderByOrdinalAsc(readingId))
        .thenReturn(new ArrayList<>(List.of(q1, q2)));
    when(answerRepository.existsByQuestionIdIn(List.of(q2Id))).thenReturn(false);
    when(answerRepository.existsBySelectedOptionIdIn(anyList())).thenReturn(false);

    // Incoming: Q1 keeps ordinals 1, 2, replaces 3, 4 with 5, 6. Q2 removed. Q3 added.
    var incomingQ1 =
        createDomainQuestion(
            UUID.randomUUID(),
            readingId,
            1,
            "Prompt 1 updated",
            "Exp 1 updated",
            List.of(1, 2, 5, 6),
            1);

    var incomingQ3 =
        createDomainQuestion(
            UUID.randomUUID(),
            readingId,
            3,
            "Prompt 3 added",
            "Exp 3 added",
            List.of(1, 2, 3, 4),
            1);

    adapter.replaceQuestions(readingId, List.of(incomingQ1, incomingQ3));

    // Q2 should have been deleted
    verify(questionRepository).deleteAll(List.of(q2));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ComprehensionQuestionEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(questionRepository).saveAll(captor.capture());

    List<ComprehensionQuestionEntity> saved = captor.getValue();
    assertThat(saved).hasSize(2);

    ComprehensionQuestionEntity savedQ1 =
        saved.stream().filter(q -> q.getOrdinal() == 1).findFirst().orElseThrow();
    assertThat(savedQ1.getId()).isEqualTo(q1Id); // Retained ID
    assertThat(savedQ1.getPrompt()).isEqualTo("Prompt 1 updated");
    assertThat(savedQ1.getOptions()).hasSize(4);

    // Ordinals 1 and 2 retained IDs
    assertThat(
            savedQ1.getOptions().stream()
                .filter(o -> o.getOrdinal() == 1)
                .findFirst()
                .get()
                .getId())
        .isEqualTo(q1OptIds.get(0));
    assertThat(
            savedQ1.getOptions().stream()
                .filter(o -> o.getOrdinal() == 2)
                .findFirst()
                .get()
                .getId())
        .isEqualTo(q1OptIds.get(1));

    // Ordinals 5 and 6 have new IDs
    assertThat(
            savedQ1.getOptions().stream()
                .filter(o -> o.getOrdinal() == 5)
                .findFirst()
                .get()
                .getId())
        .isNotNull()
        .isNotIn(q1OptIds);

    ComprehensionQuestionEntity savedQ3 =
        saved.stream().filter(q -> q.getOrdinal() == 3).findFirst().orElseThrow();
    assertThat(savedQ3.getId()).isNotNull().isNotEqualTo(q1Id).isNotEqualTo(q2Id);
  }

  @Test
  @DisplayName("Empty questions with historical answers: throws exception")
  void emptyQuestionsWithHistoricalAnswersThrows() {
    UUID qId = UUID.randomUUID();
    var q = createQuestionEntity(qId, 1, "Prompt", "Exp");
    when(questionRepository.findByReadingIdOrderByOrdinalAsc(readingId))
        .thenReturn(new ArrayList<>(List.of(q)));
    when(answerRepository.existsByQuestionIdIn(List.of(qId))).thenReturn(true);

    assertThatThrownBy(() -> adapter.replaceQuestions(readingId, List.of()))
        .isInstanceOf(HistoricalQuizMutationException.class);
  }

  private ComprehensionQuestion createDomainQuestion(
      UUID id,
      UUID readingId,
      int ordinal,
      String prompt,
      String explanation,
      List<Integer> optionOrdinals,
      int correctOrdinal) {
    var options =
        optionOrdinals.stream()
            .map(
                ord ->
                    new ComprehensionOption(
                        UUID.randomUUID(),
                        id,
                        ord,
                        "Content for opt " + ord,
                        ord == correctOrdinal))
            .toList();
    return new ComprehensionQuestion(
        id,
        readingId,
        ordinal,
        QuestionType.FACTUAL,
        prompt,
        explanation,
        LocalDateTime.now(),
        options);
  }

  private ComprehensionQuestionEntity createQuestionEntity(
      UUID id, int ordinal, String prompt, String explanation) {
    var entity = new ComprehensionQuestionEntity();
    entity.setId(id);
    entity.setReading(readingEntity);
    entity.setOrdinal(ordinal);
    entity.setQuestionType(QuestionType.FACTUAL.name());
    entity.setPrompt(prompt);
    entity.setExplanation(explanation);
    entity.setCreatedAt(LocalDateTime.now());
    return entity;
  }

  private ComprehensionOptionEntity createOptionEntity(
      UUID id, ComprehensionQuestionEntity question, int ordinal, String content, boolean correct) {
    var entity = new ComprehensionOptionEntity();
    entity.setId(id);
    entity.setQuestion(question);
    entity.setOrdinal(ordinal);
    entity.setContent(content);
    entity.setCorrect(correct);
    return entity;
  }
}
