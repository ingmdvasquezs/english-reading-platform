package com.soap.soap.application.command;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;

public record RegisterReadingCommand(String title, String content, String language) {

  public RegisterReadingCommand {
    title = requireText(title, "Title");
    content = requireText(content, "Content");
    language = requireText(language, "Language");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidApplicationArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
