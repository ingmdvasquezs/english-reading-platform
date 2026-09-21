package com.soap.soap.application.service;

import com.soap.soap.application.model.LexicalTranslationCandidate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LexicalTranslationSelector {

  private static final Pattern LEADING_TRAILING_PUNCTUATION =
      Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");

  private static final Set<String> STOPWORDS =
      Set.of(
          "a",
          "an",
          "the",
          "of",
          "in",
          "on",
          "at",
          "to",
          "for",
          "with",
          "by",
          "from",
          "or",
          "and",
          "but",
          "as",
          "is",
          "are",
          "was",
          "were",
          "be",
          "been",
          "being",
          "have",
          "has",
          "had",
          "do",
          "does",
          "did",
          "will",
          "would",
          "shall",
          "should",
          "may",
          "might",
          "must",
          "can",
          "could",
          "that",
          "which",
          "who",
          "whom",
          "this",
          "these",
          "those",
          "it",
          "its",
          "not",
          "no",
          "nor",
          "used",
          "indicate");

  public boolean isSuspiciousTranslation(String sourceWord, String translation) {
    if (translation == null || translation.isBlank()) {
      return true;
    }
    var cleanSource = cleanWord(sourceWord);
    var cleanTrans = cleanWord(translation);
    return cleanSource.equalsIgnoreCase(cleanTrans);
  }

  private String cleanWord(String s) {
    if (s == null) {
      return "";
    }
    var trimmed = s.trim();
    return LEADING_TRAILING_PUNCTUATION.matcher(trimmed).replaceAll("").toLowerCase(Locale.ROOT);
  }

  public String selectBestCandidate(
      String sourceWord,
      String dictionaryPos,
      String englishDefinition,
      String exampleTranslation,
      List<LexicalTranslationCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return "";
    }

    var cleanSource = cleanWord(sourceWord);
    var targetPos = mapPos(dictionaryPos);
    var defTokens = extractDefinitionTokens(englishDefinition);
    var cleanExample = cleanWord(exampleTranslation);

    LexicalTranslationCandidate best = null;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (var candidate : candidates) {
      if (candidate == null || candidate.target() == null || candidate.target().isBlank()) {
        continue;
      }
      var cleanTarget = cleanWord(candidate.target());

      double score = candidate.confidence();

      // POS matching
      if (targetPos != null && candidate.posTag() != null) {
        if (targetPos.equalsIgnoreCase(candidate.posTag().trim())) {
          score += 0.5;
        }
      }

      // English definition keyword match in back-translations
      if (!defTokens.isEmpty() && candidate.backTranslations() != null) {
        long matches =
            candidate.backTranslations().stream()
                .filter(
                    bt -> {
                      var cleanBt = cleanWord(bt);
                      return defTokens.contains(cleanBt);
                    })
                .count();
        score += Math.min(1.2, matches * 0.4);
      }

      // Example translation context match
      if (cleanExample != null && !cleanExample.isBlank()) {
        var stem = getStem(cleanTarget);
        if (stem.length() >= 3 && cleanExample.contains(stem)) {
          score += 0.8;
        }
      }

      // If candidate is identical to source word (e.g. hotel)
      if (cleanTarget.equalsIgnoreCase(cleanSource)) {
        boolean hasNonIdentical =
            candidates.stream()
                .anyMatch(
                    c ->
                        c != null
                            && c.target() != null
                            && !cleanWord(c.target()).equalsIgnoreCase(cleanSource));
        if (hasNonIdentical) {
          score -= 1.0;
        }
      }

      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }

    if (best == null) {
      return "";
    }

    var bestTarget = best.target().trim();
    // Invariant: if the best candidate is still identical to the source word,
    // only accept it if it's a confirmed legitimate identical term (e.g. hotel)
    if (cleanWord(bestTarget).equalsIgnoreCase(cleanSource)) {
      if (best.confidence() >= 0.5) {
        return bestTarget;
      }
      return "";
    }

    return bestTarget;
  }

  private String mapPos(String dictionaryPos) {
    if (dictionaryPos == null || dictionaryPos.isBlank()) {
      return null;
    }
    var lower = dictionaryPos.trim().toLowerCase(Locale.ROOT);
    if (lower.startsWith("adj")) return "ADJ";
    if (lower.startsWith("noun")) return "NOUN";
    if (lower.startsWith("verb")) return "VERB";
    if (lower.startsWith("adv")) return "ADV";
    if (lower.startsWith("prep")) return "PREP";
    if (lower.startsWith("conj")) return "CONJ";
    if (lower.startsWith("pron")) return "PRON";
    if (lower.startsWith("interj")) return "INTJ";
    return lower.toUpperCase(Locale.ROOT);
  }

  private Set<String> extractDefinitionTokens(String definition) {
    if (definition == null || definition.isBlank()) {
      return Collections.emptySet();
    }
    return Arrays.stream(definition.split("[^\\p{L}\\p{N}]+"))
        .map(s -> s.toLowerCase(Locale.ROOT).trim())
        .filter(s -> s.length() > 2 && !STOPWORDS.contains(s))
        .collect(Collectors.toSet());
  }

  private String getStem(String word) {
    if (word == null) return "";
    var w = word.trim().toLowerCase(Locale.ROOT);
    if (w.length() <= 4) {
      return w;
    }
    if (w.endsWith("ar")
        || w.endsWith("er")
        || w.endsWith("ir")
        || w.endsWith("as")
        || w.endsWith("es")
        || w.endsWith("os")) {
      return w.substring(0, w.length() - 2);
    }
    if (w.endsWith("a") || w.endsWith("e") || w.endsWith("o")) {
      return w.substring(0, w.length() - 1);
    }
    return w;
  }
}
