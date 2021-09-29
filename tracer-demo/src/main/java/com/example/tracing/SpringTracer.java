package com.example.tracing;

import com.azure.core.util.Context;
import com.azure.core.util.tracing.ProcessKind;
import com.azure.core.util.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer.SpanInScope;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;

public class SpringTracer implements Tracer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringTracer.class);
    private static final String SLEUTH_TRACE_ID = "trace-id";
    private org.springframework.cloud.sleuth.Tracer sleuthTracer;
    private final ConcurrentHashMap<String, SpanHolder> holderQueue = new ConcurrentHashMap<>();

    public SpringTracer() {
        LOGGER.info("Created new instance! Name: " + this);
    }

    public void setTracer(org.springframework.cloud.sleuth.Tracer tracer) {
        this.sleuthTracer = tracer;
    }

    @Override
    public Context start(String s, Context context) {
        return start(s, context, null);
    }

    @Override
    public Context start(String s, Context context, ProcessKind processKind) {
        final Span.Builder builder = sleuthTracer.spanBuilder().name(s);
        if (processKind != null) {
            switch (processKind) {
                case SEND:
                    builder.kind(Span.Kind.PRODUCER);
                    break;
                case MESSAGE:
                    builder.kind(Span.Kind.CLIENT);
                    break;
                case PROCESS:
                    builder.kind(Span.Kind.CONSUMER);
                default:
                    throw new UnsupportedOperationException("Not supported operation kind." + processKind);
            }
        }

        context.getValues().forEach((key, value) -> builder.tag(key.toString(), value.toString()));

        final Span span = builder.start();
        final SpanInScope spanInScope = sleuthTracer.withSpan(span);
        final String id = span.context().spanId();

        holderQueue.put(id, new SpanHolder(span, spanInScope));
        return context.addData(SLEUTH_TRACE_ID, id);
    }

    @Override
    public void end(int i, Throwable throwable, Context context) {
        final Object o = context.getValues().get(SLEUTH_TRACE_ID);
        if (!(o instanceof String)) {
            return;
        }

        final String traceId = (String) o;
        final SpanHolder removed = holderQueue.remove(traceId);

        if (removed == null) {
            return;
        }

        removed.span.tag("value", String.valueOf(i));

        if (throwable != null) {
            removed.span.error(throwable);
        }

        removed.close();
    }

    @Override
    public void end(String s, Throwable throwable, Context context) {
        final Object o = context.getValues().get(SLEUTH_TRACE_ID);
        if (!(o instanceof String)) {
            return;
        }

        final String traceId = (String) o;
        final SpanHolder removed = holderQueue.remove(traceId);


        if (removed == null) {
            return;
        }

        removed.span.event(s);

        if (throwable != null) {
            removed.span.error(throwable);
        }

        removed.close();
    }

    @Override
    public void setAttribute(String s, String s1, Context context) {
        final Object o = context.getValues().get(SLEUTH_TRACE_ID);
        if (!(o instanceof String)) {
            return;
        }

        final String traceId = (String) o;
        final SpanHolder existingSpan = holderQueue.get(traceId);

        if (existingSpan == null) {
            return;
        }

        existingSpan.span.tag(s, s1);
    }

    @Override
    public Context setSpanName(String s, Context context) {
        return context;
    }

    @Override
    public void addLink(Context context) {

    }

    @Override
    public Context extractContext(String s, Context context) {
        return context;
    }

    private static class SpanHolder implements Closeable {
        private final Span span;
        private final SpanInScope spanInScope;

        SpanHolder(Span span, SpanInScope spanInScope) {
            this.span = span;
            this.spanInScope = spanInScope;
        }

        @Override
        public void close() {
            spanInScope.close();
        }
    }
}
