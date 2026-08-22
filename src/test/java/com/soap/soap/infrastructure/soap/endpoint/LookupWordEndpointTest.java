package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.WordDefinition;
import com.soap.soap.application.model.WordLookup;
import com.soap.soap.application.model.WordMeaning;
import com.soap.soap.application.port.in.LookupWordPort;
import com.soap.soap.infrastructure.soap.generated.LookupWordRequest;
import com.soap.soap.infrastructure.soap.mapper.LookupWordSoapMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LookupWordEndpointTest {
  @Mock private LookupWordPort port;

  @Test
  void mapsLookupWithoutExposingProviderDtosOrUserId() {
    var request = new LookupWordRequest();
    request.setWord("bridge");
    var lookup =
        new WordLookup(
            "bridge",
            "bridge",
            "puente",
            "/brɪdʒ/",
            "https://audio.test/bridge.mp3",
            List.of(
                new WordMeaning(
                    "noun", List.of(new WordDefinition("a structure", "cross the bridge")))));
    when(port.lookupWord("bridge")).thenReturn(lookup);

    var response = new LookupWordEndpoint(port, new LookupWordSoapMapper()).lookup(request);

    assertThat(response.getTranslation()).isEqualTo("puente");
    assertThat(response.getPhonetic()).isEqualTo("/brɪdʒ/");
    assertThat(response.getMeanings().getFirst().getDefinitions().getFirst().getExample())
        .isEqualTo("cross the bridge");
    assertThat(LookupWordRequest.class.getMethods())
        .noneMatch(method -> method.getName().equals("getUserId"));
    assertThat(response.getClass().getPackageName())
        .isEqualTo("com.soap.soap.infrastructure.soap.generated");
  }
}
