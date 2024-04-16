package it.pagopa.wallet.util

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import reactor.core.publisher.Mono

object Tracing {

    object Migration {
        /** HMAC of contract ID produced by CSTAR during migration phase */
        const val CONTRACT_HMAC = "contract"
    }

    fun <T> customizeSpan(mono: Mono<T>, f: Span.() -> Unit): Mono<T> {
        return Mono.using(
            { Span.fromContext(Context.current()) },
            { span -> f(span).let { mono } },
            {}
        )
    }
}
