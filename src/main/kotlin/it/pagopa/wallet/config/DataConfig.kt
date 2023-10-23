package it.pagopa.wallet.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.ReactiveAuditorAware
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing
import reactor.core.publisher.Mono

@Configuration
@EnableReactiveMongoAuditing
class DataConfig {
    @Bean
    fun auditorAware(): ReactiveAuditorAware<String> {
        return ReactiveAuditorAware { Mono.just("wallet") }
    }
}
