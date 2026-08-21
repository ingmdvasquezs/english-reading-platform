package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.infrastructure.soap.generated.ChangeVocabularyStatusRequest;
import com.soap.soap.infrastructure.soap.generated.ChangeVocabularyStatusResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChangeVocabularyStatusSoapMapper extends VocabularySoapMapperSupport {
  public UUID toWordId(ChangeVocabularyStatusRequest request) {
    return parseUuid(request.getWordId(), "wordId");
  }

  public VocabularyStatus toStatus(ChangeVocabularyStatusRequest request) {
    return toStatus(request.getStatus());
  }

  public ChangeVocabularyStatusResponse toResponse(UserVocabulary vocabulary) {
    var response = new ChangeVocabularyStatusResponse();
    response.setEntry(toEntry(vocabulary));
    return response;
  }
}
