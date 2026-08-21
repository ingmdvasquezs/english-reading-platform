package com.soap.soap.infrastructure.soap.interceptor;

import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import java.util.Set;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.interceptor.EndpointInterceptorAdapter;
import org.springframework.ws.soap.SoapMessage;
import org.w3c.dom.Element;

public class VocabularyStatusPayloadInterceptor extends EndpointInterceptorAdapter {
  private static final Set<String> ALLOWED_STATUSES = Set.of("NEW", "LEARNING", "KNOWN", "IGNORED");

  @Override
  public boolean handleRequest(MessageContext messageContext, Object endpoint) {
    var request = (SoapMessage) messageContext.getRequest();
    var result = new DOMResult();
    try {
      TransformerFactory.newInstance()
          .newTransformer()
          .transform(request.getPayloadSource(), result);
    } catch (TransformerException exception) {
      throw new InvalidSoapRequestException("Cannot read SOAP request payload", exception);
    }

    var payload = (Element) result.getNode().getFirstChild();
    String fieldName = statusFieldName(payload.getLocalName());
    if (fieldName == null) {
      return true;
    }

    var fields = payload.getElementsByTagNameNS(payload.getNamespaceURI(), fieldName);
    if (fields.getLength() == 0 || fields.item(0).getTextContent().isBlank()) {
      throw new InvalidSoapRequestException("Vocabulary status must not be null", null);
    }
    var value = fields.item(0).getTextContent().trim();
    if (!ALLOWED_STATUSES.contains(value)) {
      throw new InvalidSoapRequestException("Invalid vocabulary status: " + value, null);
    }
    return true;
  }

  private String statusFieldName(String payloadName) {
    return switch (payloadName) {
      case "changeVocabularyStatusRequest" -> "status";
      case "addWordToVocabularyRequest" -> "initialStatus";
      default -> null;
    };
  }
}
