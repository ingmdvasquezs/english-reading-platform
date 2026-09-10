package com.soap.soap.infrastructure.document;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.application.port.out.DocumentParserPort;
import com.soap.soap.domain.model.DocumentFormat;
import java.nio.file.Path;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class DocumentParserRegistry implements DocumentParserPort {
  private final EpubDocumentParserAdapter epub;
  private final PdfDocumentParserAdapter pdf;

  public DocumentParserRegistry(EpubDocumentParserAdapter epub, PdfDocumentParserAdapter pdf) {
    this.epub = epub;
    this.pdf = pdf;
  }

  @Override
  public ParsedDocument parse(Path source, DocumentFormat format) {
    return switch (format) {
      case EPUB -> epub.parse(source, format);
      case PDF -> pdf.parse(source, format);
      default ->
          throw new DocumentImportException(
              DocumentImportException.Reason.IMPORT_FAILURE, "Unsupported document format");
    };
  }
}
