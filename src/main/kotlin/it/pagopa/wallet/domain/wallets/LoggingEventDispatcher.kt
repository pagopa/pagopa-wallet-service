package it.pagopa.wallet.domain.wallets

import it.pagopa.wallet.audit.LoggingEvent
import reactor.core.publisher.Mono

interface LoggingEventDispatcher {
    fun dispatchEvent(event: LoggingEvent): Mono<LoggingEvent>
}
