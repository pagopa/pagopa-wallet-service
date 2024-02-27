package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.ApplicationsApi
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.ApplicationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
class ApplicationsController(
    @Autowired private val applicationsService: ApplicationService,
    @Autowired private val loggingEventRepository: LoggingEventRepository
) : ApplicationsApi {
    override fun createApplication(
        serviceCreateRequestDto: Mono<ApplicationCreateRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApplicationCreateResponseDto>> {
        return serviceCreateRequestDto
            .flatMap {
                applicationsService.createApplication(
                    it.applicationId,
                    ApplicationStatusDto.valueOf((it.status ?: ApplicationStatusDto.DISABLED).value)
                )
            }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .map { ApplicationCreateResponseDto().apply { applicationId = it.id.id } }
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
    }

    override fun setApplicationStatus(
        applicationId: String,
        servicePatchRequestDto: Mono<ApplicationPatchRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> {
        return servicePatchRequestDto
            .flatMap {
                applicationsService.setApplicationStatus(
                    applicationId,
                    ApplicationStatusDto.valueOf(it.status.toString())
                )
            }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .map { ResponseEntity.noContent().build() }
    }
}
