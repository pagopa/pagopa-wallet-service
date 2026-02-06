package it.pagopa.wallet.repositories

import java.time.OffsetDateTime
import lombok.Data
import org.springframework.data.annotation.Id
import org.springframework.lang.NonNull

@Data
class UniqueIdDocument(@NonNull @Id val id: String, val creationDate: String) {

    constructor(id: String) : this(id, OffsetDateTime.now().toString())
}
