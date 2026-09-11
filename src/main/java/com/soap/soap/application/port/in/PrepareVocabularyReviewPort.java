package com.soap.soap.application.port.in;

import com.soap.soap.application.model.VocabularyReviewPreparation;

public interface PrepareVocabularyReviewPort {
  VocabularyReviewPreparation prepareReview(int size);
}
