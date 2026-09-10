package com.soap.soap.application.command;

import java.util.UUID;

public record UpdateDocumentProgressCommand(
    UUID documentId, UUID currentUnitId, boolean completed, Long expectedVersion) {}
