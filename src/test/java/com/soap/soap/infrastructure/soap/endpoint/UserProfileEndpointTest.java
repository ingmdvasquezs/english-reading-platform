package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.UpdateMyProfileCommand;
import com.soap.soap.application.model.UserProfile;
import com.soap.soap.application.port.in.GetMyProfilePort;
import com.soap.soap.application.port.in.UpdateMyProfilePort;
import com.soap.soap.infrastructure.soap.generated.GetMyProfileRequest;
import com.soap.soap.infrastructure.soap.generated.UpdateMyProfileRequest;
import com.soap.soap.infrastructure.soap.mapper.UserProfileSoapMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileEndpointTest {
  @Mock private GetMyProfilePort getProfile;
  @Mock private UpdateMyProfilePort updateProfile;

  @Test
  void contractDoesNotAcceptUserIdAndGetSerializesOptionalFields() {
    var profile = new UserProfile("Ada", null, null, null, "en", "ada@example.com");
    when(getProfile.getMyProfile()).thenReturn(profile);

    var response =
        new UserProfileEndpoint(getProfile, updateProfile, new UserProfileSoapMapper())
            .getMyProfile(new GetMyProfileRequest());

    assertThat(response.getName()).isEqualTo("Ada");
    assertThat(response.getAlias()).isNull();
    assertThat(response.getAge()).isNull();
    assertThat(response.getEmail()).isEqualTo("ada@example.com");
    assertThat(Arrays.stream(GetMyProfileRequest.class.getMethods()))
        .noneMatch(method -> method.getName().equals("getUserId"));
  }

  @Test
  void updateMapsExplicitNullsAndDoesNotExposeEmailInRequest() {
    var request = new UpdateMyProfileRequest();
    request.setName("Ada Lovelace");
    request.setAlias(null);
    request.setAge(null);
    request.setNativeLanguage("es");
    request.setLearningLanguage("en");
    var command = new UpdateMyProfileCommand("Ada Lovelace", null, null, "es", "en");
    var profile = new UserProfile("Ada Lovelace", null, null, "es", "en", "ada@example.com");
    when(updateProfile.updateMyProfile(command)).thenReturn(profile);

    var response =
        new UserProfileEndpoint(getProfile, updateProfile, new UserProfileSoapMapper())
            .updateMyProfile(request);

    assertThat(response.getEmail()).isEqualTo("ada@example.com");
    assertThat(Arrays.stream(UpdateMyProfileRequest.class.getMethods()))
        .noneMatch(method -> method.getName().equals("getEmail"));
    assertThat(Arrays.stream(UpdateMyProfileRequest.class.getMethods()))
        .noneMatch(method -> method.getName().equals("getUserId"));
    verify(updateProfile).updateMyProfile(command);
  }
}
