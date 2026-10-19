package io.github.dakshshukla.lookalike.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.github.dakshshukla.lookalike.rerank.LexicalCrossScorerReranker;
import io.github.dakshshukla.lookalike.rerank.ModelReranker;
import io.github.dakshshukla.lookalike.rerank.PassthroughReranker;
import io.github.dakshshukla.lookalike.rerank.Reranker;

class RerankConfigTest {

    @Configuration
    @Import(RerankConfig.class)
    static class WithChatClient {
        @Bean
        ChatClient chatClient() {
            return ChatClient.create(prompt -> {
                throw new UnsupportedOperationException();
            });
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(WithChatClient.class);

    @Test
    void defaultsToTheLexicalCrossScorer() {
        runner.run(context -> assertThat(context.getBean(Reranker.class)).isInstanceOf(LexicalCrossScorerReranker.class));
    }

    @Test
    void noneKeepsRetrievalOrder() {
        runner.withPropertyValues("lookalike.rerank.mode=none")
                .run(context -> assertThat(context.getBean(Reranker.class)).isInstanceOf(PassthroughReranker.class));
    }

    @Test
    void modelModeUsesTheModelRerankerWithARealProvider() {
        runner.withPropertyValues("lookalike.rerank.mode=model", "lookalike.ai.provider=openai")
                .run(context -> assertThat(context.getBean(Reranker.class)).isInstanceOf(ModelReranker.class));
    }

    @Test
    void modelModeFailsAtStartupWithTheOfflineProvider() {
        runner.withPropertyValues("lookalike.rerank.mode=model", "lookalike.ai.provider=offline")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasRootCauseMessage(
                                "lookalike.rerank.mode=model needs a real provider; the offline chat model cannot score"
                                        + " candidates. Use the openai profile or pick lexical."));
    }

    @Test
    void unknownModeFailsAtStartup() {
        runner.withPropertyValues("lookalike.rerank.mode=bert")
                .run(context -> assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class));
    }
}
