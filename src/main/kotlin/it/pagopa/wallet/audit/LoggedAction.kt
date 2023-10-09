package it.pagopa.wallet.audit

import it.pagopa.wallet.repositories.LoggingEventRepository
import reactor.core.publisher.Mono

data class LoggedAction<T : Any>(val data: T, val events: List<LoggingEvent>) {
    companion object {
        fun <T : Any> swap(loggedAction: LoggedAction<Mono<T>>): Mono<LoggedAction<T>> {
            return loggedAction.data.map { LoggedAction(it, loggedAction.events) }
        }

        fun <T : Any> join(loggedAction: LoggedAction<LoggedAction<T>>): LoggedAction<T> {
            return loggedAction.flatMap { it }
        }
    }

    constructor(data: T, event: LoggingEvent) : this(data, listOf(event))

    fun <R : Any> flatMap(action: (T) -> LoggedAction<R>): LoggedAction<R> {
        val result = action(this.data)

        return LoggedAction(result.data, this.events + result.events)
    }

    fun <R : Any> map(mapper: (T) -> R): LoggedAction<R> {
        return LoggedAction(mapper(this.data), this.events)
    }

    fun saveEvents(repository: LoggingEventRepository): Mono<T> {
        return repository.saveAll(this.events).then(Mono.just(this.data))
    }
}

fun <T : Any> LoggedAction<Mono<T>>.swap(): Mono<LoggedAction<T>> {
    return LoggedAction.swap(this)
}

fun <T : Any> LoggedAction<LoggedAction<T>>.join(): LoggedAction<T> {
    return LoggedAction.join(this)
}

fun <T : Any, R : Any> Mono<LoggedAction<T>>.flatMapLogged(
    f: (T) -> Mono<LoggedAction<R>>
): Mono<LoggedAction<R>> {
    return this.mapLogged(f).map { it.join() }
}

fun <T : Any, R : Any> Mono<LoggedAction<T>>.mapLogged(f: (T) -> Mono<R>): Mono<LoggedAction<R>> {
    return this.flatMap { action -> action.map(f).swap() }
}
