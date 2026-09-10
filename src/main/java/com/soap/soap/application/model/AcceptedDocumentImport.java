package com.soap.soap.application.model;

import com.soap.soap.domain.model.DocumentImportStatus;
import java.util.UUID;

public record AcceptedDocumentImport(UUID documentId, DocumentImportStatus status) {}
