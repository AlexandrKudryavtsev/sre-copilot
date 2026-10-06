package com.srecopilot.monitoring;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class PrometheusClient {

    private final HttpClient client;
    private final MonitoringProperties properties;
    private final PrometheusTextValidator validator;

    public PrometheusClient(HttpClient prometheusHttpClient, MonitoringProperties properties,
            PrometheusTextValidator validator) {
        this.client = prometheusHttpClient;
        this.properties = properties;
        this.validator = validator;
    }

    public ConnectionProbeResult testConnection(String endpointUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpointUrl))
                .timeout(properties.requestTimeout())
                .header("Accept", "text/plain;version=0.0.4;charset=utf-8;escaping=underscores")
                .GET()
                .build();
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request,
                info -> new LimitedBodySubscriber(properties.maxResponseBytes()));
        try {
            // Covers the entire response, including a body that stalls after headers arrive.
            HttpResponse<byte[]> response = future.get(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                return ConnectionProbeResult.unavailable("Endpoint returned HTTP " + response.statusCode() + ".");
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (!"text/plain".equals(contentType)) {
                return ConnectionProbeResult.unavailable("Endpoint must return Prometheus text metrics (text/plain).");
            }
            String body = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(response.body())).toString();
            validator.validate(body);
            return ConnectionProbeResult.available();
        } catch (TimeoutException exception) {
            future.cancel(true);
            return ConnectionProbeResult.unavailable("Endpoint request timed out.");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return ConnectionProbeResult.unavailable("Endpoint request was interrupted.");
        } catch (ExecutionException exception) {
            for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof ResponseTooLargeException) {
                    return ConnectionProbeResult.unavailable("Endpoint response exceeds the configured size limit.");
                }
                if (cause instanceof HttpTimeoutException) {
                    return ConnectionProbeResult.unavailable("Endpoint request timed out.");
                }
            }
            return ConnectionProbeResult.unavailable("Unable to connect to the monitoring endpoint.");
        } catch (CharacterCodingException exception) {
            return ConnectionProbeResult.unavailable("Endpoint metrics must be UTF-8 text.");
        } catch (IllegalArgumentException exception) {
            return ConnectionProbeResult.unavailable(exception.getMessage());
        }
    }

    private static final class ResponseTooLargeException extends IOException {
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;
        private Flow.Subscription subscription;
        private long received;

        private LimitedBodySubscriber(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                received += buffer.remaining();
            }
            if (received > limit) {
                subscription.cancel();
                delegate.onError(new ResponseTooLargeException());
            } else {
                delegate.onNext(buffers);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }
}
