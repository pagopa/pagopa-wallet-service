package it.pagopa.wallet.controllers

import it.pagopa.wallet.ServiceTestUtils
import it.pagopa.wallet.ServiceTestUtils.Companion.DOMAIN_SERVICE
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.ServiceCreatedEvent
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.ServicesService
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
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux

@OptIn(ExperimentalCoroutinesApi::class)
@WebFluxTest(ServicesController::class)
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
}
