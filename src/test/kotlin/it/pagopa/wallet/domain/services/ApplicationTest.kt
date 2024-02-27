package it.pagopa.wallet.domain.services

import it.pagopa.wallet.WalletTestUtils.APPLICATION_DESCRIPTION
import it.pagopa.wallet.WalletTestUtils.APPLICATION_ID
import it.pagopa.wallet.domain.applications.Application
import it.pagopa.wallet.domain.applications.ApplicationStatus
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ApplicationTest {
    @Test
    fun `can construct application`() {
        assertDoesNotThrow {
            Application(
                APPLICATION_ID,
                APPLICATION_DESCRIPTION,
                ApplicationStatus.DISABLED,
                Instant.now(),
                Instant.now()
            )
        }
    }
}
