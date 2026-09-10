package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.command.SetVocabularyStatusCommand;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.infrastructure.soap.generated.SetVocabularyStatusRequest;
import com.soap.soap.infrastructure.soap.generated.SetVocabularyStatusResponse;
import org.springframework.stereotype.Component;

@Component
public class SetVocabularyStatusSoapMapper extends VocabularySoapMapperSupport {
  public SetVocabularyStatusCommand toCommand(SetVocabularyStatusRequest request) {
    return new SetVocabularyStatusCommand(
        request.getWord(), request.getLanguage(), toStatus(request.getStatus()));
  }

  public SetVocabularyStatusResponse toResponse(UserVocabulary vocabulary) {
    var response = new SetVocabularyStatusResponse();
    response.setEntry(toEntry(vocabulary));
    return response;
  }
}
