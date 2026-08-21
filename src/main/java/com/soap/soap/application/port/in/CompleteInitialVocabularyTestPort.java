package com.soap.soap.application.port.in;

import com.soap.soap.application.model.InitialVocabularyTestResult;
import java.util.Collection;

public interface CompleteInitialVocabularyTestPort {
  InitialVocabularyTestResult completeInitialVocabularyTest(
      String testId, Collection<String> knownWords);
}
