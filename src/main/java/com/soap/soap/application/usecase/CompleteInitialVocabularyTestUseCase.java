package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.InitialVocabularyTestResult;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.port.in.CompleteInitialVocabularyTestPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.InitialVocabularyTestSourcePort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.WordResolver;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CompleteInitialVocabularyTestUseCase implements CompleteInitialVocabularyTestPort {
  private final UserRepositoryPort users;
  private final UserVocabularyRepositoryPort vocabulary;
  private final InitialVocabularyTestSourcePort source;
  private final TextWordProcessor processor;
  private final WordResolver wordResolver;
  private final Clock clock;
  private final CurrentUserPort currentUser;
  private final InputLimits limits;

  @Override
  @Transactional
  public InitialVocabularyTestResult completeInitialVocabularyTest(
      String testId, Collection<String> knownWords) {
    if (testId == null || knownWords == null) {
      throw new InvalidApplicationArgumentException("Test id and known words must not be null");
    }
    if (knownWords.size() > limits.maxOnboardingWords()) {
      throw new InvalidApplicationArgumentException("Too many selected onboarding words");
    }
    var userId = currentUser.requireUserId();
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    var test = source.load();
    if (!test.testId().equals(testId)) {
      throw new InvalidApplicationArgumentException("Unknown initial vocabulary test");
    }
    var selected = new LinkedHashSet<String>();
    try {
      knownWords.forEach(word -> selected.add(processor.normalize(word)));
    } catch (IllegalArgumentException exception) {
      throw new InvalidApplicationArgumentException("Known words must not be blank");
    }
    if (!new java.util.HashSet<>(test.selectableWords()).containsAll(selected)) {
      throw new InvalidApplicationArgumentException("Every selected word must belong to the test");
    }
    var confirmed = new java.util.ArrayList<String>();
    for (var value : selected) {
      var word = wordResolver.resolve(value, "en");
      var existing = vocabulary.findByUserIdAndWordId(userId, word.id());
      if (existing.isPresent() && existing.get().status() == VocabularyStatus.IGNORED) {
        continue;
      }
      if (existing.isPresent() && existing.get().status() == VocabularyStatus.KNOWN) {
        confirmed.add(value);
        continue;
      }
      var now = LocalDateTime.now(clock);
      var entry =
          existing
              .map(current -> current.changeStatus(VocabularyStatus.KNOWN, clock))
              .orElseGet(
                  () -> new UserVocabulary(null, user, word, VocabularyStatus.KNOWN, now, now));
      vocabulary.save(entry);
      confirmed.add(value);
    }
    double percentage =
        test.selectableWords().isEmpty()
            ? 0.0
            : ((double) selected.size() / test.selectableWords().size()) * 100.0;
    return new InitialVocabularyTestResult(confirmed.size(), confirmed, percentage);
  }
}
