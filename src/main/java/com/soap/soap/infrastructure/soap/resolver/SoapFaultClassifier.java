package com.soap.soap.infrastructure.soap.resolver;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.ConcurrentVocabularyModificationException;
import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.application.exception.ExternalProviderException;
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
import java.util.List;

public final class SoapFaultClassifier {
  private static final List<Class<? extends Exception>> CLIENT_EXCEPTION_TYPES =
      List.of(
          AuthenticationRequiredException.class,
          EmailAlreadyRegisteredException.class,
          ConcurrentVocabularyModificationException.class,
          InvalidApplicationArgumentException.class,
          InvalidCredentialsException.class,
          ReadingAccessDeniedException.class,
          ReadingNotFoundException.class,
          UserNotFoundException.class,
          VocabularyEntryNotFoundException.class,
          WordAlreadyInVocabularyException.class,
          WordNotFoundException.class,
          InvalidVocabularyStateException.class,
          InvalidSoapRequestException.class);

  private SoapFaultClassifier() {}

  public static List<Class<? extends Exception>> clientExceptionTypes() {
    return CLIENT_EXCEPTION_TYPES;
  }

  public static boolean isClientFault(Exception exception) {
    return CLIENT_EXCEPTION_TYPES.stream().anyMatch(type -> type.isInstance(exception));
  }

  public static String category(Exception exception) {
    if (exception instanceof ExternalProviderException) {
      return "external_provider";
    }
    if (exception instanceof AuthenticationRequiredException
        || exception instanceof InvalidCredentialsException) {
      return "authentication";
    }
    if (exception instanceof ConcurrentVocabularyModificationException
        || exception instanceof WordAlreadyInVocabularyException
        || exception instanceof EmailAlreadyRegisteredException) {
      return "conflict";
    }
    if (exception instanceof ReadingAccessDeniedException) {
      return "access_denied";
    }
    if (exception instanceof ReadingNotFoundException
        || exception instanceof UserNotFoundException
        || exception instanceof VocabularyEntryNotFoundException
        || exception instanceof WordNotFoundException) {
      return "not_found";
    }
    return isClientFault(exception) ? "validation" : "internal";
  }
}
