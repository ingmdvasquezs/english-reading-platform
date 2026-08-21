package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record User(
    UUID id, String name, String email, String passwordHash, LocalDateTime createdAt) {
  public User(UUID id, String name, String email) {
    this(id, name, email, null, null);
  }

  @Override
  public String toString() {
    return "User[id=%s, name=%s, email=%s, createdAt=%s]".formatted(id, name, email, createdAt);
  }
}
