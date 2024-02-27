package it.pagopa.wallet.controllers

import it.pagopa.wallet.ApplicationsTestUtils
import it.pagopa.wallet.ApplicationsTestUtils.Companion.DOMAIN_APPLICATION
import it.pagopa.wallet.audit.ApplicationCreatedEvent
import it.pagopa.wallet.audit.ApplicationStatusChangedEvent
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.exception.ApplicationNotFoundException
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.ApplicationService
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@OptIn(ExperimentalCoroutinesApi::class)
@WebFluxTest(ApplicationsController::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class ApplicationsControllerTest {

    @MockBean private lateinit var applicationsService: ApplicationService

    @MockBean private lateinit var loggingEventRepository: LoggingEventRepository

    @Autowired private lateinit var webClient: WebTestClient

    private lateinit var applicationsController: ApplicationsController

    @BeforeEach
    fun setupServicesController() {
        applicationsController = ApplicationsController(applicationsService, loggingEventRepository)
    }

    @Test
    fun `createApplication creates a new application`() = runTest {
        /* preconditions */

        given { applicationsService.createApplication(any(), any()) }
            .willReturn(
                mono {
                    LoggedAction(
                        DOMAIN_APPLICATION,
                        ApplicationCreatedEvent(DOMAIN_APPLICATION.id.id)
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        /* test */
        webClient
            .post()
            .uri("/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ApplicationsTestUtils.CREATE_APPLICATION_REQUEST)
            .exchange()
            .expectStatus()
            .isCreated
    }

    @Test
    fun `setServiceApplication updates status on existing application`() = runTest {
        /* preconditions */

        given { applicationsService.setApplicationStatus(any(), any()) }
            .willReturn(
                mono {
                    LoggedAction(
                        DOMAIN_APPLICATION,
                        ApplicationStatusChangedEvent(
                            DOMAIN_APPLICATION.id.id,
                            DOMAIN_APPLICATION.status,
                            ApplicationStatus.INCOMING
                        )
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        /* test */
        webClient
            .patch()
            .uri("/applications/${DOMAIN_APPLICATION.id.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ApplicationsTestUtils.UPDATE_SERVICE_STATUS_REQUEST)
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `setApplicationStatus returns 404 on non existing application`() = runTest {
        /* preconditions */
        val invalidId = UUID.randomUUID().toString()

        given { applicationsService.setApplicationStatus(any(), any()) }
            .willReturn(Mono.error(ApplicationNotFoundException(invalidId)))

        /* test */
        webClient
            .patch()
            .uri("/applications/${invalidId}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ApplicationsTestUtils.UPDATE_SERVICE_STATUS_REQUEST)
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
