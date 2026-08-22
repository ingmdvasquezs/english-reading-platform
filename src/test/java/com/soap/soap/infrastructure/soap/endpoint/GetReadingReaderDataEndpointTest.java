package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.ReaderToken;
import com.soap.soap.application.model.ReaderTokenType;
import com.soap.soap.application.model.ReadingReaderData;
import com.soap.soap.application.port.in.GetReadingReaderDataPort;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import com.soap.soap.infrastructure.soap.generated.GetReadingReaderDataRequest;
import com.soap.soap.infrastructure.soap.generated.ReaderTokenTypeType;
import com.soap.soap.infrastructure.soap.mapper.GetReadingReaderDataSoapMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetReadingReaderDataEndpointTest {
  @Mock private GetReadingReaderDataPort port;

  @Test
  void mapsTheContractWithoutDictionaryOrUserFields() {
    var id = UUID.randomUUID();
    var request = new GetReadingReaderDataRequest();
    request.setReadingId(id.toString());
    when(port.getReadingReaderData(id))
        .thenReturn(
            new ReadingReaderData(
                id,
                "Title",
                "en",
                List.of(
                    new ReaderToken("Hello", "hello", ReaderTokenType.WORD, VocabularyStatus.KNOWN),
                    new ReaderToken(", ", null, ReaderTokenType.PUNCTUATION, null))));

    var response =
        new GetReadingReaderDataEndpoint(port, new GetReadingReaderDataSoapMapper())
            .getReadingReaderData(request);

    assertThat(response.getReadingId()).isEqualTo(id.toString());
    assertThat(response.getTokens()).hasSize(2);
    assertThat(response.getTokens().getFirst().getType()).isEqualTo(ReaderTokenTypeType.WORD);
    assertThat(response.getTokens().getFirst().getStatus())
        .isEqualTo(com.soap.soap.infrastructure.soap.generated.VocabularyStatusType.KNOWN);
    assertThat(response.getTokens().get(1).getNormalizedValue()).isNull();
    assertThat(response.getTokens().get(1).getStatus()).isNull();
    assertThat(response.getClass().getMethods())
        .noneMatch(
            method ->
                method.getName().matches("get(UserId|Translation|Definition|Phonetic|AudioUrl)"));
  }

  @Test
  void rejectsInvalidUuidAtTheSoapBoundary() {
    var request = new GetReadingReaderDataRequest();
    request.setReadingId("invalid");

    assertThatThrownBy(
            () ->
                new GetReadingReaderDataEndpoint(port, new GetReadingReaderDataSoapMapper())
                    .getReadingReaderData(request))
        .isInstanceOf(InvalidSoapRequestException.class);
  }
}
