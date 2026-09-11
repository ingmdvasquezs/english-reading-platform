package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.UserVocabulary;
import java.util.UUID;

public interface RecordVocabularyReviewPort {
  UserVocabulary recordReview(UUID wordId, ReviewAssessment assessment);
}
