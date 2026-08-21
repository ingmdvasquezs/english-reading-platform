package com.soap.soap.application.model;

public record AccessToken(String value, String tokenType, long expiresIn) {}
