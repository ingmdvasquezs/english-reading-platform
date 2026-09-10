package com.soap.soap.application.service;

import com.soap.soap.application.model.RecommendationEvidenceV2;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RecommendationEvidenceV2Calculator {

  public RecommendationEvidenceV2 calculate(
      List<TextWordProcessor.Token> tokens, Map<String, VocabularyStatus> statuses) {
    var unique = new java.util.HashSet<String>();
    var knownTokens = 0;
    var learningTokens = 0;
    var ignoredTokens = 0;
    for (var token : tokens) {
      unique.add(token.normalizedValue());
      switch (statuses.getOrDefault(token.normalizedValue(), VocabularyStatus.NEW)) {
        case KNOWN -> knownTokens++;
        case LEARNING -> learningTokens++;
        case IGNORED -> ignoredTokens++;
        case NEW -> {
          // Token-level NEW and UNCLASSIFIED are intentionally kept out of ease coverage.
        }
      }
    }

    var knownUnique = 0;
    var learningUnique = 0;
    var explicitNewUnique = 0;
    var ignoredUnique = 0;
    var unclassifiedUnique = 0;
    for (var word : unique) {
      if (!statuses.containsKey(word)) {
        unclassifiedUnique++;
        continue;
      }
      switch (statuses.get(word)) {
        case KNOWN -> knownUnique++;
        case LEARNING -> learningUnique++;
        case NEW -> explicitNewUnique++;
        case IGNORED -> ignoredUnique++;
      }
    }
    return new RecommendationEvidenceV2(
        tokens.size(),
        knownTokens,
        learningTokens,
        ignoredTokens,
        unique.size(),
        knownUnique,
        learningUnique,
        explicitNewUnique,
        ignoredUnique,
        unclassifiedUnique);
  }
}
