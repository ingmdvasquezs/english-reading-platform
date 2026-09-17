package com.soap.soap.application.command;

import com.soap.soap.domain.model.EditorialLevel;
import java.util.List;

public record UpdatePublishedEditorialContentCommand(
    String adaptationGroupKey,
    String language,
    EditorialLevel editorialLevel,
    String content,
    List<EditorialQuestionCommand> questions) {}
