package it.pagopa.wallet.exception

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class WalletConflictStatusException(
    val walletId: WalletId,
    walletStatusDto: WalletStatusDto,
    allowedStatuses: Set<WalletStatusDto>
) :
    ApiError(
        "Conflict with walletId [${walletId.value}] with status [${walletStatusDto.value}]. Allowed statuses $allowedStatuses"
    ) {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.CONFLICT, "Conflict", message!!)
}
