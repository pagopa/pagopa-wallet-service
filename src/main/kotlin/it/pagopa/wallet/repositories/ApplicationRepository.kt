package it.pagopa.wallet.repositories

import it.pagopa.wallet.documents.applications.Application
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface ApplicationRepository : ReactiveCrudRepository<Application, String> {
    fun findByName(name: String): Mono<Application>
}
