package it.pagopa.wallet.repositories

import it.pagopa.wallet.documents.service.Service
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface ServiceRepository : ReactiveCrudRepository<Service, String> {
    fun findByName(name: String): Mono<Service>
}
