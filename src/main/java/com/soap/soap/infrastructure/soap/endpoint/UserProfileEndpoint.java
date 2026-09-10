package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.GetMyProfilePort;
import com.soap.soap.application.port.in.UpdateMyProfilePort;
import com.soap.soap.infrastructure.soap.generated.GetMyProfileRequest;
import com.soap.soap.infrastructure.soap.generated.GetMyProfileResponse;
import com.soap.soap.infrastructure.soap.generated.UpdateMyProfileRequest;
import com.soap.soap.infrastructure.soap.generated.UpdateMyProfileResponse;
import com.soap.soap.infrastructure.soap.mapper.UserProfileSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class UserProfileEndpoint {
  private final GetMyProfilePort getMyProfile;
  private final UpdateMyProfilePort updateMyProfile;
  private final UserProfileSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getMyProfileRequest")
  @ResponsePayload
  public GetMyProfileResponse getMyProfile(@RequestPayload GetMyProfileRequest request) {
    return mapper.toGetResponse(getMyProfile.getMyProfile());
  }

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "updateMyProfileRequest")
  @ResponsePayload
  public UpdateMyProfileResponse updateMyProfile(@RequestPayload UpdateMyProfileRequest request) {
    return mapper.toUpdateResponse(updateMyProfile.updateMyProfile(mapper.toCommand(request)));
  }
}
