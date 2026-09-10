package com.soap.soap.application.command;

import java.nio.file.Path;

public record ImportEpubCommand(Path source, String originalFilename, String languageOverride) {}
