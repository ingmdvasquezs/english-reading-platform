package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.ComprehensionQuiz;
import java.util.Optional;
import java.util.UUID;

public interface ComprehensionQuizRepositoryPort {
  Optional<ComprehensionQuiz> findByReadingId(UUID readingId);

  void replaceQuestions(
      UUID readingId, java.util.List<com.soap.soap.domain.model.ComprehensionQuestion> questions);
}
