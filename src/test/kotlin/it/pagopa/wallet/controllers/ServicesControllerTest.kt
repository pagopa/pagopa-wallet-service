package it.pagopa.wallet.controllers

import it.pagopa.wallet.ServiceTestUtils
import it.pagopa.wallet.ServiceTestUtils.Companion.DOMAIN_SERVICE
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.ServiceCreatedEvent
import it.pagopa.wallet.audit.ServiceStatusChangedEvent
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.exception.ServiceNotFoundException
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.ServicesService
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
@WebFluxTest(ServicesController::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class ServicesControllerTest {

    @MockBean private lateinit var servicesService: ServicesService

    @MockBean private lateinit var loggingEventRepository: LoggingEventRepository

    @Autowired private lateinit var webClient: WebTestClient

    private lateinit var servicesController: ServicesController

    @BeforeEach
    fun setupServicesController() {
        servicesController = ServicesController(servicesService, loggingEventRepository)
    }

    @Test
    fun `createService creates a new service`() = runTest {
        /* preconditions */

        given { servicesService.createService(any(), any(), any()) }
            .willReturn(
                mono {
                    LoggedAction(
                        DOMAIN_SERVICE,
                        ServiceCreatedEvent(DOMAIN_SERVICE.id.id, DOMAIN_SERVICE.name.name)
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        /* test */
        webClient
            .post()
            .uri("/services")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ServiceTestUtils.CREATE_SERVICE_REQUEST)
            .exchange()
            .expectStatus()
            .isCreated
    }

    @Test
    fun `setServiceStatus updates status on existing service`() = runTest {
        /* preconditions */

        given { servicesService.setServiceStatus(any(), any()) }
            .willReturn(
                mono {
                    LoggedAction(
                        DOMAIN_SERVICE,
                        ServiceStatusChangedEvent(
                            DOMAIN_SERVICE.id.id,
                            DOMAIN_SERVICE.name.name,
                            DOMAIN_SERVICE.status,
                            ServiceStatus.INCOMING
                        )
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        /* test */
        webClient
            .patch()
            .uri("/services/${DOMAIN_SERVICE.id.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ServiceTestUtils.UPDATE_SERVICE_STATUS_REQUEST)
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `setServiceStatus returns 404 on non existing service`() = runTest {
        /* preconditions */
        val invalidId = UUID.randomUUID()

        given { servicesService.setServiceStatus(any(), any()) }
            .willReturn(Mono.error(ServiceNotFoundException(invalidId)))

        /* test */
        webClient
            .patch()
            .uri("/services/${invalidId}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ServiceTestUtils.UPDATE_SERVICE_STATUS_REQUEST)
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
