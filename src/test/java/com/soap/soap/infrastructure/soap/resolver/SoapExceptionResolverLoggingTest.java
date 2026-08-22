package com.soap.soap.infrastructure.soap.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SoapExceptionResolverLoggingTest {

  @Test
  void expectedClientFaultLogsOnlyCategoryAndExceptionType() {
    var logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SoapExceptionResolver.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var resolver = new SoapExceptionResolver();
      var word = "private-vocabulary-word";
      var userId = UUID.randomUUID();
      var wordId = UUID.randomUUID();
      var arbitrarySensitiveMessage =
          "email=private@example.com password=hunter2 jwt=ey.private reading=private-content";

      resolve(resolver, new WordAlreadyInVocabularyException(word));
      resolve(resolver, new VocabularyEntryNotFoundException(userId, wordId));
      resolve(resolver, new InvalidApplicationArgumentException(arbitrarySensitiveMessage));

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .containsExactly(
              "soap.fault category=conflict type=WordAlreadyInVocabularyException",
              "soap.fault category=not_found type=VocabularyEntryNotFoundException",
              "soap.fault category=validation type=InvalidApplicationArgumentException")
          .allSatisfy(
              message ->
                  assertThat(message)
                      .doesNotContain(
                          word,
                          userId.toString(),
                          wordId.toString(),
                          arbitrarySensitiveMessage,
                          "private@example.com",
                          "hunter2",
                          "ey.private",
                          "private-content",
                          "message="));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private static void resolve(SoapExceptionResolver resolver, Exception exception) {
    resolver.getFaultDefinition(null, exception);
  }
}
