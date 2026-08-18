package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record Reading(
    UUID id, User user, String title, String content, String language, LocalDateTime createdAt) {}
