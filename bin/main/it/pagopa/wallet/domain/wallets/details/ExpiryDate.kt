package it.pagopa.wallet.domain.wallets.details

import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ExpiryDate(val expDate: String) {

    companion object {
        val expiryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")

        fun fromYearMonth(date: YearMonth) = ExpiryDate(expiryDateFormatter.format(date))
    }

    init {
        require(runCatching { YearMonth.parse(expDate, expiryDateFormatter) }.isSuccess)
    }
}
