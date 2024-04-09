package it.pagopa.wallet.reactormdc

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.context.Context

fun <T> Mono<T>.enrichContext(f: (T, Context) -> Context) = flatMap { value ->
    Mono.just(value).contextWrite { f(value, it) }
}

fun <T> Flux<T>.enrichContext(f: (T, Context) -> Context) = flatMap { value ->
    Flux.just(value).contextWrite { f(value, it) }
}
