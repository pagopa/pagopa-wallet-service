package it.pagopa.wallet.domain.wallets.details

import java.time.format.DateTimeFormatter

data class ExpiryDate(val expDate: String) {
    init {
         companion object {
        val expiryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("YYYYMM")
    }

    init {
        require(runCatching { (expiryDateFormatter.parse(expDate)) }.isSuccess)
    }
    }
}
