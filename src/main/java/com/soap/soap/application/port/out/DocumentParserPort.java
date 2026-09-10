package com.soap.soap.application.port.out;

import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.domain.model.DocumentFormat;
import java.nio.file.Path;

public interface DocumentParserPort {
  ParsedDocument parse(Path source, DocumentFormat format);
}
