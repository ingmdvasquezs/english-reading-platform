package com.soap.soap.application.port.in;

import com.soap.soap.application.command.UpdateMyProfileCommand;
import com.soap.soap.application.model.UserProfile;

public interface UpdateMyProfilePort {
  UserProfile updateMyProfile(UpdateMyProfileCommand command);
}
