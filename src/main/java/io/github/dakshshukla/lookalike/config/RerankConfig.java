package io.github.dakshshukla.lookalike.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.dakshshukla.lookalike.rerank.LexicalCrossScorerReranker;
import io.github.dakshshukla.lookalike.rerank.ModelReranker;
import io.github.dakshshukla.lookalike.rerank.PassthroughReranker;
import io.github.dakshshukla.lookalike.rerank.Reranker;

/**
 * Picks the reranker with {@code lookalike.rerank.mode}: {@code lexical} (default), {@code none}
 * or {@code model}. The model mode needs a real chat provider because the offline chat model
 * only knows how to rank, not to score.
 */
@Configuration
public class RerankConfig {

    @Bean
    Reranker reranker(
            @Value("${lookalike.rerank.mode:lexical}") String mode,
            @Value("${lookalike.ai.provider:offline}") String provider,
            @Value("${lookalike.rerank.retrieval-weight:0.4}") double retrievalWeight,
            @Value("${lookalike.rerank.cross-weight:0.6}") double crossWeight,
            ObjectProvider<ChatClient> chatClient) {
        LexicalCrossScorerReranker lexical = new LexicalCrossScorerReranker(retrievalWeight, crossWeight);
        return switch (mode.strip().toLowerCase()) {
            case "lexical" -> lexical;
            case "none" -> new PassthroughReranker();
            case "model" -> {
                if ("offline".equals(provider)) {
                    throw new IllegalStateException(
                            "lookalike.rerank.mode=model needs a real provider; the offline chat model cannot score"
                                    + " candidates. Use the openai profile or pick lexical.");
                }
                yield new ModelReranker(chatClient.getObject(), lexical, retrievalWeight, crossWeight);
            }
            default -> throw new IllegalStateException(
                    "Unknown lookalike.rerank.mode '" + mode + "', expected lexical, none or model");
        };
    }
}
