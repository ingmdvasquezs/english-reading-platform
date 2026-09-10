package com.soap.soap.infrastructure.soap.resolver;

import com.soap.soap.application.exception.DictionaryInvalidResponseException;
import com.soap.soap.application.exception.DictionaryTimeoutException;
import com.soap.soap.application.exception.DictionaryUnavailableException;
import com.soap.soap.application.exception.ExternalProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;

public class SoapExceptionResolver extends SoapFaultMappingExceptionResolver {
  private static final Logger LOGGER = LoggerFactory.getLogger(SoapExceptionResolver.class);

  @Override
  protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception exception) {
    var category = SoapFaultClassifier.category(exception);
    if (exception instanceof DictionaryTimeoutException
        || exception instanceof DictionaryUnavailableException
        || exception instanceof DictionaryInvalidResponseException) {
      LOGGER.warn("soap.fault category={}", category);
      var controlled = new SoapFaultDefinition();
      controlled.setFaultCode(SoapFaultDefinition.SERVER);
      controlled.setFaultStringOrReason(exception.getMessage());
      return controlled;
    }
    if (!SoapFaultClassifier.isClientFault(exception)) {
      if (exception instanceof ExternalProviderException) {
        // Provider observation already recorded the safe failure details; its cause may contain a
        // full URL, so do not duplicate that stack trace here.
        LOGGER.error("soap.fault category={}", category);
      } else {
        LOGGER.error("soap.fault category={}", category, exception);
      }
      var sanitized = new SoapFaultDefinition();
      sanitized.setFaultCode(SoapFaultDefinition.SERVER);
      sanitized.setFaultStringOrReason("Internal server error");
      return sanitized;
    }
    LOGGER.warn("soap.fault category={} type={}", category, exception.getClass().getSimpleName());
    return super.getFaultDefinition(endpoint, exception);
  }
}
