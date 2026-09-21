package com.soap.soap.application.port.in;

import com.soap.soap.application.model.VocabularyReviewRecordResult;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.ReviewRating;
import java.util.UUID;

public interface RecordVocabularyReviewPort {
  VocabularyReviewRecordResult recordReview(UUID wordId, ReviewRating rating);

  default VocabularyReviewRecordResult recordReview(UUID wordId, ReviewAssessment assessment) {
    if (assessment == null) {
      return recordReview(wordId, (ReviewRating) null);
    }
    ReviewRating mappedRating =
        switch (assessment) {
          case FORGOT -> ReviewRating.AGAIN;
          case STRUGGLED -> ReviewRating.HARD;
          case REMEMBERED -> ReviewRating.GOOD;
        };
    return recordReview(wordId, mappedRating);
  }
}
