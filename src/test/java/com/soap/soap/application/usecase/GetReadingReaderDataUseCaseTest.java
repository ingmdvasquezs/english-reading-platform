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
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.ReaderTextTokenizer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.LocalDateTime;
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
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private CurrentUserPort currentUser;

  private UUID userId;
  private GetReadingReaderDataUseCase useCase;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    useCase =
        new GetReadingReaderDataUseCase(
            readings, vocabulary, new ReaderTextTokenizer(new TextWordProcessor()), currentUser);
  }

  @Test
  void returnsOwnReadingWithAllStatusesUsingOneDeduplicatedBatchQuery() {
    var reading = reading(userId, "Hello hello, Learning ignored NEW.");
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    var values = Set.of("hello", "learning", "ignored", "new");
    when(vocabulary.findStatusesByNormalizedValues(userId, "en", values))
        .thenReturn(
            Map.of(
                "hello", VocabularyStatus.KNOWN,
                "learning", VocabularyStatus.LEARNING,
                "ignored", VocabularyStatus.IGNORED));

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

    var missingId = UUID.randomUUID();
    when(readings.findById(missingId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.getReadingReaderData(missingId))
        .isInstanceOf(ReadingNotFoundException.class);
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
