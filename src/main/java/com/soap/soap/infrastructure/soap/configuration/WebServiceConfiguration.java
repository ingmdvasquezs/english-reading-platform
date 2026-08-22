package com.soap.soap.infrastructure.soap.configuration;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.infrastructure.soap.resolver.SoapExceptionResolver;
import com.soap.soap.infrastructure.soap.resolver.SoapFaultClassifier;
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

    SoapFaultClassifier.clientExceptionTypes()
        .forEach(
            type -> mappings.setProperty(type.getName(), SoapFaultDefinition.CLIENT.toString()));

    resolver.setExceptionMappings(mappings);

    var defaultFault = new SoapFaultDefinition();
    defaultFault.setFaultCode(SoapFaultDefinition.SERVER);

    resolver.setDefaultFault(defaultFault);
    resolver.setOrder(1);

    return resolver;
  }
}
