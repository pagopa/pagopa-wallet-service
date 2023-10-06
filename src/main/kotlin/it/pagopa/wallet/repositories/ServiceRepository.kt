package it.pagopa.wallet.repositories

import it.pagopa.wallet.documents.service.Service
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface ServiceRepository : ReactiveCrudRepository<Service, String>
