package it.pagopa.wallet

import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.WalletTestUtils.APPLICATION_DESCRIPTION
import it.pagopa.wallet.WalletTestUtils.APPLICATION_ID
import it.pagopa.wallet.domain.applications.Application
import it.pagopa.wallet.domain.applications.ApplicationStatus
import java.time.Instant

class ApplicationsTestUtils {
    companion object {
        val DOMAIN_APPLICATION =
            Application(
                APPLICATION_ID,
                APPLICATION_DESCRIPTION,
                ApplicationStatus.DISABLED,
                Instant.now(),
                Instant.now()
            )

        val CREATE_APPLICATION_REQUEST =
            ApplicationCreateRequestDto().apply { applicationId = APPLICATION_ID.id }

        val UPDATE_SERVICE_STATUS_REQUEST =
            ApplicationPatchRequestDto().apply { status = ApplicationStatusDto.INCOMING }
    }
}
