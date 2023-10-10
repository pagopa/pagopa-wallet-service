package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.ServicesApi
import it.pagopa.generated.wallet.model.ServiceCreateRequestDto
import it.pagopa.generated.wallet.model.ServiceCreateResponseDto
import it.pagopa.generated.wallet.model.ServicePatchRequestDto
import it.pagopa.generated.wallet.model.ServiceStatusDto
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.ServicesService
import java.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
class ServicesController(
    @Autowired private val servicesService: ServicesService,
    @Autowired private val loggingEventRepository: LoggingEventRepository
) : ServicesApi {
    override fun createService(
        serviceCreateRequestDto: Mono<ServiceCreateRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ServiceCreateResponseDto>> {
        return serviceCreateRequestDto
            .flatMap {
                servicesService.createService(
                    UUID.randomUUID(),
                    ServiceName(it.name),
                    ServiceStatus.valueOf((it.status ?: ServiceStatusDto.DISABLED).value),
                )
            }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .map { ServiceCreateResponseDto().apply { serviceId = it.id.id } }
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
    }

    override fun setServiceStatus(
        serviceId: UUID,
        servicePatchRequestDto: Mono<ServicePatchRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> {
        return servicePatchRequestDto
            .flatMap {
                servicesService.setServiceStatus(
                    serviceId,
                    ServiceStatus.valueOf(it.status.toString())
                )
            }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .map { ResponseEntity.noContent().build() }
    }
}
