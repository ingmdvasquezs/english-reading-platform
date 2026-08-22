package com.soap.soap.infrastructure.soap.configuration;

import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.infrastructure.security.InMemoryRateLimiter;
import com.soap.soap.infrastructure.security.RateLimitPolicy;
import com.soap.soap.infrastructure.security.SoapSecurityInterceptor;
import com.soap.soap.infrastructure.soap.interceptor.VocabularyStatusPayloadInterceptor;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.xml.xsd.XsdSchema;

@Configuration
public class SoapInterceptorConfiguration implements WsConfigurer {
  private final SoapSecurityInterceptor securityInterceptor;
  private final PayloadValidatingInterceptor validatingInterceptor;

  public SoapInterceptorConfiguration(
      SoapSecurityInterceptor securityInterceptor,
      PayloadValidatingInterceptor validatingInterceptor) {
    this.securityInterceptor = securityInterceptor;
    this.validatingInterceptor = validatingInterceptor;
  }

  @Bean
  static InMemoryRateLimiter inMemoryRateLimiter(
      Clock clock, @Value("${app.rate-limit.maximum-buckets:10000}") int maximumBuckets) {
    return new InMemoryRateLimiter(clock, maximumBuckets);
  }

  @Bean
  static SoapSecurityInterceptor soapSecurityInterceptor(
      CurrentUserPort currentUser,
      InMemoryRateLimiter limiter,
      @Value("${app.rate-limit.login.requests:10}") int loginRequests,
      @Value("${app.rate-limit.login.window:1m}") Duration loginWindow,
      @Value("${app.rate-limit.register.requests:5}") int registerRequests,
      @Value("${app.rate-limit.register.window:1h}") Duration registerWindow,
      @Value("${app.rate-limit.lookup.requests:30}") int lookupRequests,
      @Value("${app.rate-limit.lookup.window:1m}") Duration lookupWindow,
      @Value("${app.rate-limit.analyze.requests:10}") int analyzeRequests,
      @Value("${app.rate-limit.analyze.window:1m}") Duration analyzeWindow,
      @Value("${app.rate-limit.reader.requests:20}") int readerRequests,
      @Value("${app.rate-limit.reader.window:1m}") Duration readerWindow) {
    var limits = new EnumMap<RateLimitPolicy, SoapSecurityInterceptor.Limit>(RateLimitPolicy.class);
    limits.put(
        RateLimitPolicy.LOGIN, new SoapSecurityInterceptor.Limit(loginRequests, loginWindow));
    limits.put(
        RateLimitPolicy.REGISTER,
        new SoapSecurityInterceptor.Limit(registerRequests, registerWindow));
    limits.put(
        RateLimitPolicy.LOOKUP, new SoapSecurityInterceptor.Limit(lookupRequests, lookupWindow));
    limits.put(
        RateLimitPolicy.ANALYZE, new SoapSecurityInterceptor.Limit(analyzeRequests, analyzeWindow));
    limits.put(
        RateLimitPolicy.READER, new SoapSecurityInterceptor.Limit(readerRequests, readerWindow));
    return new SoapSecurityInterceptor(currentUser, limiter, limits);
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
    interceptors.add(securityInterceptor);
    interceptors.add(new VocabularyStatusPayloadInterceptor());
    interceptors.add(validatingInterceptor);
  }
}
