package it.pagopa.wallet.repositories

import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.domain.wallets.LoggingEventDispatcher
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

interface LoggingEventRepository {
    fun saveAll(events: Iterable<LoggingEvent>): Flux<LoggingEvent>
}

interface LoggingEventRepositoryMongo : ReactiveCrudRepository<LoggingEvent, String>

@Repository
class LoggingEventRepositoryImpl(
    private val loggingEventDispatcher: LoggingEventDispatcher,
    private val loggingEventRepository: LoggingEventRepositoryMongo
) : LoggingEventRepository {
    override fun saveAll(events: Iterable<LoggingEvent>): Flux<LoggingEvent> {
        return loggingEventRepository.saveAll(events).flatMap { event ->
            loggingEventDispatcher.dispatchEvent(event).defaultIfEmpty(event)
        }
    }
}
