package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.VocabularyReviewItem;
import com.soap.soap.application.model.VocabularyReviewPreparation;
import com.soap.soap.application.port.in.PrepareVocabularyReviewPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PrepareVocabularyReviewUseCase implements PrepareVocabularyReviewPort {
  private final CurrentUserPort currentUser;
  private final UserVocabularyRepositoryPort vocabulary;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public VocabularyReviewPreparation prepareReview(int size) {
    if (size != 10 && size != 20 && size != 30) {
      throw new InvalidApplicationArgumentException("Review batch size must be 10, 20, or 30");
    }
    var userId = currentUser.requireUserId();
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var dueCount = vocabulary.countDueWords(userId, nowUtc);
    var totalReviewable = vocabulary.countTotalReviewableWords(userId, nowUtc);
    var candidates = vocabulary.findReviewCandidates(userId, nowUtc, size);
    var items =
        candidates.stream()
            .map(
                uv ->
                    new VocabularyReviewItem(
                        uv.word().id(),
                        uv.word().normalizedValue(),
                        uv.word().language(),
                        uv.status()))
            .toList();
    return new VocabularyReviewPreparation(dueCount, totalReviewable, items);
  }
}
