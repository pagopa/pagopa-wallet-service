package it.pagopa.wallet.repositories

import it.pagopa.wallet.documents.applications.Application
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface ApplicationRepository : ReactiveCrudRepository<Application, String> {}
