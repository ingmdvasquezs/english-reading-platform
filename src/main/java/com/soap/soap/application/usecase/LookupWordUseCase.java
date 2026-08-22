package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.model.WordLookup;
import com.soap.soap.application.port.in.LookupWordPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DictionaryPort;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.application.service.TextWordProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LookupWordUseCase implements LookupWordPort {
  private final DictionaryPort dictionary;
  private final TranslationPort translation;
  private final TextWordProcessor words;
  private final CurrentUserPort currentUser;
  private final InputLimits limits;

  @Override
  public WordLookup lookupWord(String word) {
    currentUser.requireUserId();
    if (word == null || word.isBlank()) {
      throw new InvalidApplicationArgumentException("Word must not be blank");
    }
    if (word.length() > limits.maxWordCharacters()) {
      throw new InvalidApplicationArgumentException(
          "Word must contain at most " + limits.maxWordCharacters() + " characters");
    }
    var tokens = words.tokenize(word);
    String normalized;
    try {
      normalized = words.normalize(word);
    } catch (IllegalArgumentException exception) {
      throw new InvalidApplicationArgumentException("Word must not be blank");
    }
    if (tokens.size() != 1 || !tokens.getFirst().normalizedValue().equals(normalized)) {
      throw new InvalidApplicationArgumentException("Lookup supports exactly one word");
    }
    var entry = dictionary.lookup(normalized, "en");
    var translated = translation.translate(normalized, "en", "es");
    return new WordLookup(
        entry.word(), normalized, translated, entry.phonetic(), entry.audioUrl(), entry.meanings());
  }
}
