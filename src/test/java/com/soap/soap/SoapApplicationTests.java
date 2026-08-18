package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SoapApplicationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private UserRepositoryPort users;
  @Autowired private WordRepositoryPort words;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private UserVocabularyRepositoryPort vocabulary;

  private User user;

  @BeforeEach
  void createUser() {
    user = users.save(new User(null, "Ada Lovelace", "ada@example.com"));
  }

  @Test
  @Transactional
  void persistsAndLoadsTheReadingAggregate() {
    var reading = readings.save(new Reading(null, user, "A short text", "Hello world", "en", null));

    assertThat(reading.id()).isNotNull();
    assertThat(reading.createdAt()).isNotNull();
    assertThat(readings.findById(reading.id())).contains(reading);
    assertThat(readings.findByUserId(user.id())).containsExactly(reading);
  }

  @Test
  @Transactional
  void treatsTheSameNormalizedValueInDifferentLanguagesAsDifferentWords() {
    var englishWord = words.save(new Word(null, "chat", "en"));
    var frenchWord = words.save(new Word(null, "chat", "fr"));

    assertThat(englishWord.id()).isNotEqualTo(frenchWord.id());
    assertThat(words.findByNormalizedValueAndLanguage("chat", "en")).contains(englishWord);
    assertThat(words.findByNormalizedValueAndLanguage("chat", "fr")).contains(frenchWord);
  }

  @Test
  @Transactional
  void loadsVocabularyWithItsLazyRelationships() {
    var word = words.save(new Word(null, "hello", "en"));
    var firstSeenAt = LocalDateTime.now();
    var saved =
        vocabulary.save(
            new UserVocabulary(null, user, word, VocabularyStatus.LEARNING, firstSeenAt, null));

    assertThat(vocabulary.findByUserIdAndWordId(user.id(), word.id())).contains(saved);
    assertThat(vocabulary.findByUserId(user.id())).containsExactly(saved);
  }
}
