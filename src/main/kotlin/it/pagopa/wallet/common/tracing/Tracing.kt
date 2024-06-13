package it.pagopa.wallet.common.tracing

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
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
