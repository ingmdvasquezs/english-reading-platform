package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.ListUserVocabularyPort;
import com.soap.soap.infrastructure.soap.generated.ListUserVocabularyRequest;
import com.soap.soap.infrastructure.soap.generated.ListUserVocabularyResponse;
import com.soap.soap.infrastructure.soap.mapper.ListUserVocabularySoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class ListUserVocabularyEndpoint {
  private final ListUserVocabularyPort port;
  private final ListUserVocabularySoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "listUserVocabularyRequest")
  @ResponsePayload
  public ListUserVocabularyResponse listUserVocabulary(
      @RequestPayload ListUserVocabularyRequest request) {
    return mapper.toResponse(port.listUserVocabulary(mapper.toPageRequest(request)));
  }
}
