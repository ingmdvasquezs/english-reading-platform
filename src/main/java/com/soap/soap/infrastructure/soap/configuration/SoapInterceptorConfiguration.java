package com.soap.soap.infrastructure.soap.configuration;

import com.soap.soap.infrastructure.observability.SoapObservationInterceptor;
import com.soap.soap.infrastructure.security.SoapSecurityInterceptor;
import com.soap.soap.infrastructure.soap.interceptor.VocabularyStatusPayloadInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.xml.xsd.XsdSchema;

@Configuration
public class SoapInterceptorConfiguration implements WsConfigurer {
  private final SoapSecurityInterceptor securityInterceptor;
  private final SoapObservationInterceptor observationInterceptor;
  private final PayloadValidatingInterceptor validatingInterceptor;

  public SoapInterceptorConfiguration(
      SoapSecurityInterceptor securityInterceptor,
      SoapObservationInterceptor observationInterceptor,
      PayloadValidatingInterceptor validatingInterceptor) {
    this.securityInterceptor = securityInterceptor;
    this.observationInterceptor = observationInterceptor;
    this.validatingInterceptor = validatingInterceptor;
  }

  @Bean
  static SoapObservationInterceptor soapObservationInterceptor(MeterRegistry meters) {
    return new SoapObservationInterceptor(meters);
  }

  @Bean
  static PayloadValidatingInterceptor payloadValidatingInterceptor(XsdSchema readingsSchema)
      throws Exception {
    var interceptor = new PayloadValidatingInterceptor();
    interceptor.setXsdSchema(readingsSchema);
    interceptor.setValidateRequest(true);
    interceptor.setValidateResponse(false);
    interceptor.setAddValidationErrorDetail(false);
    interceptor.setFaultStringOrReason("Invalid SOAP request");
    interceptor.afterPropertiesSet();
    return interceptor;
  }

  @Override
  public void addInterceptors(List<EndpointInterceptor> interceptors) {
    interceptors.add(observationInterceptor);
    interceptors.add(securityInterceptor);
    interceptors.add(new VocabularyStatusPayloadInterceptor());
    interceptors.add(validatingInterceptor);
  }
}
