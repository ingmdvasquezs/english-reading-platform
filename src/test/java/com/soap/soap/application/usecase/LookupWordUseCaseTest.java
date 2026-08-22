package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.DictionaryEntry;
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
    when(translation.translate("bridge", "en", "es")).thenReturn("puente");

    var result = useCase().lookupWord(" Bridge ");

    assertThat(result.normalizedWord()).isEqualTo("bridge");
    assertThat(result.translation()).isEqualTo("puente");
    assertThat(result.meanings()).isEqualTo(meanings);
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

  private LookupWordUseCase useCase() {
    return new LookupWordUseCase(dictionary, translation, new TextWordProcessor(), currentUser);
  }
}
