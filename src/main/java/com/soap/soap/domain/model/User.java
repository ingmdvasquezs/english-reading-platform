package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record User(
    UUID id,
    String name,
    String email,
    String passwordHash,
    LocalDateTime createdAt,
    boolean onboardingCompleted,
    String alias,
    Integer age,
    String nativeLanguage,
    String learningLanguage) {
  public User(
      UUID id,
      String name,
      String email,
      String passwordHash,
      LocalDateTime createdAt,
      boolean onboardingCompleted) {
    this(id, name, email, passwordHash, createdAt, onboardingCompleted, null, null, null, "en");
  }

  public User(UUID id, String name, String email, String passwordHash, LocalDateTime createdAt) {
    this(id, name, email, passwordHash, createdAt, false);
  }

  public User(UUID id, String name, String email) {
    this(id, name, email, null, null, false);
  }

  public User updateProfile(
      String name, String alias, Integer age, String nativeLanguage, String learningLanguage) {
    return new User(
        id,
        name,
        email,
        passwordHash,
        createdAt,
        onboardingCompleted,
        alias,
        age,
        nativeLanguage,
        learningLanguage);
  }

  @Override
  public String toString() {
    return "User[id=%s, name=%s, email=%s, createdAt=%s, onboardingCompleted=%s, alias=%s]"
        .formatted(id, name, email, createdAt, onboardingCompleted, alias);
  }
}
