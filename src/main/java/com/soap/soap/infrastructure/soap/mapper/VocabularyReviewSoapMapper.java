package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.VocabularyReviewPreparation;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import com.soap.soap.infrastructure.soap.generated.PrepareVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.PrepareVocabularyReviewResponse;
import com.soap.soap.infrastructure.soap.generated.RatingOptionType;
import com.soap.soap.infrastructure.soap.generated.RecordVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.RecordVocabularyReviewResponse;
import com.soap.soap.infrastructure.soap.generated.ReviewItemType;
import com.soap.soap.infrastructure.soap.generated.ReviewRatingType;
import com.soap.soap.infrastructure.soap.generated.ReviewResultType;
import com.soap.soap.infrastructure.soap.generated.SrsStateType;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VocabularyReviewSoapMapper extends VocabularySoapMapperSupport {

  public int toSize(PrepareVocabularyReviewRequest request) {
    if (request == null) {
      throw new InvalidSoapRequestException("Prepare review request must not be null", null);
    }
    return request.getSize() != null && request.getSize() > 0 ? request.getSize() : 15;
  }

  public PrepareVocabularyReviewResponse toResponse(VocabularyReviewPreparation preparation) {
    var response = new PrepareVocabularyReviewResponse();
    response.setDueCount(preparation.dueCount());
    response.setTotalReviewableCount(preparation.totalReviewableCount());
    for (var item : preparation.entries()) {
      var soapItem = new ReviewItemType();
      soapItem.setWordId(item.wordId().toString());
      soapItem.setWord(item.word());
      soapItem.setLanguage(item.language());
      soapItem.setStatus(VocabularyStatusType.fromValue(item.status().name()));
      if (item.srsState() != null) {
        soapItem.setSrsState(SrsStateType.fromValue(item.srsState().name()));
      }
      if (item.ratingOptions() != null) {
        for (var opt : item.ratingOptions()) {
          var soapOpt = new RatingOptionType();
          soapOpt.setRating(ReviewRatingType.fromValue(opt.rating().name()));
          soapOpt.setNextReviewAt(toXmlDate(opt.nextReviewAt()));
          soapOpt.setIntervalSeconds(opt.intervalSeconds());
          soapItem.getRatingOptions().add(soapOpt);
        }
      }
      response.getEntries().add(soapItem);
    }
    return response;
  }

  public UUID toWordId(RecordVocabularyReviewRequest request) {
    if (request == null) {
      throw new InvalidSoapRequestException("Record review request must not be null", null);
    }
    return parseUuid(request.getWordId(), "wordId");
  }

  public ReviewRating toRating(RecordVocabularyReviewRequest request) {
    if (request == null) {
      throw new InvalidSoapRequestException("Record review request must not be null", null);
    }
    if (request.getRating() != null) {
      try {
        return ReviewRating.valueOf(request.getRating().value());
      } catch (IllegalArgumentException exception) {
        throw new InvalidSoapRequestException(
            "Invalid rating: " + request.getRating().value(), exception);
      }
    }
    if (request.getAssessment() != null) {
      try {
        var assessment = ReviewAssessment.valueOf(request.getAssessment().value());
        return switch (assessment) {
          case FORGOT -> ReviewRating.AGAIN;
          case STRUGGLED -> ReviewRating.HARD;
          case REMEMBERED -> ReviewRating.GOOD;
        };
      } catch (IllegalArgumentException exception) {
        throw new InvalidSoapRequestException(
            "Invalid assessment: " + request.getAssessment().value(), exception);
      }
    }
    throw new InvalidSoapRequestException("Either rating or assessment must be provided", null);
  }

  public ReviewAssessment toAssessment(RecordVocabularyReviewRequest request) {
    if (request == null || request.getAssessment() == null) {
      throw new InvalidSoapRequestException("Assessment must not be null", null);
    }
    try {
      return ReviewAssessment.valueOf(request.getAssessment().value());
    } catch (IllegalArgumentException exception) {
      throw new InvalidSoapRequestException(
          "Invalid assessment: " + request.getAssessment().value(), exception);
    }
  }

  public RecordVocabularyReviewResponse toResponse(UserVocabulary vocabulary) {
    var response = new RecordVocabularyReviewResponse();
    var entry = new ReviewResultType();
    entry.setWordId(vocabulary.word().id().toString());
    entry.setStatus(VocabularyStatusType.fromValue(vocabulary.status().name()));
    if (vocabulary.srsState() != null) {
      entry.setSrsState(SrsStateType.fromValue(vocabulary.srsState().name()));
    }
    if (vocabulary.nextReviewAt() != null) {
      entry.setNextReviewAt(toXmlDate(vocabulary.nextReviewAt()));
    }
    if (vocabulary.lastReviewedAt() != null && vocabulary.nextReviewAt() != null) {
      entry.setIntervalSeconds(
          Math.max(
              0L,
              Duration.between(vocabulary.lastReviewedAt(), vocabulary.nextReviewAt())
                  .toSeconds()));
    }
    entry.setStability(
        BigDecimal.valueOf(vocabulary.stability()).setScale(4, RoundingMode.HALF_UP));
    entry.setDifficulty(
        BigDecimal.valueOf(vocabulary.difficulty()).setScale(4, RoundingMode.HALF_UP));
    response.setEntry(entry);
    return response;
  }
}
