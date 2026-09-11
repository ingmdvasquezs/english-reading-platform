package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.VocabularyQuery;
import com.soap.soap.application.model.VocabularySummary;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListUserVocabularyUseCaseTest {

  @Mock private UserRepositoryPort users;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private CurrentUserPort currentUser;

  private final TextWordProcessor wordProcessor = new TextWordProcessor();
  private ListUserVocabularyUseCase useCase;

  private User user;
  private UserVocabulary entry;

  @BeforeEach
  void setUp() {
    useCase = new ListUserVocabularyUseCase(users, vocabulary, currentUser, wordProcessor);
    user = new User(UUID.randomUUID(), "Test User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "journey", "en");
    entry =
        new UserVocabulary(
            UUID.randomUUID(), user, word, VocabularyStatus.LEARNING, LocalDateTime.now(), null);
  }

  @Test
  void listsVocabularyWithoutFiltersAndReturnsSummary() {
    when(currentUser.requireUserId()).thenReturn(user.id());
    when(users.existsById(user.id())).thenReturn(true);

    var pageRequest = new PageRequest(0, 20);
    var pageResult = new PageResult<>(List.of(entry), 0, 20, 1L);
    var summary = new VocabularySummary(10L, 2L, 5L, 2L, 1L);

    when(vocabulary.findByUserIdAndCriteria(user.id(), null, null, pageRequest))
        .thenReturn(pageResult);
    when(vocabulary.countSummaryByUserId(user.id())).thenReturn(summary);

    var query = new VocabularyQuery(pageRequest, null, null);
    var result = useCase.listUserVocabulary(query);

    assertThat(result.page().content()).containsExactly(entry);
    assertThat(result.page().totalElements()).isEqualTo(1L);
    assertThat(result.summary()).isEqualTo(summary);
  }

  @ParameterizedTest
  @EnumSource(VocabularyStatus.class)
  void filtersBySpecificVocabularyStatus(VocabularyStatus status) {
    when(currentUser.requireUserId()).thenReturn(user.id());
    when(users.existsById(user.id())).thenReturn(true);

    var pageRequest = new PageRequest(0, 10);
    var pageResult = new PageResult<>(List.of(entry), 0, 10, 1L);
    var summary = new VocabularySummary(4L, 1L, 1L, 1L, 1L);

    when(vocabulary.findByUserIdAndCriteria(user.id(), status, null, pageRequest))
        .thenReturn(pageResult);
    when(vocabulary.countSummaryByUserId(user.id())).thenReturn(summary);

    var query = new VocabularyQuery(pageRequest, status, null);
    var result = useCase.listUserVocabulary(query);

    verify(vocabulary).findByUserIdAndCriteria(user.id(), status, null, pageRequest);
    assertThat(result.summary().totalCount()).isEqualTo(4L);
  }

  @Test
  void normalizesSearchTermUsingTextWordProcessor() {
    when(currentUser.requireUserId()).thenReturn(user.id());
    when(users.existsById(user.id())).thenReturn(true);

    var pageRequest = new PageRequest(0, 20);
    var pageResult = new PageResult<>(List.of(entry), 0, 20, 1L);
    var summary = new VocabularySummary(1L, 0L, 1L, 0L, 0L);

    when(vocabulary.findByUserIdAndCriteria(user.id(), null, "jour", pageRequest))
        .thenReturn(pageResult);
    when(vocabulary.countSummaryByUserId(user.id())).thenReturn(summary);

    var query = new VocabularyQuery(pageRequest, null, "  Jour  ");
    var result = useCase.listUserVocabulary(query);

    verify(vocabulary).findByUserIdAndCriteria(user.id(), null, "jour", pageRequest);
    assertThat(result.page().content()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t\n"})
  void treatsEmptyOrBlankSearchAsAbsenceOfSearch(String blankSearch) {
    when(currentUser.requireUserId()).thenReturn(user.id());
    when(users.existsById(user.id())).thenReturn(true);

    var pageRequest = new PageRequest(0, 20);
    var pageResult = new PageResult<>(List.of(entry), 0, 20, 1L);
    var summary = new VocabularySummary(1L, 0L, 1L, 0L, 0L);

    when(vocabulary.findByUserIdAndCriteria(user.id(), null, null, pageRequest))
        .thenReturn(pageResult);
    when(vocabulary.countSummaryByUserId(user.id())).thenReturn(summary);

    var query = new VocabularyQuery(pageRequest, null, blankSearch);
    var result = useCase.listUserVocabulary(query);

    verify(vocabulary).findByUserIdAndCriteria(user.id(), null, null, pageRequest);
    assertThat(result.page().content()).hasSize(1);
  }

  @Test
  void supportsCombinedStatusAndSearchCriteria() {
    when(currentUser.requireUserId()).thenReturn(user.id());
    when(users.existsById(user.id())).thenReturn(true);

    var pageRequest = new PageRequest(0, 20);
    var pageResult = new PageResult<>(List.of(entry), 0, 20, 1L);
    var summary = new VocabularySummary(50L, 10L, 20L, 15L, 5L);

    when(vocabulary.findByUserIdAndCriteria(
            user.id(), VocabularyStatus.LEARNING, "jour", pageRequest))
        .thenReturn(pageResult);
    when(vocabulary.countSummaryByUserId(user.id())).thenReturn(summary);

    var query = new VocabularyQuery(pageRequest, VocabularyStatus.LEARNING, "jour");
    var result = useCase.listUserVocabulary(query);

    verify(vocabulary)
        .findByUserIdAndCriteria(user.id(), VocabularyStatus.LEARNING, "jour", pageRequest);
    assertThat(result.page().content()).containsExactly(entry);
    // Crucial: summary reflects global totals (50L), not the filtered page total (1L)
    assertThat(result.summary().totalCount()).isEqualTo(50L);
    assertThat(result.page().totalElements()).isEqualTo(1L);
  }

  @Test
  void throwsUserNotFoundExceptionWhenUserDoesNotExist() {
    var unknownUserId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(unknownUserId);
    when(users.existsById(unknownUserId)).thenReturn(false);

    var query = new VocabularyQuery(new PageRequest(0, 20), null, null);

    assertThatThrownBy(() -> useCase.listUserVocabulary(query))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void throwsInvalidApplicationArgumentExceptionWhenQueryIsNull() {
    assertThatThrownBy(() -> useCase.listUserVocabulary(null))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Vocabulary query must not be null");
  }

  @Test
  void throwsInvalidApplicationArgumentExceptionWhenPageRequestIsInvalid() {
    assertThatThrownBy(() -> new PageRequest(-1, 20))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Page number must not be negative");

    assertThatThrownBy(() -> new PageRequest(0, 0))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Page size must be between 1 and 100");

    assertThatThrownBy(() -> new PageRequest(0, 101))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Page size must be between 1 and 100");
  }

  @Test
  void returnsZeroCountsWhenSummaryHasMissingStatusesOrEmptyVocabulary() {
    when(currentUser.requireUserId()).thenReturn(user.id());
    when(users.existsById(user.id())).thenReturn(true);

    var pageRequest = new PageRequest(0, 20);
    var pageResult = new PageResult<UserVocabulary>(List.of(), 0, 20, 0L);
    var emptySummary = VocabularySummary.empty();

    when(vocabulary.findByUserIdAndCriteria(user.id(), null, null, pageRequest))
        .thenReturn(pageResult);
    when(vocabulary.countSummaryByUserId(user.id())).thenReturn(emptySummary);

    var query = new VocabularyQuery(pageRequest, null, null);
    var result = useCase.listUserVocabulary(query);

    assertThat(result.page().content()).isEmpty();
    assertThat(result.page().totalElements()).isEqualTo(0L);
    assertThat(result.summary().totalCount()).isEqualTo(0L);
    assertThat(result.summary().newCount()).isEqualTo(0L);
    assertThat(result.summary().learningCount()).isEqualTo(0L);
    assertThat(result.summary().knownCount()).isEqualTo(0L);
    assertThat(result.summary().ignoredCount()).isEqualTo(0L);
  }
}
