package com.soap.soap.application.usecase;

import com.soap.soap.application.command.UpdateMyProfileCommand;
import com.soap.soap.application.exception.AliasAlreadyInUseException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.model.UserProfile;
import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import com.soap.soap.application.port.in.UpdateMyProfilePort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.LanguageTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateMyProfileUseCase implements UpdateMyProfilePort {
  private static final int MAX_ALIAS_CHARACTERS = 50;
  private static final int MIN_AGE = 5;
  private static final int MAX_AGE = 120;

  private final CurrentUserPort currentUser;
  private final UserRepositoryPort users;
  private final InputLimits limits;
  private final LanguageAvailabilityPolicy languageAvailabilityPolicy;

  @Override
  @Transactional
  public UserProfile updateMyProfile(UpdateMyProfileCommand command) {
    if (command == null) {
      throw new InvalidApplicationArgumentException("Command must not be null");
    }
    var userId = currentUser.requireUserId();
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    var name = requiredText(command.name(), "Name", limits.maxNameCharacters());
    var alias = optionalText(command.alias(), "Alias", MAX_ALIAS_CHARACTERS);
    var nativeLanguage = optionalLanguageSyntaxOnly(command.nativeLanguage(), "Native language");
    var learningLanguage =
        requiredLearningLanguage(command.learningLanguage(), "Learning language");
    if (command.age() != null && (command.age() < MIN_AGE || command.age() > MAX_AGE)) {
      throw new InvalidApplicationArgumentException("Age must be between 5 and 120");
    }
    if (alias != null && users.existsByAliasIgnoreCaseAndIdNot(alias, userId)) {
      throw new AliasAlreadyInUseException();
    }
    return GetMyProfileUseCase.toProfile(
        users.save(
            user.updateProfile(name, alias, command.age(), nativeLanguage, learningLanguage)));
  }

  private static String requiredText(String value, String field, int maximum) {
    if (value == null || value.isBlank()) {
      throw new InvalidApplicationArgumentException(field + " must not be blank");
    }
    var normalized = value.trim();
    if (normalized.length() > maximum) {
      throw new InvalidApplicationArgumentException(
          field + " must contain at most " + maximum + " characters");
    }
    return normalized;
  }

  private static String optionalText(String value, String field, int maximum) {
    if (value == null) return null;
    return requiredText(value, field, maximum);
  }

  /** Validates BCP-47 syntax only — no availability check (e.g. nativeLanguage). */
  private static String optionalLanguageSyntaxOnly(String value, String field) {
    if (value == null) return null;
    if (value.isBlank()) {
      throw new InvalidApplicationArgumentException(field + " must not be blank");
    }
    try {
      return LanguageTag.of(value).value();
    } catch (IllegalArgumentException e) {
      throw new InvalidApplicationArgumentException(field + " is invalid", e);
    }
  }

  /** Validates BCP-47 syntax AND product availability (learningLanguage). */
  private String requiredLearningLanguage(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidApplicationArgumentException(field + " must not be blank");
    }
    LanguageTag tag;
    try {
      tag = LanguageTag.of(value);
    } catch (IllegalArgumentException e) {
      throw new InvalidApplicationArgumentException(field + " is invalid", e);
    }
    languageAvailabilityPolicy.requireLearningLanguageEnabled(tag);
    return tag.value();
  }
}
