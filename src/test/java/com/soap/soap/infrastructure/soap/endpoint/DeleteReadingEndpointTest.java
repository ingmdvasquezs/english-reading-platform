package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.soap.soap.application.port.in.DeleteReadingPort;
import com.soap.soap.infrastructure.soap.generated.DeleteReadingRequest;
import com.soap.soap.infrastructure.soap.mapper.DeleteReadingSoapMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteReadingEndpointTest {
  @Mock private DeleteReadingPort port;

  @Test
  void mapsReadingIdAndReturnsSuccessWithoutAcceptingAUserId() {
    var readingId = UUID.randomUUID();
    var request = new DeleteReadingRequest();
    request.setReadingId(readingId.toString());

    var response =
        new DeleteReadingEndpoint(port, new DeleteReadingSoapMapper()).deleteReading(request);

    assertThat(DeleteReadingRequest.class.getMethods())
        .noneMatch(method -> method.getName().equals("getUserId"));
    assertThat(response.isSuccess()).isTrue();
    verify(port).deleteReading(readingId);
  }
}
