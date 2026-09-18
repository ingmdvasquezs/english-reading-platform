package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.PrepareVocabularyReviewPort;
import com.soap.soap.application.port.in.RecordVocabularyReviewPort;
import com.soap.soap.infrastructure.soap.generated.PrepareVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.PrepareVocabularyReviewResponse;
import com.soap.soap.infrastructure.soap.generated.RecordVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.RecordVocabularyReviewResponse;
import com.soap.soap.infrastructure.soap.mapper.VocabularyReviewSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class VocabularyReviewEndpoint {
  private final PrepareVocabularyReviewPort preparePort;
  private final RecordVocabularyReviewPort recordPort;
  private final VocabularyReviewSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "prepareVocabularyReviewRequest")
  @ResponsePayload
  public PrepareVocabularyReviewResponse prepareVocabularyReview(
      @RequestPayload PrepareVocabularyReviewRequest request) {
    return mapper.toResponse(preparePort.prepareReview(mapper.toSize(request)));
  }

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "recordVocabularyReviewRequest")
  @ResponsePayload
  public RecordVocabularyReviewResponse recordVocabularyReview(
      @RequestPayload RecordVocabularyReviewRequest request) {
    return mapper.toResponse(
        recordPort.recordReview(mapper.toWordId(request), mapper.toRating(request)));
  }
}
