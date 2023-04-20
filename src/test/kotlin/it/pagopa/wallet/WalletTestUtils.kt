package it.pagopa.wallet

import it.pagopa.generated.npg.model.*
import java.net.URI

object WalletTestUtils {

    fun hppRequest() =
        HppRequest()
            .order(
                OrderItem().orderId("orderId").amount("0").currency("EUR").customerId("customerId")
            )
            .paymentSession(
                PaymentSessionItem()
                    .actionType(PaymentSessionItem.ActionTypeEnum.VERIFY)
                    .amount("0")
                    .language("ita")
                    .resultUrl(URI.create("http://localhost/resultUrl"))
                    .cancelUrl(URI.create("http://localhost/cancelUrl"))
                    .notificationUrl(URI.create("http://localhost/notificationUrl"))
                    .paymentService(PaymentSessionItem.PaymentServiceEnum.CARDS)
                    .recurrence(
                        RecurrenceItem()
                            .action(RecurrenceItem.ActionEnum.CONTRACT_CREATION)
                            .contractId("contractId")
                            .contractType(RecurrenceItem.ContractTypeEnum.CIT)
                    )
            )

    fun hppResponse() =
        HppResponse().hostedPage("http://localhost/hostedPage").securityToken("securityToken")
}
