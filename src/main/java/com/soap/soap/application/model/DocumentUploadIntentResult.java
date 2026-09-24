package com.soap.soap.application.model;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DocumentUploadIntentResult(
    UUID uploadId,
    UUID documentId,
    URI uploadUrl,
    String method,
    Instant expiresAt,
    Map<String, String> requiredHeaders) {}
