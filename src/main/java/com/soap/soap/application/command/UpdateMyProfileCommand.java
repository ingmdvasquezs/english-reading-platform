package com.soap.soap.application.command;

public record UpdateMyProfileCommand(
    String name, String alias, Integer age, String nativeLanguage, String learningLanguage) {}
