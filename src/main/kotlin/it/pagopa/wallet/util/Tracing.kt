package it.pagopa.wallet.util

import com.azure.core.http.HttpHeaderName
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import reactor.core.publisher.Mono

object Tracing {

    object Migration {
        /** HMAC of contract ID produced by CSTAR during migration phase */
        val CONTRACT_HMAC = AttributeKey.stringKey("contract")
        val WALLET_ID = AttributeKey.stringKey("walletId")
    }

    fun <T> customizeSpan(mono: Mono<T>, f: Span.() -> Unit): Mono<T> {
        return Mono.using(
            { Span.fromContext(Context.current()) },
            { span -> f(span).let { mono } },
            {}
        )
    }
}

data class QueueTracingInfo(
    val traceparent: String?,
    val tracestate: String?,
    val baggage: String?
)

class TracingUtils(private val openTelemetry: OpenTelemetry, private val tracer: Tracer) {
    companion object {
        const val TRACEPARENT: String = "traceparent"
        const val TRACESTATE: String = "tracestate"
        const val BAGGAGE: String = "baggage"
    }

    fun <T> traceMono(spanName: String, traced: (QueueTracingInfo) -> Mono<T>) =
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

                val tracingInfo: QueueTracingInfo =
                    QueueTracingInfo(
                        rawTracingInfo[HttpHeaderName.TRACEPARENT.toString()],
                        rawTracingInfo[TRACESTATE],
                        rawTracingInfo[BAGGAGE]
                    )
                Pair(span, tracingInfo)
            },
            { (_, tracingInfo) -> traced.invoke(tracingInfo) },
            { (span, _) -> span.end() }
        )
}
