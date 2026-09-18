package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.UserVocabulary;
import java.util.UUID;

public interface RecordVocabularyReviewPort {
  UserVocabulary recordReview(UUID wordId, ReviewRating rating);

  default UserVocabulary recordReview(UUID wordId, ReviewAssessment assessment) {
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
