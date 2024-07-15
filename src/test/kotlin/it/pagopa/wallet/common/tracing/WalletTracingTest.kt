package it.pagopa.wallet.common.tracing

import io.opentelemetry.api.common.Attributes
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class WalletTracingTest {

    private val tracingUtils = TracingUtilsTest.getMock()
    private val walletTracing = WalletTracing(tracingUtils)

    @ParameterizedTest
    @EnumSource(WalletTracing.WalletNotificationOutcome::class)
    fun shouldTraceWalletUpdates(walletOutcome: WalletTracing.WalletNotificationOutcome) {
        walletTracing.traceWalletUpdate(
            WalletTracing.WalletUpdateResult(
                walletOutcome,
                WalletDetailsType.PAYPAL,
                WalletStatusDto.CREATED,
                WalletTracing.GatewayNotificationOutcomeResult("EXECUTED")
            )
        )

        val expectedAttributes =
            Attributes.of(
                WalletTracing.UPDATE_WALLET_STATUS_OUTCOME_ATTRIBUTE_KEY,
                walletOutcome.name,
                WalletTracing.UPDATE_WALLET_STATUS_CURRENT_STATE_ATTRIBUTE_KEY,
                "CREATED",
                WalletTracing.UPDATE_WALLET_STATUS_DETAILS_TYPE_ATTRIBUTE_KEY,
                WalletDetailsType.PAYPAL.name,
                WalletTracing.UPDATE_WALLET_STATUS_GATEWAY_OUTCOME_ATTRIBUTE_KEY,
                "EXECUTED",
                WalletTracing.UPDATE_WALLET_STATUS_GATEWAY_ERROR_CODE_ATTRIBUTE_KEY,
                "N/A"
            )

        verify(tracingUtils, times(1))
            .addSpan(WalletTracing.WALLET_UPDATE_RESULT_SPAN_NAME, expectedAttributes)
    }

    @ParameterizedTest
    @EnumSource(WalletTracing.WalletNotificationOutcome::class)
    fun shouldTraceWalletUpdatesWithErrors(walletOutcome: WalletTracing.WalletNotificationOutcome) {
        walletTracing.traceWalletUpdate(
            WalletTracing.WalletUpdateResult(
                walletOutcome,
                WalletDetailsType.CARDS,
                WalletStatusDto.CREATED,
                WalletTracing.GatewayNotificationOutcomeResult("EXECUTED", "GW001")
            )
        )

        val expectedAttributes =
            Attributes.of(
                WalletTracing.UPDATE_WALLET_STATUS_OUTCOME_ATTRIBUTE_KEY,
                walletOutcome.name,
                WalletTracing.UPDATE_WALLET_STATUS_CURRENT_STATE_ATTRIBUTE_KEY,
                "CREATED",
                WalletTracing.UPDATE_WALLET_STATUS_DETAILS_TYPE_ATTRIBUTE_KEY,
                WalletDetailsType.CARDS.name,
                WalletTracing.UPDATE_WALLET_STATUS_GATEWAY_OUTCOME_ATTRIBUTE_KEY,
                "EXECUTED",
                WalletTracing.UPDATE_WALLET_STATUS_GATEWAY_ERROR_CODE_ATTRIBUTE_KEY,
                "GW001"
            )

        verify(tracingUtils, times(1))
            .addSpan(WalletTracing.WALLET_UPDATE_RESULT_SPAN_NAME, expectedAttributes)
    }
}
