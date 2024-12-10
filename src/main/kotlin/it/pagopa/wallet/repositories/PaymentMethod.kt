package it.pagopa.wallet.repositories

import it.pagopa.generated.ecommerce.model.PaymentMethodManagementType
import it.pagopa.generated.ecommerce.model.PaymentMethodStatus
import it.pagopa.generated.ecommerce.model.Range
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.redis.core.RedisHash
import org.springframework.lang.NonNull

@RedisHash(value = "payment-methods")
data class PaymentMethod
@PersistenceCreator
constructor(
    @NonNull @Id val id: String,
    @NonNull val name: String,
    @NonNull val description: String,
    val asset: String?,
    @NonNull val status: PaymentMethodStatus,
    @NonNull val paymentTypeCode: String,
    @NonNull val methodManagement: PaymentMethodManagementType,
    @NonNull val ranges: List<Range>,
    val brandAssets: Map<String, String>?,
) {}
