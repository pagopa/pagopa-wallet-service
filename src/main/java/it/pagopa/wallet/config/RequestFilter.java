package it.pagopa.wallet.config;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestFilter implements WebFilter {

    public static final String CONTEXT_KEY = "contextKey";

    @Override
    public Mono<Void> filter(
        ServerWebExchange exchange,
        WebFilterChain chain
    ) {
      final HttpHeaders headers = exchange.getRequest().getHeaders();

      return chain.filter(exchange)
          .contextWrite(Context.of(CONTEXT_KEY, UUID.randomUUID().toString()));
    }

}
