package it.pagopa.wallet.common.tracing

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import reactor.core.publisher.Mono

typealias TracedMono<T> = (QueueTracingInfo) -> Mono<T>

class TracingUtils(private val openTelemetry: OpenTelemetry, private val tracer: Tracer) {
    companion object {
        const val TRACEPARENT: String = "traceparent"
        const val TRACESTATE: String = "tracestate"
        const val BAGGAGE: String = "baggage"
    }

    fun <T> traceMonoQueue(spanName: String, traced: TracedMono<T>) =
        Mono.using(
            {
                val span: Span =
                    tracer
                        .spanBuilder(spanName)
                        .setSpanKind(SpanKind.PRODUCER)
                        .setParent(Context.current().with(Span.current()))
                        .startSpan()
                val rawTracingInfo: MutableMap<String, String> = mutableMapOf()
                openTelemetry.propagators.textMapPropagator.inject(
                    Context.current(),
                    rawTracingInfo
                ) { map, k, v ->
                    map?.put(k, v)
                }

                val tracingInfo =
                    QueueTracingInfo(
                        rawTracingInfo[TRACEPARENT],
                        rawTracingInfo[TRACESTATE],
                        rawTracingInfo[BAGGAGE]
                    )
                Pair(span, tracingInfo)
            },
            { (_, tracingInfo) -> traced.invoke(tracingInfo) },
            { (span, _) -> span.end() }
        )
}
