package it.pagopa.wallet.common.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.common.QueueEvent
import it.pagopa.wallet.common.tracing.QueueTracingInfo
import it.pagopa.wallet.config.SerializationConfiguration
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.domain.wallets.WalletId
import java.time.OffsetDateTime
import java.util.*
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class WalletQueueEventTest {

    private val serializer =
        SerializationConfiguration().objectMapperBuilder().build<ObjectMapper>()

    @ParameterizedTest
    @MethodSource("walletEvents")
    fun shouldSerializeWalletEvent(walletQueueEvent: WalletQueueEvent) {
        val json = serializer.writeValueAsString(walletQueueEvent)
        println("serialized event: $json")
        val deserializedEvent = serializer.readValue<WalletQueueEvent>(json)
        assertEquals(walletQueueEvent, deserializedEvent)
    }

    @ParameterizedTest
    @MethodSource("walletEvents")
    fun shouldSerializeQueueWalletEvent(walletQueueEvent: WalletQueueEvent) {
        val queueEvent = QueueEvent(walletQueueEvent, QueueTracingInfo.empty())
        val json = serializer.writeValueAsString(queueEvent)
        println("serialized event: $json")
        val deserializedEvent = serializer.readValue<QueueEvent<WalletQueueEvent>>(json)
        assertEquals(queueEvent, deserializedEvent)
    }

    companion object {
        @JvmStatic
        private fun walletEvents() =
            Stream.of(
                Arguments.of(WalletCreatedEvent.of(walletId = WalletId(UUID.randomUUID()))),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent = WalletAddedEvent(walletId = UUID.randomUUID().toString())
                    )
                ),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent =
                            WalletMigratedAddedEvent(walletId = UUID.randomUUID().toString())
                    )
                ),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent = WalletDeletedEvent(walletId = UUID.randomUUID().toString())
                    )
                ),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent =
                            SessionWalletCreatedEvent(
                                walletId = UUID.randomUUID().toString(),
                                auditWallet = AuditWalletCreated(orderId = "orderId")
                            )
                    )
                ),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent =
                            WalletApplicationsUpdatedEvent(
                                walletId = UUID.randomUUID().toString(),
                                updatedApplications =
                                    listOf(
                                        AuditWalletApplication(
                                            id = "appId",
                                            status = "status",
                                            creationDate = OffsetDateTime.now().toString(),
                                            updateDate = OffsetDateTime.now().toString(),
                                            metadata = mapOf("key1" to "value1")
                                        )
                                    )
                            )
                    )
                ),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent =
                            WalletDetailsAddedEvent(walletId = UUID.randomUUID().toString())
                    )
                ),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent =
                            WalletOnboardCompletedEvent(
                                walletId = UUID.randomUUID().toString(),
                                auditWallet =
                                    AuditWalletCompleted(
                                        paymentMethodId = "paymentMethodId",
                                        updateDate = OffsetDateTime.now().toString(),
                                        creationDate = OffsetDateTime.now().toString(),
                                        status = "status",
                                        validationOperationId = "validationOperationId ",
                                        validationOperationResult = "validationOperationResult",
                                        validationErrorCode = "validationErrorCode",
                                        validationOperationTimestamp =
                                            OffsetDateTime.now().toString(),
                                        details =
                                            AuditWalletDetails(
                                                pspId = "pspId",
                                                type = "type",
                                                cardBrand = "cardBrand"
                                            ),
                                        applications =
                                            listOf(
                                                AuditWalletApplication(
                                                    status = "status",
                                                    creationDate = OffsetDateTime.now().toString(),
                                                    updateDate = OffsetDateTime.now().toString(),
                                                    metadata = mapOf("key1" to "value1"),
                                                    id = "id"
                                                )
                                            )
                                    )
                            )
                    )
                ),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent =
                            ApplicationCreatedEvent(serviceId = UUID.randomUUID().toString())
                    )
                ),
                Arguments.of(
                    WalletLoggingErrorEvent.of(
                        loggingEvent =
                            ApplicationStatusChangedEvent(
                                serviceId = UUID.randomUUID().toString(),
                                oldStatus = ApplicationStatus.INCOMING,
                                newStatus = ApplicationStatus.ENABLED
                            )
                    )
                ),
            )
    }
}
