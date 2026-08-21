package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

abstract class SoapMapperSupport {
  private final DatatypeFactory datatypeFactory;

  protected SoapMapperSupport() {
    try {
      datatypeFactory = DatatypeFactory.newInstance();
    } catch (DatatypeConfigurationException exception) {
      throw new IllegalStateException("Unable to initialize XML datatype factory", exception);
    }
  }

  protected UUID parseUuid(String value, String field) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new InvalidSoapRequestException("Invalid " + field + " format: " + value, exception);
    }
  }

  protected XMLGregorianCalendar toXmlDate(LocalDateTime value) {
    return value == null ? null : datatypeFactory.newXMLGregorianCalendar(value.toString());
  }
}
