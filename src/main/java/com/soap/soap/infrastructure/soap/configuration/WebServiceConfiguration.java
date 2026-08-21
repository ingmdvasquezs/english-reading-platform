package com.soap.soap.infrastructure.soap.configuration;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.InvalidCredentialsException;
import com.soap.soap.application.exception.ReadingAccessDeniedException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import com.soap.soap.domain.exception.InvalidVocabularyStateException;
import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import com.soap.soap.infrastructure.soap.resolver.SoapExceptionResolver;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@Configuration
public class WebServiceConfiguration {

  @Bean
  public XsdSchema readingsSchema() {
    return new SimpleXsdSchema(new ClassPathResource("xsd/readings.xsd"));
  }

  @Bean(name = "readings")
  public DefaultWsdl11Definition readingsWsdl(XsdSchema readingsSchema) {
    var definition = new DefaultWsdl11Definition();
    definition.setPortTypeName("ReadingsPort");
    definition.setLocationUri("/ws");
    definition.setTargetNamespace(NAMESPACE_URI);
    definition.setSchema(readingsSchema);
    return definition;
  }

  @Bean
  public SoapExceptionResolver soapExceptionResolver() {
    var resolver = new SoapExceptionResolver();

    var mappings = new Properties();

    mappings.setProperty(
        UserNotFoundException.class.getName(), SoapFaultDefinition.CLIENT.toString());

    mappings.setProperty(
        ReadingNotFoundException.class.getName(), SoapFaultDefinition.CLIENT.toString());

    mappings.setProperty(
        InvalidApplicationArgumentException.class.getName(), SoapFaultDefinition.CLIENT.toString());

    mappings.setProperty(
        InvalidSoapRequestException.class.getName(), SoapFaultDefinition.CLIENT.toString());

    mappings.setProperty(
        ReadingAccessDeniedException.class.getName(), SoapFaultDefinition.CLIENT.toString());

    mappings.setProperty(
        VocabularyEntryNotFoundException.class.getName(), SoapFaultDefinition.CLIENT.toString());

    mappings.setProperty(
        WordAlreadyInVocabularyException.class.getName(), SoapFaultDefinition.CLIENT.toString());

    mappings.setProperty(
        InvalidVocabularyStateException.class.getName(), SoapFaultDefinition.CLIENT.toString());
    mappings.setProperty(
        AuthenticationRequiredException.class.getName(), SoapFaultDefinition.CLIENT.toString());
    mappings.setProperty(
        EmailAlreadyRegisteredException.class.getName(), SoapFaultDefinition.CLIENT.toString());
    mappings.setProperty(
        InvalidCredentialsException.class.getName(), SoapFaultDefinition.CLIENT.toString());

    resolver.setExceptionMappings(mappings);

    var defaultFault = new SoapFaultDefinition();
    defaultFault.setFaultCode(SoapFaultDefinition.SERVER);

    resolver.setDefaultFault(defaultFault);
    resolver.setOrder(1);

    return resolver;
  }
}
