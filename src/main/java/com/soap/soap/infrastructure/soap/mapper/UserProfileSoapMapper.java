package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.command.UpdateMyProfileCommand;
import com.soap.soap.application.model.UserProfile;
import com.soap.soap.infrastructure.soap.generated.GetMyProfileResponse;
import com.soap.soap.infrastructure.soap.generated.UpdateMyProfileRequest;
import com.soap.soap.infrastructure.soap.generated.UpdateMyProfileResponse;
import com.soap.soap.infrastructure.soap.generated.UserProfileType;
import org.springframework.stereotype.Component;

@Component
public class UserProfileSoapMapper {
  public UpdateMyProfileCommand toCommand(UpdateMyProfileRequest request) {
    return new UpdateMyProfileCommand(
        request.getName(),
        request.getAlias(),
        request.getAge(),
        request.getNativeLanguage(),
        request.getLearningLanguage());
  }

  public GetMyProfileResponse toGetResponse(UserProfile profile) {
    var response = new GetMyProfileResponse();
    populate(response, profile);
    return response;
  }

  public UpdateMyProfileResponse toUpdateResponse(UserProfile profile) {
    var response = new UpdateMyProfileResponse();
    populate(response, profile);
    return response;
  }

  private static void populate(UserProfileType response, UserProfile profile) {
    response.setName(profile.name());
    response.setAlias(profile.alias());
    response.setAge(profile.age());
    response.setNativeLanguage(profile.nativeLanguage());
    response.setLearningLanguage(profile.learningLanguage());
    response.setEmail(profile.email());
  }
}
