package it.pagopa.wallet.exception

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import org.springframework.http.HttpStatus

class WalletConflictStatusException(
    val walletId: WalletId,
    val walletStatusDto: WalletStatusDto,
    allowedStatuses: Set<WalletStatusDto>,
    val walletDetailsType: WalletDetailsType?
) :
    ApiError(
        "Conflict with walletId [${walletId.value}] with status [${walletStatusDto.value}]. Allowed statuses $allowedStatuses"
    ) {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.CONFLICT, "Conflict", message!!)
}
