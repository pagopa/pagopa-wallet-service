package it.pagopa.wallet.config

/*import it.pagopa.wallet.documents.wallets.UuidIdentifiedEntity
import it.pagopa.wallet.domain.wallets.WalletId
import java.util.*
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent
import org.springframework.stereotype.Component

/** TO BE REMOVED IF WE PASS UUID PROGRAMMATICALLY FROM START CREATION */
@Component
class GenerateUUIDListener : AbstractMongoEventListener<UuidIdentifiedEntity>() {
    override fun onBeforeConvert(event: BeforeConvertEvent<UuidIdentifiedEntity>) {
        val entity: UuidIdentifiedEntity = event.source
        if (entity.id == null) {
            entity.id = WalletId(UUID.randomUUID())
        }
    }

    override fun onBeforeSave(event: BeforeSaveEvent<UuidIdentifiedEntity>) {
        val entity: UuidIdentifiedEntity = event.source
        if (entity.id == null) {
            entity.id = WalletId(UUID.randomUUID())
        }
    }
}
*/
