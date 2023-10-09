package it.pagopa.wallet.services

import it.pagopa.wallet.WalletTestUtils.SERVICE_ID
import it.pagopa.wallet.WalletTestUtils.SERVICE_NAME
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.ServiceCreatedEvent
import it.pagopa.wallet.domain.services.Service
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.repositories.ServiceRepository
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class ServicesServiceTest {
    private val loggingEventRepository: LoggingEventRepository = mock()

    private val serviceRepository: ServiceRepository = mock()

    private val servicesService = ServicesService(serviceRepository)

    @Test
    fun `createService creates a new service`() {
        val serviceId = SERVICE_ID
        val serviceName = SERVICE_NAME
        val serviceStatus = ServiceStatus.DISABLED
        val creationDate = Instant.now()

        val expected =
            LoggedAction(
                Service(serviceId, serviceName, serviceStatus, creationDate),
                ServiceCreatedEvent(serviceId.id, serviceName.name)
            )

        mockStatic(Instant::class.java).use { mockedInstant ->
            mockedInstant.`when`<Instant> { Instant.now() }.thenReturn(creationDate)

            given(loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>())).willAnswer {
                Flux.fromIterable(it.arguments[0] as Iterable<*>)
            }
            given(serviceRepository.save(any())).willAnswer { Mono.just(it.arguments[0]) }

            val actual =
                servicesService.createService(serviceId.id, serviceName, serviceStatus).block()
            assertEquals(expected, actual)
        }
    }
}
