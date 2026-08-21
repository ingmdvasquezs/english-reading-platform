package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.command.RegisterReadingCommand;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.soap.generated.RegisterReadingRequest;
import com.soap.soap.infrastructure.soap.generated.RegisterReadingResponse;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import org.springframework.stereotype.Component;

@Component
public class RegisterReadingSoapMapper {

  private final DatatypeFactory datatypeFactory;

  public RegisterReadingSoapMapper() {
    try {
      this.datatypeFactory = DatatypeFactory.newInstance();
    } catch (DatatypeConfigurationException exception) {
      throw new IllegalStateException("Unable to initialize XML datatype factory", exception);
    }
  }

  public RegisterReadingCommand toCommand(RegisterReadingRequest request) {
    return new RegisterReadingCommand(
        request.getTitle(), request.getContent(), request.getLanguage());
  }

  public RegisterReadingResponse toResponse(Reading reading) {
    var response = new RegisterReadingResponse();

    response.setReadingId(reading.id().toString());
    response.setTitle(reading.title());
    response.setLanguage(reading.language());

    if (reading.createdAt() != null) {
      response.setCreatedAt(
          datatypeFactory.newXMLGregorianCalendar(reading.createdAt().toString()));
    }

    return response;
  }
}
