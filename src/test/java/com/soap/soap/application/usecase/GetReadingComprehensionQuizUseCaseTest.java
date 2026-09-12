package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.ComprehensionNotAvailableException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.service.ComprehensionQuizSelectionPolicy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetReadingComprehensionQuizUseCaseTest {

  @Mock private CurrentUserPort currentUser;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private ComprehensionQuizRepositoryPort quizRepository;

  @org.mockito.Spy
  private ComprehensionQuizSelectionPolicy selectionPolicy = new ComprehensionQuizSelectionPolicy();

  @InjectMocks private GetReadingComprehensionQuizUseCase useCase;

  private UUID userId;
  private UUID readingId;
  private Reading platformReading;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    readingId = UUID.randomUUID();
    platformReading =
        new Reading(
            readingId,
            null,
            "Platform Reading",
            "Content",
            "en",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Education");
  }

  private List<ComprehensionOption> createOptions(UUID qId) {
    return List.of(
        new ComprehensionOption(UUID.randomUUID(), qId, 1, "Opt 1", true),
        new ComprehensionOption(UUID.randomUUID(), qId, 2, "Opt 2", false),
        new ComprehensionOption(UUID.randomUUID(), qId, 3, "Opt 3", false),
        new ComprehensionOption(UUID.randomUUID(), qId, 4, "Opt 4", false));
  }

  @Test
  void returnsQuizWhenPlatformReadingIsCompletedAndQuizExists() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.completed(
                    userId, readingId, LocalDateTime.now().minusHours(1), LocalDateTime.now())));

    UUID qId = UUID.randomUUID();
    var options =
        List.of(
            new ComprehensionOption(UUID.randomUUID(), qId, 1, "Opt 1", true),
            new ComprehensionOption(UUID.randomUUID(), qId, 2, "Opt 2", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 3, "Opt 3", false),
            new ComprehensionOption(UUID.randomUUID(), qId, 4, "Opt 4", false));
    var question =
        new ComprehensionQuestion(
            qId,
            readingId,
            1,
            QuestionType.FACTUAL,
            "What happened?",
            "Detailed explanation.",
            LocalDateTime.now(),
            options);
    when(quizRepository.findByReadingId(readingId))
        .thenReturn(Optional.of(new ComprehensionQuiz(readingId, List.of(question))));

    var result = useCase.getQuiz(readingId);

    assertThat(result.available()).isTrue();
    assertThat(result.readingId()).isEqualTo(readingId);
    assertThat(result.selectionVersion()).isNull();
    assertThat(result.questions()).hasSize(1);
    var qView = result.questions().get(0);
    assertThat(qView.prompt()).isEqualTo("What happened?");
    assertThat(qView.options()).hasSize(4);
  }

  @Test
  void returnsQuizWithVersionWhenSubmissionIdProvided() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.completed(
                    userId, readingId, LocalDateTime.now().minusHours(1), LocalDateTime.now())));

    UUID q1 = UUID.randomUUID();
    UUID q2 = UUID.randomUUID();
    UUID q3 = UUID.randomUUID();
    var questions =
        List.of(
            new ComprehensionQuestion(
                q1,
                readingId,
                1,
                QuestionType.FACTUAL,
                "P1",
                "E1",
                LocalDateTime.now(),
                createOptions(q1)),
            new ComprehensionQuestion(
                q2,
                readingId,
                2,
                QuestionType.INFERENCE,
                "P2",
                "E2",
                LocalDateTime.now(),
                createOptions(q2)),
            new ComprehensionQuestion(
                q3,
                readingId,
                3,
                QuestionType.MAIN_IDEA,
                "P3",
                "E3",
                LocalDateTime.now(),
                createOptions(q3)));
    when(quizRepository.findByReadingId(readingId))
        .thenReturn(Optional.of(new ComprehensionQuiz(readingId, questions)));

    UUID submissionId = UUID.randomUUID();
    var result = useCase.getQuiz(readingId, submissionId);

    assertThat(result.available()).isTrue();
    assertThat(result.readingId()).isEqualTo(readingId);
    assertThat(result.selectionVersion()).isEqualTo(1);
    assertThat(result.questions()).hasSize(3);
    assertThat(result.questions().get(0).ordinal()).isEqualTo(1);
    assertThat(result.questions().get(1).ordinal()).isEqualTo(2);
    assertThat(result.questions().get(2).ordinal()).isEqualTo(3);
  }

  @Test
  void returnsAvailableFalseWhenPlatformReadingIsCompletedButNoQuizConfigured() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.completed(
                    userId, readingId, LocalDateTime.now().minusHours(1), LocalDateTime.now())));
    when(quizRepository.findByReadingId(readingId))
        .thenReturn(Optional.of(new ComprehensionQuiz(readingId, List.of())));

    var result = useCase.getQuiz(readingId);

    assertThat(result.available()).isFalse();
    assertThat(result.readingId()).isEqualTo(readingId);
    assertThat(result.questions()).isEmpty();
  }

  @Test
  void throwsExceptionWhenReadingIsNotCompleted() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.inProgress(
                    userId, readingId, LocalDateTime.now().minusMinutes(10))));

    assertThatThrownBy(() -> useCase.getQuiz(readingId))
        .isInstanceOf(ComprehensionNotAvailableException.class)
        .hasMessageContaining("requires the reading to be completed");

    verifyNoInteractions(quizRepository);
  }

  @Test
  void throwsExceptionWhenReadingProgressDoesNotExist() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.getQuiz(readingId))
        .isInstanceOf(ComprehensionNotAvailableException.class)
        .hasMessageContaining("requires the reading to be completed");

    verifyNoInteractions(quizRepository);
  }

  @Test
  void throwsExceptionWhenReadingIsNotPlatform() {
    var userReading =
        new Reading(
            readingId,
            new User(userId, "User", "u@example.com", "hash", null),
            "My Reading",
            "User content",
            "en",
            LocalDateTime.now());
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(userReading));

    assertThatThrownBy(() -> useCase.getQuiz(readingId))
        .isInstanceOf(ComprehensionNotAvailableException.class)
        .hasMessageContaining("only available for platform readings");

    verifyNoInteractions(progress, quizRepository);
  }

  @Test
  void throwsExceptionWhenReadingNotFound() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.getQuiz(readingId))
        .isInstanceOf(ReadingNotFoundException.class);

    verifyNoInteractions(progress, quizRepository);
  }

  @Test
  void throwsExceptionWhenReadingIdIsNull() {
    assertThatThrownBy(() -> useCase.getQuiz(null))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }
}
