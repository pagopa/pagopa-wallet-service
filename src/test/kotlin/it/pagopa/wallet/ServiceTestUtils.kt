package it.pagopa.wallet

import it.pagopa.generated.wallet.model.ServiceCreateRequestDto
import it.pagopa.wallet.WalletTestUtils.SERVICE_ID
import it.pagopa.wallet.WalletTestUtils.SERVICE_NAME
import it.pagopa.wallet.domain.services.Service
import it.pagopa.wallet.domain.services.ServiceStatus
import java.time.Instant

class ServiceTestUtils {
    companion object {
        val DOMAIN_SERVICE =
            Service(SERVICE_ID, SERVICE_NAME, ServiceStatus.DISABLED, Instant.now())

        val CREATE_SERVICE_REQUEST = ServiceCreateRequestDto().apply { name = SERVICE_NAME.name }
    }
}
