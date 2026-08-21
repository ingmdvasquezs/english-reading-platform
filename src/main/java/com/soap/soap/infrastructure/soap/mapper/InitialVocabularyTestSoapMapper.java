package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.InitialVocabularyTest;
import com.soap.soap.application.model.InitialVocabularyTestResult;
import com.soap.soap.infrastructure.soap.generated.CompleteInitialVocabularyTestRequest;
import com.soap.soap.infrastructure.soap.generated.CompleteInitialVocabularyTestResponse;
import com.soap.soap.infrastructure.soap.generated.GetInitialVocabularyTestResponse;
import org.springframework.stereotype.Component;

@Component
public class InitialVocabularyTestSoapMapper {
  public GetInitialVocabularyTestResponse toResponse(InitialVocabularyTest test) {
    var response = new GetInitialVocabularyTestResponse();
    response.setTestId(test.testId());
    response.setText(test.text());
    response.getSelectableWords().addAll(test.selectableWords());
    return response;
  }

  public CompleteInitialVocabularyTestResponse toResponse(InitialVocabularyTestResult result) {
    var response = new CompleteInitialVocabularyTestResponse();
    response.setConfirmedWordCount(result.confirmedWordCount());
    response.getKnownWords().addAll(result.knownWords());
    response.setMarkedPercentage(result.markedPercentage());
    return response;
  }

  public String testId(CompleteInitialVocabularyTestRequest request) {
    return request.getTestId();
  }
}
