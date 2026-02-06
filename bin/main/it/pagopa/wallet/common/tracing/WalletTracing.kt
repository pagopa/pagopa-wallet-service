package it.pagopa.wallet.common.tracing

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType

class WalletTracing(private val tracingUtils: TracingUtils) {

    companion object {
        const val WALLET_UPDATE_RESULT_SPAN_NAME = "WalletUpdateResult"
        const val FIELD_NOT_AVAILABLE = "N/A"
        val UPDATE_WALLET_STATUS_OUTCOME_ATTRIBUTE_KEY: AttributeKey<String> =
            AttributeKey.stringKey("updateWalletStatus.outcome")
        val UPDATE_WALLET_STATUS_CURRENT_STATE_ATTRIBUTE_KEY: AttributeKey<String> =
            AttributeKey.stringKey("updateWalletStatus.status")
        val UPDATE_WALLET_STATUS_DETAILS_TYPE_ATTRIBUTE_KEY: AttributeKey<String> =
            AttributeKey.stringKey("updateWalletStatus.detailsType")
        val UPDATE_WALLET_STATUS_GATEWAY_OUTCOME_ATTRIBUTE_KEY: AttributeKey<String> =
            AttributeKey.stringKey("updateWalletStatus.gateway.outcome")
        val UPDATE_WALLET_STATUS_GATEWAY_ERROR_CODE_ATTRIBUTE_KEY: AttributeKey<String> =
            AttributeKey.stringKey("updateWalletStatus.gateway.errorCode")
    }

    data class WalletUpdateResult(
        val outcome: WalletNotificationOutcome,
        val walletType: WalletDetailsType? = null,
        val walletStatusDto: WalletStatusDto? = null,
        val gatewayOutcome: GatewayNotificationOutcomeResult? = null
    )

    data class GatewayNotificationOutcomeResult(
        val gatewayAuthorizationStatus: String,
        val errorCode: String? = null
    )

    enum class WalletNotificationOutcome {
        OK,
        SESSION_NOT_FOUND,
        WALLET_NOT_FOUND,
        SECURITY_TOKEN_MISMATCH,
        WRONG_WALLET_STATUS,
        BAD_REQUEST,
        PROCESSING_ERROR
    }

    fun traceWalletUpdate(updateResult: WalletUpdateResult) {
        val attributes =
            Attributes.of(
                UPDATE_WALLET_STATUS_OUTCOME_ATTRIBUTE_KEY,
                updateResult.outcome.name,
                UPDATE_WALLET_STATUS_CURRENT_STATE_ATTRIBUTE_KEY,
                updateResult.walletStatusDto?.value ?: FIELD_NOT_AVAILABLE,
                UPDATE_WALLET_STATUS_DETAILS_TYPE_ATTRIBUTE_KEY,
                updateResult.walletType?.name ?: FIELD_NOT_AVAILABLE,
                UPDATE_WALLET_STATUS_GATEWAY_OUTCOME_ATTRIBUTE_KEY,
                updateResult.gatewayOutcome?.gatewayAuthorizationStatus ?: FIELD_NOT_AVAILABLE,
                UPDATE_WALLET_STATUS_GATEWAY_ERROR_CODE_ATTRIBUTE_KEY,
                updateResult.gatewayOutcome?.errorCode ?: FIELD_NOT_AVAILABLE,
            )
        tracingUtils.addSpan(WALLET_UPDATE_RESULT_SPAN_NAME, attributes)
    }
}
