package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.OnboardingAlreadyCompletedException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.InitialVocabularyTestResult;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.model.VocabularyClassification;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CompleteInitialVocabularyTestUseCase implements CompleteInitialVocabularyTestPort {
  static final int MINIMUM_ONBOARDING_CLASSIFICATIONS = 10;
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
      String testId, Collection<VocabularyClassification> classifications) {
    if (testId == null || classifications == null) {
      throw new InvalidApplicationArgumentException("Test id and classifications must not be null");
    }
    if (classifications.size() > limits.maxOnboardingWords()) {
      throw new InvalidApplicationArgumentException("Too many onboarding classifications");
    }
    var userId = currentUser.requireUserId();
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    if (user.onboardingCompleted()) {
      throw new OnboardingAlreadyCompletedException();
    }
    var test = source.load();
    if (!test.testId().equals(testId)) {
      throw new InvalidApplicationArgumentException("Unknown initial vocabulary test");
    }
    var normalizedClassifications = new java.util.LinkedHashMap<String, VocabularyStatus>();
    for (var classification : classifications) {
      if (classification == null || classification.status() == null) {
        throw new InvalidApplicationArgumentException(
            "Every classification must contain a word and status");
      }
      try {
        var normalized = processor.normalize(classification.word());
        if (normalizedClassifications.putIfAbsent(normalized, classification.status()) != null) {
          throw new InvalidApplicationArgumentException(
              "A word must not be classified more than once");
        }
      } catch (InvalidApplicationArgumentException exception) {
        throw exception;
      } catch (IllegalArgumentException exception) {
        throw new InvalidApplicationArgumentException("Classified words must not be blank");
      }
    }
    if (normalizedClassifications.size() < MINIMUM_ONBOARDING_CLASSIFICATIONS) {
      throw new InvalidApplicationArgumentException(
          "At least "
              + MINIMUM_ONBOARDING_CLASSIFICATIONS
              + " unique vocabulary classifications are required");
    }
    if (!new java.util.HashSet<>(test.selectableWords())
        .containsAll(normalizedClassifications.keySet())) {
      throw new InvalidApplicationArgumentException(
          "Every classified word must belong to the test");
    }
    var resolvedWords = wordResolver.resolveAll(normalizedClassifications.keySet(), "en");
    if (resolvedWords.size() != normalizedClassifications.size()) {
      throw new IllegalStateException("Unable to resolve every onboarding word");
    }
    var existingByWordId =
        vocabulary.findByUserIdAndWordIds(
            userId, resolvedWords.values().stream().map(word -> word.id()).toList());
    var known = new java.util.ArrayList<String>();
    var changes = new java.util.ArrayList<UserVocabulary>();
    var now = LocalDateTime.now(clock);
    for (var classificationEntry : normalizedClassifications.entrySet()) {
      var value = classificationEntry.getKey();
      var status = classificationEntry.getValue();
      var word = resolvedWords.get(value);
      var existing = existingByWordId.get(word.id());
      if (existing != null && existing.status() == status) {
        if (status == VocabularyStatus.KNOWN) {
          known.add(value);
        }
        continue;
      }
      var vocabularyEntry =
          existing != null
              ? existing.changeStatus(status, clock)
              : new UserVocabulary(
                  null, user, word, status, now, status == VocabularyStatus.KNOWN ? now : null);
      changes.add(vocabularyEntry);
      if (status == VocabularyStatus.KNOWN) {
        known.add(value);
      }
    }
    if (!changes.isEmpty()) {
      vocabulary.saveAll(changes);
    }
    if (!users.markOnboardingCompleted(userId)) {
      throw new OnboardingAlreadyCompletedException();
    }
    double percentage =
        test.selectableWords().isEmpty()
            ? 0.0
            : ((double) normalizedClassifications.size() / test.selectableWords().size()) * 100.0;
    return new InitialVocabularyTestResult(normalizedClassifications.size(), known, percentage);
  }
}
