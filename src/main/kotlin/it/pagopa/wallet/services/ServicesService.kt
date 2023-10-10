package it.pagopa.wallet.services

import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.ServiceCreatedEvent
import it.pagopa.wallet.audit.ServiceStatusChangedEvent
import it.pagopa.wallet.domain.services.ServiceId
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.exception.ServiceNotFoundException
import it.pagopa.wallet.repositories.ServiceRepository
import java.time.Instant
import java.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class ServicesService(@Autowired private val serviceRepository: ServiceRepository) {
    fun createService(
        serviceId: UUID,
        serviceName: ServiceName,
        status: ServiceStatus
    ): Mono<LoggedAction<it.pagopa.wallet.domain.services.Service>> {
        val service =
            it.pagopa.wallet.domain.services.Service(
                id = ServiceId(serviceId),
                name = serviceName,
                status = status,
                lastUpdated = Instant.now()
            )

        return serviceRepository
            .save(it.pagopa.wallet.documents.service.Service.fromDomain(service))
            .map { LoggedAction(service, ServiceCreatedEvent(service.id.id, service.name.name)) }
    }

    fun setServiceStatus(
        serviceId: UUID,
        status: ServiceStatus
    ): Mono<LoggedAction<it.pagopa.wallet.domain.services.Service>> {
        return serviceRepository
            .findById(serviceId.toString())
            .switchIfEmpty(Mono.error(ServiceNotFoundException(serviceId)))
            .map {
                it.copy(status = status.toString(), lastUpdated = Instant.now().toString()) to
                    ServiceStatus.valueOf(it.status)
            }
            .flatMap { (service, oldStatus) ->
                serviceRepository.save(service).map { service to oldStatus }
            }
            .map { (service, oldStatus) ->
                it.pagopa.wallet.domain.services.Service(
                    ServiceId(serviceId),
                    ServiceName(service.name),
                    ServiceStatus.valueOf(service.status),
                    Instant.parse(service.lastUpdated)
                ) to oldStatus
            }
            .map { (service, oldStatus) ->
                LoggedAction(
                    service,
                    ServiceStatusChangedEvent(
                        serviceId,
                        service.name.name,
                        oldStatus,
                        service.status
                    )
                )
            }
    }
}
