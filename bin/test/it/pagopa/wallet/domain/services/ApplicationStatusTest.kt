package it.pagopa.wallet.domain.services

import it.pagopa.wallet.domain.applications.ApplicationStatus
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ApplicationStatusTest {
    companion object {
        @JvmStatic
        private fun statusChangeResult() =
            Stream.of(
                Arguments.of(Triple(ApplicationStatus.INCOMING, ApplicationStatus.INCOMING, true)),
                Arguments.of(Triple(ApplicationStatus.INCOMING, ApplicationStatus.DISABLED, false)),
                Arguments.of(Triple(ApplicationStatus.INCOMING, ApplicationStatus.ENABLED, false)),
                Arguments.of(Triple(ApplicationStatus.DISABLED, ApplicationStatus.INCOMING, true)),
                Arguments.of(Triple(ApplicationStatus.DISABLED, ApplicationStatus.DISABLED, true)),
                Arguments.of(Triple(ApplicationStatus.DISABLED, ApplicationStatus.ENABLED, true)),
                Arguments.of(Triple(ApplicationStatus.ENABLED, ApplicationStatus.INCOMING, false)),
                Arguments.of(Triple(ApplicationStatus.ENABLED, ApplicationStatus.DISABLED, false)),
                Arguments.of(Triple(ApplicationStatus.ENABLED, ApplicationStatus.ENABLED, true)),
            )
    }

    @ParameterizedTest
    @MethodSource("statusChangeResult")
    fun checkCanChangeStatus(args: Triple<ApplicationStatus, ApplicationStatus, Boolean>) {
        val (requested, global, expected) = args

        assertEquals(expected, ApplicationStatus.canChangeToStatus(requested, global))
    }
}
