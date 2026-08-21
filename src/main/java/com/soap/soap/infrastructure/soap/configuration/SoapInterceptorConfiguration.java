package com.soap.soap.infrastructure.soap.configuration;

import com.soap.soap.infrastructure.soap.interceptor.VocabularyStatusPayloadInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;

@Configuration
public class SoapInterceptorConfiguration implements WsConfigurer {
  @Override
  public void addInterceptors(List<EndpointInterceptor> interceptors) {
    interceptors.add(new VocabularyStatusPayloadInterceptor());
  }
}
