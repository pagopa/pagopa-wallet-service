package it.pagopa.wallet.domain.services

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ServiceStatusTest {
    companion object {
        @JvmStatic
        private fun statusChangeResult() =
            Stream.of(
                Arguments.of(Triple(ServiceStatus.INCOMING, ServiceStatus.INCOMING, true)),
                Arguments.of(Triple(ServiceStatus.INCOMING, ServiceStatus.DISABLED, false)),
                Arguments.of(Triple(ServiceStatus.INCOMING, ServiceStatus.ENABLED, false)),
                Arguments.of(Triple(ServiceStatus.DISABLED, ServiceStatus.INCOMING, true)),
                Arguments.of(Triple(ServiceStatus.DISABLED, ServiceStatus.DISABLED, true)),
                Arguments.of(Triple(ServiceStatus.DISABLED, ServiceStatus.ENABLED, true)),
                Arguments.of(Triple(ServiceStatus.ENABLED, ServiceStatus.INCOMING, false)),
                Arguments.of(Triple(ServiceStatus.ENABLED, ServiceStatus.DISABLED, false)),
                Arguments.of(Triple(ServiceStatus.ENABLED, ServiceStatus.ENABLED, true)),
            )
    }

    @ParameterizedTest
    @MethodSource("statusChangeResult")
    fun checkCanChangeStatus(args: Triple<ServiceStatus, ServiceStatus, Boolean>) {
        val (requested, global, expected) = args

        assertEquals(expected, ServiceStatus.canChangeToStatus(requested, global))
    }
}
