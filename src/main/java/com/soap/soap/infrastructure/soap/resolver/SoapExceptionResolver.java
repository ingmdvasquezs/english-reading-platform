package com.soap.soap.infrastructure.soap.resolver;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.ConcurrentVocabularyModificationException;
import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.InvalidCredentialsException;
import com.soap.soap.application.exception.ReadingAccessDeniedException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import com.soap.soap.application.exception.WordNotFoundException;
import com.soap.soap.domain.exception.InvalidVocabularyStateException;
import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;

public class SoapExceptionResolver extends SoapFaultMappingExceptionResolver {
  @Override
  protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception exception) {
    if (!isControlledClientException(exception)) {
      var sanitized = new SoapFaultDefinition();
      sanitized.setFaultCode(SoapFaultDefinition.SERVER);
      sanitized.setFaultStringOrReason("Internal server error");
      return sanitized;
    }
    return super.getFaultDefinition(endpoint, exception);
  }

  private boolean isControlledClientException(Exception exception) {
    return exception instanceof AuthenticationRequiredException
        || exception instanceof EmailAlreadyRegisteredException
        || exception instanceof ConcurrentVocabularyModificationException
        || exception instanceof InvalidApplicationArgumentException
        || exception instanceof InvalidCredentialsException
        || exception instanceof ReadingAccessDeniedException
        || exception instanceof ReadingNotFoundException
        || exception instanceof UserNotFoundException
        || exception instanceof VocabularyEntryNotFoundException
        || exception instanceof WordAlreadyInVocabularyException
        || exception instanceof WordNotFoundException
        || exception instanceof InvalidVocabularyStateException
        || exception instanceof InvalidSoapRequestException;
  }
}
