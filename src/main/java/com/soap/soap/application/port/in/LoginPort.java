package com.soap.soap.application.port.in;

import com.soap.soap.application.command.LoginCommand;
import com.soap.soap.application.model.LoginResult;

public interface LoginPort {
  LoginResult login(LoginCommand command);
}
