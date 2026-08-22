package com.soap.soap.application.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReadingSummary(UUID id, String title, String language, LocalDateTime createdAt) {}
