package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.AddWordToVocabularyPort;
import com.soap.soap.infrastructure.soap.generated.AddWordToVocabularyRequest;
import com.soap.soap.infrastructure.soap.generated.AddWordToVocabularyResponse;
import com.soap.soap.infrastructure.soap.mapper.AddWordToVocabularySoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class AddWordToVocabularyEndpoint {
  private final AddWordToVocabularyPort port;
  private final AddWordToVocabularySoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "addWordToVocabularyRequest")
  @ResponsePayload
  public AddWordToVocabularyResponse addWordToVocabulary(
      @RequestPayload AddWordToVocabularyRequest request) {
    return mapper.toResponse(port.addWordToVocabulary(mapper.toCommand(request)));
  }
}
