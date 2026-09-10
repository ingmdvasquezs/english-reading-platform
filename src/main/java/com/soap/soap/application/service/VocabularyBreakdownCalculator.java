package com.soap.soap.application.service;

import com.soap.soap.application.model.VocabularyBreakdown;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VocabularyBreakdownCalculator {
  public VocabularyBreakdown calculate(
      Set<String> uniqueWords, Map<String, VocabularyStatus> explicitStatuses) {
    var known = 0;
    var learning = 0;
    var explicitNew = 0;
    var ignored = 0;
    var unclassified = 0;
    for (var word : uniqueWords) {
      if (!explicitStatuses.containsKey(word)) {
        unclassified++;
        continue;
      }
      switch (explicitStatuses.get(word)) {
        case KNOWN -> known++;
        case LEARNING -> learning++;
        case NEW -> explicitNew++;
        case IGNORED -> ignored++;
      }
    }
    return new VocabularyBreakdown(
        uniqueWords.size(), known, learning, explicitNew, ignored, unclassified);
  }
}
