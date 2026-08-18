package com.soap.soap.domain.model;

import java.util.UUID;

public record Word(UUID id, String normalizedValue, String language) {}
