package com.soap.soap.infrastructure.onboarding;

import com.soap.soap.application.model.InitialVocabularyTest;
import com.soap.soap.application.port.out.InitialVocabularyTestSourcePort;
import com.soap.soap.application.service.TextWordProcessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ClasspathInitialVocabularyTestAdapter implements InitialVocabularyTestSourcePort {
  public static final String TEST_ID = "initial-reading-v1";
  private final InitialVocabularyTest test;

  public ClasspathInitialVocabularyTestAdapter(TextWordProcessor processor) {
    try {
      var resource = new ClassPathResource("onboarding/initial-reading.txt");
      var text = resource.getContentAsString(StandardCharsets.UTF_8).trim();
      var words = new LinkedHashSet<String>();
      processor.tokenize(text).forEach(token -> words.add(token.normalizedValue()));
      test = new InitialVocabularyTest(TEST_ID, text, words.stream().toList());
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load initial vocabulary test", exception);
    }
  }

  @Override
  public InitialVocabularyTest load() {
    return test;
  }
}
