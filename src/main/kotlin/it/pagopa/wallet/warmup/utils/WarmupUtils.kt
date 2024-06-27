package it.pagopa.wallet.warmup.utils

import it.pagopa.generated.wallet.model.*
import java.time.OffsetDateTime
import java.util.*

object WarmupUtils {
    val mockedUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    val walletCreateRequest =
        WalletCreateRequestDto().apply {
            this.addApplicationsItem("PAGOPA")
            this.paymentMethodId = mockedUUID
            this.useDiagnosticTracing = true
        }

    const val patchWalletStatusErrorRequest = """{"status":"ERROR","details":{"reason":"reason"}}"""

    val walletApplicationUpdateRequestRequest =
        WalletApplicationUpdateRequestDto().apply {
            this.addApplicationsItem(
                WalletApplicationDto().apply {
                    this.name = "PAGOPA"
                    this.status = WalletApplicationStatusDto.ENABLED
                }
            )
        }

    const val sessionInputCardDataRequest = """{"paymentMethodType":"cards"}"""

    val walletNotificationRequest =
        WalletNotificationRequestDto().apply {
            this.timestampOperation = OffsetDateTime.now()
            this.operationId = "operationId"
            this.operationResult = WalletNotificationRequestDto.OperationResultEnum.DECLINED
            this.errorCode = "ERROR CODE"
            this.details =
                WalletNotificationRequestCardDetailsDto().apply {
                    this.paymentInstrumentGatewayId = "paymentInstrumentGatewayId"
                }
        }

    val updateWalletUsageRequestDto =
        UpdateWalletUsageRequestDto().apply {
            this.usageTime = OffsetDateTime.now()
            this.clientId = ClientIdDto.IO
        }

    val createTransactionWalletRequestDto =
        WalletTransactionCreateRequestDto().apply {
            this.amount = 0
            this.paymentMethodId = mockedUUID
            this.useDiagnosticTracing = true
        }

    const val TRANSACTION_WALLET_URI = "http://localhost:8080/transactions/{transactionId}/wallets"
    const val WALLETS_URL = "http://localhost:8080/wallets"

    const val WALLETS_ID_RESOURCE_URL = "${WALLETS_URL}/{walletId}"

    const val WALLETS_SESSIONS_URL = "${WALLETS_ID_RESOURCE_URL}/sessions"

    const val WALLETS_SESSIONS_BY_ORDER_ID_URL = "${WALLETS_SESSIONS_URL}/{orderId}"

    const val USER_ID_HEADER_KEY = "x-user-id"

    const val CLIENT_ID_HEADER_KEY = "x-client-id"
}
