package com.soap.soap.application.port.out;

import com.soap.soap.application.model.AccessToken;
import com.soap.soap.domain.model.User;

public interface TokenProviderPort {
  AccessToken create(User user);
}
