package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.common.tracing.WalletTracing
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import it.pagopa.wallet.exception.*

fun errorToWalletNotificationOutcome(error: Throwable) =
    when (error) {
        is SessionNotFoundException -> WalletTracing.WalletNotificationOutcome.SESSION_NOT_FOUND
        is SecurityTokenMatchException ->
            WalletTracing.WalletNotificationOutcome.SECURITY_TOKEN_MISMATCH
        is WalletNotFoundException,
        is WalletSessionMismatchException ->
            WalletTracing.WalletNotificationOutcome.WALLET_NOT_FOUND
        is WalletConflictStatusException ->
            WalletTracing.WalletNotificationOutcome.WRONG_WALLET_STATUS
        else -> WalletTracing.WalletNotificationOutcome.PROCESSING_ERROR
    }

fun extractWalletStatusFromError(error: Throwable): WalletStatusDto? =
    when (error) {
        is WalletConflictStatusException -> error.walletStatusDto
        else -> null
    }

fun extractWalletTypeFromError(error: Throwable): WalletDetailsType? =
    when (error) {
        is WalletConflictStatusException -> error.walletDetailsType
        else -> null
    }
