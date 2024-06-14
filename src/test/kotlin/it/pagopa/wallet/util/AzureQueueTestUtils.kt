package it.pagopa.wallet.util

import com.azure.core.http.HttpHeaders
import com.azure.core.http.HttpRequest
import com.azure.core.http.rest.Response
import com.azure.storage.queue.models.SendMessageResult
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono

object AzureQueueTestUtils {
    var QUEUE_SUCCESSFUL_RESULT: Mono<SendMessageResult> = Mono.just(SendMessageResult())

    var QUEUE_SUCCESSFUL_RESPONSE: Mono<Response<SendMessageResult>> =
        Mono.just(
            object : Response<SendMessageResult> {
                override fun getStatusCode(): Int = 200

                override fun getHeaders(): HttpHeaders = HttpHeaders()

                override fun getRequest(): HttpRequest = mock()

                override fun getValue(): SendMessageResult = SendMessageResult()
            }
        )
}
