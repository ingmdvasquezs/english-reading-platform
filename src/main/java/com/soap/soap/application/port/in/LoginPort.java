package com.soap.soap.application.port.in;

import com.soap.soap.application.command.LoginCommand;
import com.soap.soap.application.model.AccessToken;

public interface LoginPort {
  AccessToken login(LoginCommand command);
}
