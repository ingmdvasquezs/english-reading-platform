package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.VocabularyReviewPreparation;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import com.soap.soap.infrastructure.soap.generated.PrepareVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.PrepareVocabularyReviewResponse;
import com.soap.soap.infrastructure.soap.generated.RecordVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.RecordVocabularyReviewResponse;
import com.soap.soap.infrastructure.soap.generated.ReviewItemType;
import com.soap.soap.infrastructure.soap.generated.ReviewResultType;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VocabularyReviewSoapMapper extends VocabularySoapMapperSupport {

  public int toSize(PrepareVocabularyReviewRequest request) {
    if (request == null) {
      throw new InvalidSoapRequestException("Prepare review request must not be null", null);
    }
    return request.getSize();
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
    response.setEntry(entry);
    return response;
  }
}
