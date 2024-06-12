package it.pagopa.wallet.domain.wallets

import it.pagopa.wallet.audit.LoggingEvent
import reactor.core.publisher.Mono

interface DomainEventDispatcher {
    fun dispatchEvent(event: LoggingEvent): Mono<LoggingEvent>
}
