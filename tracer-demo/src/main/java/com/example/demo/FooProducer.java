package com.example.demo;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.example.tracing.SpringTracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class FooProducer implements AutoCloseable {
    private static final String EVENT_HUB_CONNECTION_STRING =  System.getenv("EVENT_HUB_CONNECTION_STRING");
    private static final String EVENT_HUB_NAME = System.getenv("EVENT_HUB_NAME");
    private final Tracer tracer;
    private final EventHubProducerAsyncClient client;

    @Autowired
    public FooProducer(Tracer tracer) {
        this.tracer = tracer;
        this.client = new EventHubClientBuilder()
                .connectionString(EVENT_HUB_CONNECTION_STRING, EVENT_HUB_NAME)
                .buildAsyncProducerClient();

        final ServiceLoader<com.azure.core.util.tracing.Tracer> load =
                ServiceLoader.load(com.azure.core.util.tracing.Tracer.class);

        load.stream().forEach(t -> {
            if (t.type().isAssignableFrom(SpringTracer.class)) {
                ((SpringTracer)t.get()).setTracer(tracer);
            }
        });
    }

    public Mono<Void> send(int eventsToSend) {
        final List<EventData> events = IntStream.range(0, eventsToSend)
                .mapToObj(index -> new EventData(String.valueOf(index)))
                .collect(Collectors.toList());

        return client.send(events);

        // return Mono.create(sink -> {
        //     final Span newSpan = tracer.spanBuilder()
        //             .name("event-hubs:send")
        //             .kind(Span.Kind.PRODUCER)
        //             .event("publishing")
        //             .tag("number-of-events", String.valueOf(eventsToSend))
        //             .start();
        //
        //     try (Tracer.SpanInScope scope = tracer.withSpan(newSpan)) {
        //         if (numberCalled.incrementAndGet() % 3 == 0) {
        //             throw new UnsupportedOperationException("IO Exception occurred. Test one.");
        //         }
        //         newSpan.event("published");
        //         sink.success();
        //     } catch (Exception e) {
        //         newSpan.error(e);
        //         sink.error(e);
        //     } finally {
        //         newSpan.end();
        //     }
        // });
    }

    @Override
    public void close() {
        this.client.close();
    }
}
