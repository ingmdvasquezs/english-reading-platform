package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.UUID;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

abstract class SoapMapperSupport {
  private static final DateTimeFormatter XML_LOCAL_DATE_TIME =
      new DateTimeFormatterBuilder()
          .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
          .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
          .toFormatter();
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
      throw new InvalidSoapRequestException("Invalid " + field + " format", exception);
    }
  }

  protected XMLGregorianCalendar toXmlDate(LocalDateTime value) {
    return value == null
        ? null
        : datatypeFactory.newXMLGregorianCalendar(XML_LOCAL_DATE_TIME.format(value));
  }
}
