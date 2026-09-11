package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.WordNotFoundException;
import com.soap.soap.application.model.DictionaryEntry;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.model.WordDefinition;
import com.soap.soap.application.model.WordMeaning;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DictionaryPort;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.application.service.TextWordProcessor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LookupWordUseCaseTest {
  @Mock private DictionaryPort dictionary;
  @Mock private TranslationPort translation;
  @Mock private CurrentUserPort currentUser;

  @Test
  void combinesDictionaryAndTranslationForOneNormalizedWord() {
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    var meanings =
        List.of(new WordMeaning("noun", List.of(new WordDefinition("a crossing", null))));
    when(dictionary.lookup("bridge", "en"))
        .thenReturn(new DictionaryEntry("bridge", "/brɪdʒ/", "audio", meanings));
    when(translation.translateBatch(List.of("bridge"), "en", "es")).thenReturn(List.of("puente"));

    var result = useCase().lookupWord(" Bridge ");

    assertThat(result.normalizedWord()).isEqualTo("bridge");
    assertThat(result.translation()).isEqualTo("puente");
    assertThat(result.meanings()).isEqualTo(meanings);
  }

  @Test
  void handlesWouldCaseWhereAzureReturnsEmptyTranslation() {
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    var meanings =
        List.of(
            new WordMeaning(
                "verb",
                List.of(
                    new WordDefinition(
                        "used to indicate a future intention", "I would like to travel.", null))));
    when(dictionary.lookup("would", "en"))
        .thenReturn(new DictionaryEntry("would", "/wʊd/", "audio-would", meanings));
    when(translation.translateBatch(List.of("would", "I would like to travel."), "en", "es"))
        .thenReturn(List.of("", "Me gustaría viajar."));

    var result = useCase().lookupWord("would");

    assertThat(result.word()).isEqualTo("would");
    assertThat(result.normalizedWord()).isEqualTo("would");
    assertThat(result.translation()).isEmpty();
    assertThat(result.meanings()).hasSize(1);
    var def = result.meanings().getFirst().definitions().getFirst();
    assertThat(def.example()).isEqualTo("I would like to travel.");
    assertThat(def.exampleTranslation()).isEqualTo("Me gustaría viajar.");
  }

  @Test
  void returnsTranslationWhenDictionaryReportsWordNotFound() {
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    when(dictionary.lookup("slangword", "en")).thenThrow(new WordNotFoundException("slangword"));
    when(translation.translateBatch(List.of("slangword"), "en", "es"))
        .thenReturn(List.of("palabro"));

    var result = useCase().lookupWord("slangword");

    assertThat(result.normalizedWord()).isEqualTo("slangword");
    assertThat(result.translation()).isEqualTo("palabro");
    assertThat(result.meanings()).isEmpty();
  }

  @Test
  void rejectsBlankAndPhraseInputs() {
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(() -> useCase().lookupWord(" "))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(() -> useCase().lookupWord("two words"))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }

  @Test
  void requiresAuthenticationBeforeCallingProviders() {
    when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException());
    assertThatThrownBy(() -> useCase().lookupWord("bridge"))
        .isInstanceOf(AuthenticationRequiredException.class);
    verify(currentUser).requireUserId();
  }

  @Test
  void throwsWhenBothDictionaryAndTranslationFail() {
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    when(dictionary.lookup("missing", "en")).thenThrow(new WordNotFoundException("missing"));
    when(translation.translateBatch(List.of("missing"), "en", "es")).thenReturn(List.of(""));

    assertThatThrownBy(() -> useCase().lookupWord("missing"))
        .isInstanceOf(WordNotFoundException.class);
  }

  private LookupWordUseCase useCase() {
    return new LookupWordUseCase(
        dictionary, translation, new TextWordProcessor(), currentUser, InputLimits.defaults());
  }
}
