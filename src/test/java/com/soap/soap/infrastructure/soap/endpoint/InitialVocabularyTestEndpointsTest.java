package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.InitialVocabularyTest;
import com.soap.soap.application.model.InitialVocabularyTestResult;
import com.soap.soap.application.port.in.CompleteInitialVocabularyTestPort;
import com.soap.soap.application.port.in.GetInitialVocabularyTestPort;
import com.soap.soap.infrastructure.soap.generated.CompleteInitialVocabularyTestRequest;
import com.soap.soap.infrastructure.soap.generated.GetInitialVocabularyTestRequest;
import com.soap.soap.infrastructure.soap.mapper.InitialVocabularyTestSoapMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InitialVocabularyTestEndpointsTest {
  @Mock private GetInitialVocabularyTestPort getPort;
  @Mock private CompleteInitialVocabularyTestPort completePort;
  private final InitialVocabularyTestSoapMapper mapper = new InitialVocabularyTestSoapMapper();

  @Test
  void exposesTheInitialTestWithoutAUserId() {
    when(getPort.getInitialVocabularyTest())
        .thenReturn(new InitialVocabularyTest("v1", "Hello world", List.of("hello", "world")));

    var response =
        new GetInitialVocabularyTestEndpoint(getPort, mapper)
            .get(new GetInitialVocabularyTestRequest());

    assertThat(response.getTestId()).isEqualTo("v1");
    assertThat(response.getSelectableWords()).containsExactly("hello", "world");
    assertThat(GetInitialVocabularyTestRequest.class.getMethods())
        .noneMatch(method -> method.getName().equals("getUserId"));
  }

  @Test
  void completesTheTestThroughTheSoapPort() {
    var request = new CompleteInitialVocabularyTestRequest();
    request.setTestId("v1");
    request.getKnownWords().add("hello");
    when(completePort.completeInitialVocabularyTest("v1", List.of("hello")))
        .thenReturn(new InitialVocabularyTestResult(1, List.of("hello"), 50.0));

    var response =
        new CompleteInitialVocabularyTestEndpoint(completePort, mapper).complete(request);

    assertThat(response.getConfirmedWordCount()).isEqualTo(1);
    assertThat(response.getKnownWords()).containsExactly("hello");
    assertThat(CompleteInitialVocabularyTestRequest.class.getMethods())
        .noneMatch(method -> method.getName().equals("getUserId"));
  }
}
