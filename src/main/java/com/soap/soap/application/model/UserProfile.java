package com.soap.soap.application.model;

public record UserProfile(
    String name,
    String alias,
    Integer age,
    String nativeLanguage,
    String learningLanguage,
    String email) {}
