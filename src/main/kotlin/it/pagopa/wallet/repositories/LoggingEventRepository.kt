package it.pagopa.wallet.repositories

import it.pagopa.wallet.audit.LoggingEvent
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface LoggingEventRepository : ReactiveCrudRepository<LoggingEvent, String>
