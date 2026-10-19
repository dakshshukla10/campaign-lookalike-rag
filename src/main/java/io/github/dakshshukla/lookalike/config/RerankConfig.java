package io.github.dakshshukla.lookalike.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.dakshshukla.lookalike.rerank.PassthroughReranker;
import io.github.dakshshukla.lookalike.rerank.Reranker;

@Configuration
public class RerankConfig {

    @Bean
    Reranker reranker() {
        return new PassthroughReranker();
    }
}
