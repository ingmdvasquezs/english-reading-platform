package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.DictionaryInvalidResponseException;
import com.soap.soap.application.exception.DictionaryTimeoutException;
import com.soap.soap.application.exception.DictionaryUnavailableException;
import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.WordNotFoundException;
import com.soap.soap.application.model.DictionaryEntry;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.model.WordDefinition;
import com.soap.soap.application.model.WordLookup;
import com.soap.soap.application.model.WordMeaning;
import com.soap.soap.application.port.in.LookupWordPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DictionaryPort;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.application.service.LexicalTranslationSelector;
import com.soap.soap.application.service.TextWordProcessor;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LookupWordUseCase implements LookupWordPort {
  private final DictionaryPort dictionary;
  private final TranslationPort translation;
  private final TextWordProcessor words;
  private final CurrentUserPort currentUser;
  private final InputLimits limits;
  private final LexicalTranslationSelector lexicalSelector;

  @Autowired
  public LookupWordUseCase(
      DictionaryPort dictionary,
      TranslationPort translation,
      TextWordProcessor words,
      CurrentUserPort currentUser,
      InputLimits limits,
      LexicalTranslationSelector lexicalSelector) {
    this.dictionary = dictionary;
    this.translation = translation;
    this.words = words;
    this.currentUser = currentUser;
    this.limits = limits;
    this.lexicalSelector = lexicalSelector;
  }

  public LookupWordUseCase(
      DictionaryPort dictionary,
      TranslationPort translation,
      TextWordProcessor words,
      CurrentUserPort currentUser,
      InputLimits limits) {
    this(dictionary, translation, words, currentUser, limits, new LexicalTranslationSelector());
  }

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

    DictionaryEntry entry = null;
    RuntimeException dictionaryException = null;
    try {
      entry = dictionary.lookup(normalized, "en");
    } catch (WordNotFoundException
        | DictionaryUnavailableException
        | DictionaryTimeoutException
        | DictionaryInvalidResponseException exception) {
      dictionaryException = exception;
    }

    String firstExample = null;
    if (entry != null && entry.meanings() != null) {
      outer:
      for (var meaning : entry.meanings()) {
        if (meaning.definitions() != null) {
          for (var def : meaning.definitions()) {
            if (def.example() != null && !def.example().isBlank()) {
              firstExample = def.example();
              break outer;
            }
          }
        }
      }
    }

    var batch = (firstExample != null) ? List.of(normalized, firstExample) : List.of(normalized);
    List<String> translations = null;
    RuntimeException translationException = null;
    try {
      translations = translation.translateBatch(batch, "en", "es");
      if ((translations == null || translations.isEmpty()) && batch.size() == 1) {
        var single = translation.translate(normalized, "en", "es");
        if (single != null && !single.isBlank()) {
          translations = List.of(single);
        }
      }
    } catch (ExternalProviderException exception) {
      translationException = exception;
    }

    var wordTranslation =
        (translations != null && !translations.isEmpty()) ? translations.getFirst() : "";
    if (wordTranslation == null) {
      wordTranslation = "";
    }
    var exampleTranslation =
        (translations != null
                && translations.size() > 1
                && translations.get(1) != null
                && !translations.get(1).isBlank())
            ? translations.get(1)
            : null;

    if (lexicalSelector.isSuspiciousTranslation(normalized, wordTranslation)) {
      String firstPos = null;
      var defTokensBuilder = new StringBuilder();
      if (entry != null && entry.meanings() != null) {
        for (var meaning : entry.meanings()) {
          if (firstPos == null
              && meaning.partOfSpeech() != null
              && !meaning.partOfSpeech().isBlank()) {
            firstPos = meaning.partOfSpeech();
          }
          if (meaning.definitions() != null) {
            for (var def : meaning.definitions()) {
              if (def.definition() != null && !def.definition().isBlank()) {
                defTokensBuilder.append(def.definition()).append(" ");
              }
            }
          }
        }
      }
      try {
        var candidates = translation.lookupLexical(normalized, "en", "es");
        var resolved =
            lexicalSelector.selectBestCandidate(
                normalized, firstPos, defTokensBuilder.toString(), exampleTranslation, candidates);
        if (resolved != null && !resolved.isBlank()) {
          wordTranslation = resolved;
        } else {
          wordTranslation = "";
        }
      } catch (Exception exception) {
        wordTranslation = "";
      }
    }

    if (entry != null) {
      var meanings = entry.meanings();
      if (exampleTranslation != null && firstExample != null) {
        final var targetExample = firstExample;
        final var targetTrans = exampleTranslation;
        meanings =
            entry.meanings().stream()
                .map(
                    meaning -> {
                      var updatedDefs =
                          meaning.definitions().stream()
                              .map(
                                  def -> {
                                    if (targetExample.equals(def.example())) {
                                      return new WordDefinition(
                                          def.definition(), def.example(), targetTrans);
                                    }
                                    return def;
                                  })
                              .toList();
                      return new WordMeaning(meaning.partOfSpeech(), updatedDefs);
                    })
                .toList();
      }
      return new WordLookup(
          entry.word(), normalized, wordTranslation, entry.phonetic(), entry.audioUrl(), meanings);
    }

    if (!wordTranslation.isBlank()) {
      return new WordLookup(normalized, normalized, wordTranslation, null, null, List.of());
    }

    if (dictionaryException != null) {
      throw dictionaryException;
    }
    if (translationException != null) {
      throw translationException;
    }
    throw new WordNotFoundException("Word not found: " + normalized);
  }
}
