package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.EditorialPublicationException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.service.EditorialQuizValidator;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishPlatformReadingUseCaseTest {

  @Mock private ReadingRepositoryPort readings;
  @Mock private ComprehensionQuizRepositoryPort comprehensionQuizzes;
  @Mock private ReadingWordFrequencyRepositoryPort wordFrequencies;

  private LanguageAvailabilityPolicy availabilityPolicy;
  private EditorialQuizValidator quizValidator;
  private PublishPlatformReadingUseCase useCase;

  private UUID readingId;
  private Reading validDraft;
  private ComprehensionQuiz validQuiz;

  @BeforeEach
  void setUp() {
    availabilityPolicy = new LanguageAvailabilityPolicy(Set.of("en"), Set.of("en"));
    quizValidator = new EditorialQuizValidator();
    useCase =
        new PublishPlatformReadingUseCase(
            readings, comprehensionQuizzes, wordFrequencies, availabilityPolicy, quizValidator);

    readingId = UUID.randomUUID();
    validDraft =
        new Reading(
            readingId,
            null,
            "Valid Draft Tale",
            "This is content that is rich and ready for publication.",
            LanguageTag.of("en"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "valid-cover-slug",
            EditorialStatus.DRAFT,
            "Short description here.",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.PEDAGOGICAL_ADAPTATION,
            LanguageTag.of("es"),
            "Title",
            "Author",
            null,
            null,
            "valid-group-key",
            null,
            AccessTier.FREE);

    var questions = new ArrayList<ComprehensionQuestion>();
    QuestionType[] types = {
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA,
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA
    };
    for (int i = 1; i <= 6; i++) {
      UUID qId = UUID.randomUUID();
      var options =
          List.of(
              new ComprehensionOption(UUID.randomUUID(), qId, 1, "Opt 1", true),
              new ComprehensionOption(UUID.randomUUID(), qId, 2, "Opt 2", false),
              new ComprehensionOption(UUID.randomUUID(), qId, 3, "Opt 3", false),
              new ComprehensionOption(UUID.randomUUID(), qId, 4, "Opt 4", false));
      questions.add(
          new ComprehensionQuestion(
              qId,
              readingId,
              i,
              types[i - 1],
              "Prompt " + i,
              "Exp " + i,
              LocalDateTime.now(),
              options));
    }
    validQuiz = new ComprehensionQuiz(readingId, questions);
  }

  @Test
  void successfullyPublishesValidDraft() {
    when(readings.findById(readingId)).thenReturn(Optional.of(validDraft));
    when(comprehensionQuizzes.findByReadingId(readingId)).thenReturn(Optional.of(validQuiz));
    when(wordFrequencies.existsByReadingId(readingId)).thenReturn(true);
    when(readings.save(any(Reading.class))).thenAnswer(inv -> inv.getArgument(0));

    var published = useCase.publish(readingId);

    assertThat(published.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);
    assertThat(published.id()).isEqualTo(readingId);

    ArgumentCaptor<Reading> captor = ArgumentCaptor.forClass(Reading.class);
    verify(readings).save(captor.capture());
    assertThat(captor.getValue().editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);
  }

  @Test
  void rejectsUserReading() {
    var userReading =
        new Reading(
            readingId,
            new User(UUID.randomUUID(), "Owner", "owner@example.com"),
            "User Story",
            "Content",
            LanguageTag.of("en"),
            LocalDateTime.now());

    when(readings.findById(readingId)).thenReturn(Optional.of(userReading));

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(EditorialPublicationException.class)
        .hasMessageContaining("Only PLATFORM readings can be published");
  }

  @Test
  void rejectsAlreadyPublishedReading() {
    var publishedReading =
        new Reading(
            readingId,
            null,
            "Already Published",
            "Content",
            LanguageTag.of("en"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "cover",
            EditorialStatus.PUBLISHED,
            "Desc",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.ORIGINAL,
            null,
            null,
            null,
            null,
            null,
            "group-1",
            null,
            AccessTier.FREE);

    when(readings.findById(readingId)).thenReturn(Optional.of(publishedReading));

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(EditorialPublicationException.class)
        .hasMessageContaining("Only DRAFT readings can be published");
  }

  @Test
  void rejectsMissingCoverKey() {
    var draftWithoutCover =
        new Reading(
            readingId,
            null,
            "No Cover",
            "Content",
            LanguageTag.of("en"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            null, // null cover
            EditorialStatus.DRAFT,
            "Desc",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.ORIGINAL,
            null,
            null,
            null,
            null,
            null,
            "group-1",
            null,
            AccessTier.FREE);

    when(readings.findById(readingId)).thenReturn(Optional.of(draftWithoutCover));

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(EditorialPublicationException.class)
        .hasMessageContaining("Cover key is required for publication");
  }

  @Test
  void rejectsUnknownRightsStatus() {
    var draftWithUnknownRights =
        new Reading(
            readingId,
            null,
            "Unknown Rights",
            "Content",
            LanguageTag.of("en"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "cover",
            EditorialStatus.DRAFT,
            "Desc",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.UNKNOWN, // UNKNOWN rights
            AdaptationKind.ORIGINAL,
            null,
            null,
            null,
            null,
            null,
            "group-1",
            null,
            AccessTier.FREE);

    when(readings.findById(readingId)).thenReturn(Optional.of(draftWithUnknownRights));

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(EditorialPublicationException.class)
        .hasMessageContaining("Rights status must be confirmed (cannot be UNKNOWN)");
  }

  @Test
  void rejectsMissingComprehensionQuizBank() {
    when(readings.findById(readingId)).thenReturn(Optional.of(validDraft));
    when(comprehensionQuizzes.findByReadingId(readingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(EditorialPublicationException.class)
        .hasMessageContaining("Comprehension quiz bank is missing");
  }

  @Test
  void rejectsMissingWordFrequencies() {
    when(readings.findById(readingId)).thenReturn(Optional.of(validDraft));
    when(comprehensionQuizzes.findByReadingId(readingId)).thenReturn(Optional.of(validQuiz));
    when(wordFrequencies.existsByReadingId(readingId)).thenReturn(false);

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(EditorialPublicationException.class)
        .hasMessageContaining("Lexical word frequencies must be indexed before publication");
  }

  @Test
  void rejectsArchivedReading() {
    var archivedReading =
        new Reading(
            readingId,
            null,
            "Archived Reading",
            "Content",
            LanguageTag.of("en"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "cover",
            EditorialStatus.ARCHIVED,
            "Desc",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.ORIGINAL,
            null,
            null,
            null,
            null,
            null,
            "group-1",
            null,
            AccessTier.FREE);

    when(readings.findById(readingId)).thenReturn(Optional.of(archivedReading));

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(EditorialPublicationException.class)
        .hasMessageContaining("Only DRAFT readings can be published, current status: ARCHIVED");
  }

  @Test
  void rejectsDisabledContentLanguage() {
    var frenchDraft =
        new Reading(
            readingId,
            null,
            "French Tale",
            "Du contenu en français.",
            LanguageTag.of("fr"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "valid-cover-slug",
            EditorialStatus.DRAFT,
            "Short description.",
            EditorialContentType.LEGEND,
            "FR",
            EditorialRegion.EUROPE,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.ORIGINAL,
            null,
            null,
            null,
            null,
            null,
            "valid-group-key",
            null,
            AccessTier.FREE);

    when(readings.findById(readingId)).thenReturn(Optional.of(frenchDraft));

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Content language 'fr' is not currently supported");

    var multiLangPolicy = new LanguageAvailabilityPolicy(Set.of("en"), Set.of("en", "fr"));
    var multiLangUseCase =
        new PublishPlatformReadingUseCase(
            readings, comprehensionQuizzes, wordFrequencies, multiLangPolicy, quizValidator);

    when(comprehensionQuizzes.findByReadingId(readingId)).thenReturn(Optional.of(validQuiz));
    when(wordFrequencies.existsByReadingId(readingId)).thenReturn(true);
    when(readings.save(any(Reading.class))).thenAnswer(inv -> inv.getArgument(0));

    var published = multiLangUseCase.publish(readingId);
    assertThat(published.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);
    assertThat(published.language().value()).isEqualTo("fr");
  }

  @Test
  void rejectsInvalidExistingQuizBank() {
    var questions = new ArrayList<ComprehensionQuestion>();
    QuestionType[] types = {
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA,
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA
    };
    for (int i = 1; i <= 6; i++) {
      UUID qId = UUID.randomUUID();
      if (i == 1) {
        // Question 1 has 0 correct options - mock to bypass domain record validation
        var invalidOptions =
            List.of(
                new ComprehensionOption(UUID.randomUUID(), qId, 1, "Opt 1", false),
                new ComprehensionOption(UUID.randomUUID(), qId, 2, "Opt 2", false),
                new ComprehensionOption(UUID.randomUUID(), qId, 3, "Opt 3", false),
                new ComprehensionOption(UUID.randomUUID(), qId, 4, "Opt 4", false));

        var q = mock(ComprehensionQuestion.class);
        when(q.ordinal()).thenReturn(1);
        when(q.questionType()).thenReturn(types[0]);
        when(q.prompt()).thenReturn("Prompt 1");
        when(q.explanation()).thenReturn("Exp 1");
        when(q.options()).thenReturn(invalidOptions);
        questions.add(q);
      } else {
        var validOptions =
            List.of(
                new ComprehensionOption(UUID.randomUUID(), qId, 1, "Opt 1", true),
                new ComprehensionOption(UUID.randomUUID(), qId, 2, "Opt 2", false),
                new ComprehensionOption(UUID.randomUUID(), qId, 3, "Opt 3", false),
                new ComprehensionOption(UUID.randomUUID(), qId, 4, "Opt 4", false));
        questions.add(
            new ComprehensionQuestion(
                qId,
                readingId,
                i,
                types[i - 1],
                "Prompt " + i,
                "Exp " + i,
                LocalDateTime.now(),
                validOptions));
      }
    }
    var invalidQuiz = new ComprehensionQuiz(readingId, questions);

    when(readings.findById(readingId)).thenReturn(Optional.of(validDraft));
    when(comprehensionQuizzes.findByReadingId(readingId)).thenReturn(Optional.of(invalidQuiz));

    assertThatThrownBy(() -> useCase.publish(readingId))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("must have exactly 1 correct option");
  }
}
