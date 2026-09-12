package com.soap.soap.domain.service;

import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.QuestionType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ComprehensionQuizSelectionPolicy {

  public static final int CURRENT_SELECTION_VERSION = 1;
  public static final int SELECTION_VERSION_1 = 1;
  public static final int SELECTION_VERSION_2 = 2;

  private static final Comparator<ComprehensionQuestion> CANONICAL_ORDER =
      Comparator.comparingInt(ComprehensionQuestion::ordinal)
          .thenComparing(ComprehensionQuestion::id);

  /**
   * Selects exactly 3 questions: 1 FACTUAL (display ordinal 1), 1 INFERENCE (display ordinal 2), 1
   * MAIN_IDEA (display ordinal 3).
   *
   * @param quiz the full comprehension quiz bank
   * @param userId user id
   * @param readingId reading id
   * @param submissionId submission id (must not be null)
   * @param selectionVersion version of the selection policy (e.g. 1 or 2)
   * @return selected 3 questions with display ordinals 1, 2, 3
   */
  public List<ComprehensionQuestion> select(
      ComprehensionQuiz quiz,
      UUID userId,
      UUID readingId,
      UUID submissionId,
      int selectionVersion) {
    Objects.requireNonNull(quiz, "quiz must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(readingId, "readingId must not be null");
    Objects.requireNonNull(submissionId, "submissionId must not be null");

    if (selectionVersion != SELECTION_VERSION_1 && selectionVersion != SELECTION_VERSION_2) {
      throw new IllegalArgumentException("Unsupported selectionVersion: " + selectionVersion);
    }

    int maxOrdinal = (selectionVersion == SELECTION_VERSION_1) ? 3 : 6;

    List<ComprehensionQuestion> eligible =
        quiz.questions().stream().filter(q -> q.ordinal() <= maxOrdinal).toList();

    Map<QuestionType, List<ComprehensionQuestion>> byType =
        eligible.stream().collect(Collectors.groupingBy(ComprehensionQuestion::questionType));

    ComprehensionQuestion factual =
        selectOne(
            byType.get(QuestionType.FACTUAL),
            userId,
            readingId,
            submissionId,
            selectionVersion,
            QuestionType.FACTUAL);
    ComprehensionQuestion inference =
        selectOne(
            byType.get(QuestionType.INFERENCE),
            userId,
            readingId,
            submissionId,
            selectionVersion,
            QuestionType.INFERENCE);
    ComprehensionQuestion mainIdea =
        selectOne(
            byType.get(QuestionType.MAIN_IDEA),
            userId,
            readingId,
            submissionId,
            selectionVersion,
            QuestionType.MAIN_IDEA);

    return List.of(
        withDisplayOrdinal(factual, 1),
        withDisplayOrdinal(inference, 2),
        withDisplayOrdinal(mainIdea, 3));
  }

  private ComprehensionQuestion selectOne(
      List<ComprehensionQuestion> candidates,
      UUID userId,
      UUID readingId,
      UUID submissionId,
      int selectionVersion,
      QuestionType type) {
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalStateException(
          "No candidate questions found for type "
              + type
              + " in selectionVersion "
              + selectionVersion);
    }

    List<ComprehensionQuestion> sorted = candidates.stream().sorted(CANONICAL_ORDER).toList();
    int index =
        computeIndex(userId, readingId, submissionId, selectionVersion, type, sorted.size());
    return sorted.get(index);
  }

  private int computeIndex(
      UUID userId,
      UUID readingId,
      UUID submissionId,
      int selectionVersion,
      QuestionType type,
      int candidateCount) {
    if (candidateCount <= 1) {
      return 0;
    }
    String input =
        userId + ":" + readingId + ":" + submissionId + ":" + selectionVersion + ":" + type.name();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      BigInteger bi = new BigInteger(1, digest);
      return bi.mod(BigInteger.valueOf(candidateCount)).intValue();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not found", e);
    }
  }

  private ComprehensionQuestion withDisplayOrdinal(ComprehensionQuestion q, int displayOrdinal) {
    return new ComprehensionQuestion(
        q.id(),
        q.readingId(),
        displayOrdinal,
        q.questionType(),
        q.prompt(),
        q.explanation(),
        q.createdAt(),
        q.options());
  }
}
