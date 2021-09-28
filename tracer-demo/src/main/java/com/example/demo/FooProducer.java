package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FooProducer {
    private final Tracer tracer;
    private final AtomicInteger numberCalled = new AtomicInteger();

    @Autowired
    public FooProducer(Tracer tracer) {
        this.tracer = tracer;
    }

    public Mono<Void> send(int eventsToSend) {
        return Mono.create(sink -> {
            final Span newSpan = tracer.spanBuilder()
                    .name("event-hubs:send")
                    .kind(Span.Kind.PRODUCER)
                    .event("publishing")
                    .tag("number-of-events", String.valueOf(eventsToSend))
                    .start();

            try (Tracer.SpanInScope scope = tracer.withSpan(newSpan)) {
                if (numberCalled.incrementAndGet() % 3 == 0) {
                    throw new UnsupportedOperationException("IO Exception occurred. Test one.");
                }
                newSpan.event("published");
                sink.success();
            } catch (Exception e) {
                newSpan.error(e);
                sink.error(e);
            } finally {
                newSpan.end();
            }
        });
    }
}
