package io.github.dakshshukla.lookalike.config;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.github.dakshshukla.lookalike.ai.HashingEmbeddingModel;
import io.github.dakshshukla.lookalike.ai.OfflineChatModel;
import io.github.dakshshukla.lookalike.cache.CachingEmbeddingModel;
import io.github.dakshshukla.lookalike.rerank.LlmRecommendationRanker;
import io.github.dakshshukla.lookalike.rerank.RecommendationRanker;

/**
 * Chooses the models. {@code lookalike.ai.provider=offline} (the default) uses the hashing
 * embedder and the rule-based chat model. The {@code openai} profile turns the Spring AI
 * OpenAI auto-configuration on instead; see {@code application-openai.properties}.
 */
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnProperty(name = "lookalike.ai.provider", havingValue = "offline", matchIfMissing = true)
    EmbeddingModel hashingEmbeddingModel(@Value("${lookalike.ai.embedding-dimensions:256}") int dimensions) {
        return new HashingEmbeddingModel(dimensions);
    }

    @Bean
    @ConditionalOnProperty(name = "lookalike.ai.provider", havingValue = "offline", matchIfMissing = true)
    ChatModel offlineChatModel() {
        return new OfflineChatModel();
    }

    @Bean
    ChatClient rankingChatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean
    RecommendationRanker recommendationRanker(ChatClient rankingChatClient) {
        return new LlmRecommendationRanker(rankingChatClient);
    }

    /**
     * Wraps whichever embedding model is active, offline or remote, with the Redis cache. Static
     * so the post-processor is available before the other beans are created.
     */
    @Bean
    static BeanPostProcessor embeddingCachePostProcessor(
            ObjectProvider<StringRedisTemplate> redis,
            @Value("${lookalike.cache.enabled:true}") boolean enabled,
            @Value("${lookalike.cache.ttl:PT24H}") Duration ttl) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (enabled && bean instanceof EmbeddingModel model && !(bean instanceof CachingEmbeddingModel)) {
                    StringRedisTemplate template = redis.getIfAvailable();
                    if (template != null) {
                        return new CachingEmbeddingModel(model, template, ttl);
                    }
                }
                return bean;
            }
        };
    }
}
