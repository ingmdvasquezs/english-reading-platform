package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.command.AddWordToVocabularyCommand;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.infrastructure.soap.generated.AddWordToVocabularyRequest;
import com.soap.soap.infrastructure.soap.generated.AddWordToVocabularyResponse;
import org.springframework.stereotype.Component;

@Component
public class AddWordToVocabularySoapMapper extends VocabularySoapMapperSupport {
  public AddWordToVocabularyCommand toCommand(AddWordToVocabularyRequest request) {
    return new AddWordToVocabularyCommand(
        request.getWord(), request.getLanguage(), toStatus(request.getInitialStatus()));
  }

  public AddWordToVocabularyResponse toResponse(UserVocabulary vocabulary) {
    var response = new AddWordToVocabularyResponse();
    response.setEntry(toEntry(vocabulary));
    return response;
  }
}
