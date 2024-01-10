package it.pagopa.wallet.documents.wallets.details

import it.pagopa.generated.wallet.model.WalletCardDetailsDto
import it.pagopa.wallet.domain.wallets.details.Bin
import it.pagopa.wallet.domain.wallets.details.CardHolderName
import it.pagopa.wallet.domain.wallets.details.ExpiryDate
import it.pagopa.wallet.domain.wallets.details.MaskedPan

data class CardDetails(
    val type: String,
    val bin: String,
    val maskedPan: String,
    val expiryDate: String,
    val brand: String,
    val holder: String
) : WalletDetails<CardDetails> {
    override fun toDomain() =
        it.pagopa.wallet.domain.wallets.details.CardDetails(
            Bin(bin),
            MaskedPan(maskedPan),
            ExpiryDate(expiryDate),
            WalletCardDetailsDto.BrandEnum.valueOf(brand),
            CardHolderName(holder)
        )
}
