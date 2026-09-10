package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity extends CreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 150)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "onboarding_completed", nullable = false)
  private boolean onboardingCompleted;

  @Column(length = 50)
  private String alias;

  private Integer age;

  @Column(name = "native_language", length = 10)
  private String nativeLanguage;

  @Column(name = "learning_language", nullable = false, length = 10)
  private String learningLanguage;
}
