package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.ApplicationStatusDto
import it.pagopa.wallet.ApplicationsTestUtils.Companion.DOMAIN_APPLICATION
import it.pagopa.wallet.WalletTestUtils.APPLICATION_DESCRIPTION
import it.pagopa.wallet.WalletTestUtils.APPLICATION_ID
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.documents.applications.Application as ApplicationDocument
import it.pagopa.wallet.domain.applications.Application
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.exception.ApplicationNotFoundException
import it.pagopa.wallet.repositories.ApplicationRepository
import it.pagopa.wallet.repositories.LoggingEventRepository
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class ApplicationsServiceTest {
    private val loggingEventRepository: LoggingEventRepository = mock()

    private val applicationsRepository: ApplicationRepository = mock()

    private val applicationsService = ApplicationService(applicationsRepository)

    @Test
    fun `createApplication creates a new Application`() {
        val applicationId = APPLICATION_ID
        val applicationDescription = APPLICATION_DESCRIPTION
        val applicationStatus = ApplicationStatus.DISABLED
        val creationDate = Instant.now()

        val application =
            Application(
                applicationId,
                applicationDescription,
                applicationStatus,
                creationDate,
                creationDate
            )
        val expected = LoggedAction(application, ApplicationCreatedEvent(applicationId.id))

        mockStatic(Instant::class.java).use { mockedInstant ->
            mockedInstant.`when`<Instant> { Instant.now() }.thenReturn(creationDate)

            given(loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>())).willAnswer {
                Flux.fromIterable(it.arguments[0] as Iterable<*>)
            }
            given(applicationsRepository.save(any())).willAnswer { Mono.just(it.arguments[0]) }

            val actual =
                applicationsService
                    .createApplication(
                        applicationId.id,
                        ApplicationStatusDto.valueOf(applicationStatus.name)
                    )
                    .block()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `setApplicationStatus updates the application status with logging`() {
        val application = DOMAIN_APPLICATION
        val updatedDate = Instant.now()

        val newStatus = ApplicationStatus.INCOMING

        val expected =
            LoggedAction(
                application.copy(status = newStatus, updateDate = updatedDate),
                ApplicationStatusChangedEvent(application.id.id, application.status, newStatus)
            )

        mockStatic(Instant::class.java, CALLS_REAL_METHODS).use { mockedInstant ->
            mockedInstant.`when`<Instant> { Instant.now() }.thenReturn(updatedDate)

            given(applicationsRepository.findById(any<String>())).willAnswer {
                Mono.just(ApplicationDocument.fromDomain(application))
            }

            given(loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>())).willAnswer {
                Flux.fromIterable(it.arguments[0] as Iterable<*>)
            }

            given(applicationsRepository.save(any())).willAnswer { Mono.just(it.arguments[0]) }

            val actual =
                applicationsService
                    .setApplicationStatus(
                        application.id.id,
                        ApplicationStatusDto.valueOf(newStatus.name)
                    )
                    .block()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `setApplicationsStatus returns ApplicationNotFoundException on service not found`() {
        val service = DOMAIN_APPLICATION
        val updatedDate = Instant.now()

        val newStatus = ApplicationStatus.INCOMING

        mockStatic(Instant::class.java, CALLS_REAL_METHODS).use { mockedInstant ->
            mockedInstant.`when`<Instant> { Instant.now() }.thenReturn(updatedDate)

            given(applicationsRepository.findById(any<String>())).willReturn(Mono.empty())

            StepVerifier.create(
                    applicationsService.setApplicationStatus(
                        service.id.id,
                        ApplicationStatusDto.valueOf(newStatus.name)
                    )
                )
                .expectError(ApplicationNotFoundException::class.java)
        }
    }
}
