package it.pagopa.wallet.documents.wallets.details

import it.pagopa.generated.wallet.model.WalletCardDetailsDto
import it.pagopa.wallet.domain.details.*
import it.pagopa.wallet.domain.details.CardDetails

data class CardDetails(
    val type: String,
    val bin: String,
    val maskedPan: String,
    val expiryDate: String,
    val brand: String,
    val holder: String
) : WalletDetails {
    override fun toDomain() =
        CardDetails(
            Bin(bin),
            MaskedPan(maskedPan),
            ExpiryDate(expiryDate),
            WalletCardDetailsDto.BrandEnum.valueOf(brand),
            CardHolderName(holder)
        )
}
