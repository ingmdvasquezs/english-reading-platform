package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.ReaderContentPreparer;
import com.soap.soap.application.service.ReaderTextTokenizer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetReadingReaderDataUseCaseTest {
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private CurrentUserPort currentUser;

  private UUID userId;
  private GetReadingReaderDataUseCase useCase;
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-30T16:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    org.mockito.Mockito.lenient()
        .when(progress.startIfAbsent(any(), any(), any()))
        .thenAnswer(
            invocation ->
                ReadingProgress.inProgress(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)));
    useCase =
        new GetReadingReaderDataUseCase(
            readings,
            progress,
            new ReaderContentPreparer(new ReaderTextTokenizer(new TextWordProcessor()), vocabulary),
            new com.soap.soap.application.service.ReadingEditorialAccessPolicy(progress),
            currentUser,
            CLOCK);
  }

  @Test
  void returnsOwnReadingWithAllStatusesUsingOneDeduplicatedBatchQuery() {
    var reading = reading(userId, "Hello hello, Learning ignored NEW absent.");
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    var values = Set.of("hello", "learning", "ignored", "new", "absent");
    when(vocabulary.findStatusesByNormalizedValues(userId, "en", values))
        .thenReturn(
            Map.of(
                "hello", VocabularyStatus.KNOWN,
                "learning", VocabularyStatus.LEARNING,
                "ignored", VocabularyStatus.IGNORED,
                "new", VocabularyStatus.NEW));

    var data = useCase.getReadingReaderData(reading.id());

    assertThat(data.readingId()).isEqualTo(reading.id());
    assertThat(data.title()).isEqualTo("Title");
    assertThat(data.tokens().stream().map(token -> token.value()).collect(Collectors.joining()))
        .isEqualTo(reading.content());
    assertThat(
            data.tokens().stream()
                .filter(token -> "hello".equals(token.normalizedValue()))
                .map(token -> token.status()))
        .containsExactly(VocabularyStatus.KNOWN, VocabularyStatus.KNOWN);
    assertThat(statusOf(data, "learning")).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(statusOf(data, "ignored")).isEqualTo(VocabularyStatus.IGNORED);
    assertThat(statusOf(data, "new")).isEqualTo(VocabularyStatus.NEW);
    assertThat(statusOf(data, "absent")).isNull();
    verify(vocabulary).findStatusesByNormalizedValues(userId, "en", values);
  }

  @Test
  void hidesAnotherUsersReadingAndHandlesMissingReadingIdentically() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var foreign = reading(UUID.randomUUID(), "Hello");
    when(readings.findById(foreign.id())).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> useCase.getReadingReaderData(foreign.id()))
        .isInstanceOf(ReadingNotFoundException.class);
    verify(vocabulary, never()).findStatusesByNormalizedValues(any(), any(), any());
    verify(progress, never()).startIfAbsent(any(), any(), any());

    var missingId = UUID.randomUUID();
    when(readings.findById(missingId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.getReadingReaderData(missingId))
        .isInstanceOf(ReadingNotFoundException.class);
  }

  @Test
  void reopeningACompletedReadingKeepsItCompleted() {
    var reading = reading(userId, "Hello");
    var startedAt = LocalDateTime.parse("2026-08-30T09:00:00");
    var completedAt = startedAt.plusMinutes(10);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    when(progress.startIfAbsent(userId, reading.id(), LocalDateTime.now(CLOCK)))
        .thenReturn(ReadingProgress.completed(userId, reading.id(), startedAt, completedAt));
    when(vocabulary.findStatusesByNormalizedValues(userId, "en", Set.of("hello")))
        .thenReturn(Map.of());

    var data = useCase.getReadingReaderData(reading.id());

    assertThat(data.progressStatus()).isEqualTo(ReadingProgressStatus.COMPLETED);
    verify(progress).startIfAbsent(userId, reading.id(), LocalDateTime.now(CLOCK));
  }

  @Test
  void requiresAuthenticationBeforeLoadingAReading() {
    when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(() -> useCase.getReadingReaderData(UUID.randomUUID()))
        .isInstanceOf(AuthenticationRequiredException.class);
    verify(readings, never()).findById(any());
  }

  @Test
  void skipsVocabularyQueryWhenThereAreNoWords() {
    var reading = reading(userId, "...  \n");
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));

    assertThat(useCase.getReadingReaderData(reading.id()).tokens()).isNotEmpty();
    verify(vocabulary, never()).findStatusesByNormalizedValues(any(), any(), any());
  }

  @Test
  void returnsPlatformReadingDataUsingTheAuthenticatedUsersVocabulary() {
    var reading =
        new Reading(
            UUID.randomUUID(),
            null,
            "Platform",
            "Hello world",
            "en",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Science",
            com.soap.soap.domain.model.EditorialStatus.PUBLISHED);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    when(vocabulary.findStatusesByNormalizedValues(userId, "en", Set.of("hello", "world")))
        .thenReturn(Map.of("hello", VocabularyStatus.KNOWN));

    var data = useCase.getReadingReaderData(reading.id());

    assertThat(data.title()).isEqualTo("Platform");
    assertThat(statusOf(data, "hello")).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(statusOf(data, "world")).isNull();
  }

  @Test
  void preservesPunctuationWhitespaceCaseAndApostrophesExactly() {
    var content = "Learning, learning. \"WORD\" isn't\njust punctuation!";
    var reading = reading(userId, content);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    when(vocabulary.findStatusesByNormalizedValues(
            userId, "en", Set.of("learning", "word", "isn't", "just", "punctuation")))
        .thenReturn(Map.of("learning", VocabularyStatus.LEARNING));

    var data = useCase.getReadingReaderData(reading.id());

    assertThat(data.tokens().stream().map(token -> token.value()).collect(Collectors.joining()))
        .isEqualTo(content);
    assertThat(
            data.tokens().stream()
                .filter(token -> "learning".equals(token.normalizedValue()))
                .map(token -> token.status()))
        .containsExactly(VocabularyStatus.LEARNING, VocabularyStatus.LEARNING);
    assertThat(
            data.tokens().stream()
                .filter(
                    token -> token.type() != com.soap.soap.application.model.ReaderTokenType.WORD)
                .allMatch(token -> token.normalizedValue() == null && token.status() == null))
        .isTrue();
  }

  @Test
  void samePlatformReadingUsesEachAuthenticatedUsersOwnStatuses() {
    var otherUserId = UUID.randomUUID();
    var reading =
        new Reading(
            UUID.randomUUID(),
            null,
            "Shared",
            "Learning learning",
            "en",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.A2,
            "Education",
            com.soap.soap.domain.model.EditorialStatus.PUBLISHED);
    when(currentUser.requireUserId()).thenReturn(userId, otherUserId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    when(vocabulary.findStatusesByNormalizedValues(userId, "en", Set.of("learning")))
        .thenReturn(Map.of("learning", VocabularyStatus.KNOWN));
    when(vocabulary.findStatusesByNormalizedValues(otherUserId, "en", Set.of("learning")))
        .thenReturn(Map.of("learning", VocabularyStatus.LEARNING));

    var first = useCase.getReadingReaderData(reading.id());
    var second = useCase.getReadingReaderData(reading.id());

    assertThat(
            first.tokens().stream()
                .filter(token -> token.normalizedValue() != null)
                .map(token -> token.status()))
        .containsOnly(VocabularyStatus.KNOWN);
    assertThat(
            second.tokens().stream()
                .filter(token -> token.normalizedValue() != null)
                .map(token -> token.status()))
        .containsOnly(VocabularyStatus.LEARNING);
  }

  private Reading reading(UUID ownerId, String content) {
    return new Reading(
        UUID.randomUUID(),
        new User(ownerId, "Ada", "ada@example.com"),
        "Title",
        content,
        "en",
        LocalDateTime.now());
  }

  private VocabularyStatus statusOf(
      com.soap.soap.application.model.ReadingReaderData data, String normalizedValue) {
    return data.tokens().stream()
        .filter(token -> normalizedValue.equals(token.normalizedValue()))
        .findFirst()
        .orElseThrow()
        .status();
  }
}
