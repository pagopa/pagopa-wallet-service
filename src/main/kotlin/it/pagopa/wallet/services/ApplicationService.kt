package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.ApplicationStatusDto
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.domain.applications.ApplicationDescription
import it.pagopa.wallet.domain.applications.ApplicationId
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.exception.ApplicationNotFoundException
import it.pagopa.wallet.repositories.ApplicationRepository
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class ApplicationService(@Autowired private val applicationRepository: ApplicationRepository) {
    fun createApplication(
        applicationId: String,
        status: ApplicationStatusDto
    ): Mono<LoggedAction<it.pagopa.wallet.domain.applications.Application>> {
        val application =
            it.pagopa.wallet.domain.applications.Application(
                id = ApplicationId(applicationId),
                description = ApplicationDescription(""), // TODO handle according API refactoring
                status = ApplicationStatus.valueOf(status.value),
                creationDate = Instant.now(),
                updateDate = Instant.now()
            )

        return applicationRepository
            .save(it.pagopa.wallet.documents.applications.Application.fromDomain(application))
            .map { LoggedAction(application, ApplicationCreatedEvent(application.id.id)) }
    }

    fun setApplicationStatus(
        applicationId: String,
        status: ApplicationStatusDto
    ): Mono<LoggedAction<it.pagopa.wallet.domain.applications.Application>> {
        return applicationRepository
            .findById(applicationId)
            .switchIfEmpty(Mono.error(ApplicationNotFoundException(applicationId)))
            .map {
                it.copy(status = status.toString(), updateDate = Instant.now().toString()) to
                    ApplicationStatus.valueOf(it.status)
            }
            .flatMap { (application, oldStatus) ->
                applicationRepository.save(application).map { application to oldStatus }
            }
            .map { (application, oldStatus) ->
                it.pagopa.wallet.domain.applications.Application(
                    ApplicationId(applicationId),
                    ApplicationDescription(""),
                    ApplicationStatus.valueOf(application.status),
                    Instant.parse(application.creationDate),
                    Instant.parse(application.updateDate)
                ) to oldStatus
            }
            .map { (application, oldStatus) ->
                LoggedAction(
                    application,
                    ApplicationStatusChangedEvent(applicationId, oldStatus, application.status)
                )
            }
    }
}
