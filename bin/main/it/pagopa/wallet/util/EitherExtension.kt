package it.pagopa.wallet.util

import io.vavr.control.Either
import reactor.core.publisher.Mono

object EitherExtension {

    fun <L : Exception, R : Any> Either<L, R>.toMono(): Mono<R> =
        this.fold({ error -> Mono.error(error) }, { value -> Mono.just(value) })
}
