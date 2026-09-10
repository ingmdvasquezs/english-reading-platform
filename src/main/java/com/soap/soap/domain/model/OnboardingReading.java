package com.soap.soap.domain.model;

import java.time.LocalDateTime;

public record OnboardingReading(
    Long id, String title, String content, boolean active, int version, LocalDateTime createdAt) {}
