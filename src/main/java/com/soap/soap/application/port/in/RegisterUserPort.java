package com.soap.soap.application.port.in;

import com.soap.soap.application.command.RegisterUserCommand;
import com.soap.soap.domain.model.User;

public interface RegisterUserPort {
  User registerUser(RegisterUserCommand command);
}
