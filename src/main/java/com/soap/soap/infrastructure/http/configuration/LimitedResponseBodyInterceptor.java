package com.soap.soap.infrastructure.http.configuration;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public final class LimitedResponseBodyInterceptor implements ClientHttpRequestInterceptor {
  private final int maximumBytes;

  public LimitedResponseBodyInterceptor(int maximumBytes) {
    this.maximumBytes = maximumBytes;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var response = execution.execute(request, body);
    if (response.getHeaders().getContentLength() > maximumBytes) {
      response.close();
      throw new IOException("External response exceeds configured body limit");
    }
    return new LimitedResponse(response, maximumBytes);
  }

  private static final class LimitedResponse implements ClientHttpResponse {
    private final ClientHttpResponse delegate;
    private final int maximumBytes;

    private LimitedResponse(ClientHttpResponse delegate, int maximumBytes) {
      this.delegate = delegate;
      this.maximumBytes = maximumBytes;
    }

    @Override
    public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
      return delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return delegate.getStatusText();
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public InputStream getBody() throws IOException {
      return new BoundedInputStream(delegate.getBody(), maximumBytes);
    }

    @Override
    public org.springframework.http.HttpHeaders getHeaders() {
      return delegate.getHeaders();
    }
  }

  private static final class BoundedInputStream extends FilterInputStream {
    private final long maximumBytes;
    private long consumed;

    private BoundedInputStream(InputStream input, long maximumBytes) {
      super(input);
      this.maximumBytes = maximumBytes;
    }

    @Override
    public int read() throws IOException {
      var value = super.read();
      if (value >= 0) {
        account(1);
      }
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      var read = super.read(bytes, offset, length);
      if (read > 0) {
        account(read);
      }
      return read;
    }

    private void account(long bytes) throws IOException {
      consumed += bytes;
      if (consumed > maximumBytes) {
        throw new IOException("External response exceeds configured body limit");
      }
    }
  }
}
