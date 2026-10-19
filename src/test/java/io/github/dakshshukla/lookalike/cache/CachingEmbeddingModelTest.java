package io.github.dakshshukla.lookalike.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.github.dakshshukla.lookalike.ai.HashingEmbeddingModel;
import io.github.dakshshukla.lookalike.support.IntegrationTest;

@IntegrationTest
class CachingEmbeddingModelTest {

    /** Counts the texts that actually reach the wrapped model. */
    private static final class CountingModel implements EmbeddingModel {
        final HashingEmbeddingModel inner = new HashingEmbeddingModel(32);
        final AtomicInteger texts = new AtomicInteger();

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            texts.addAndGet(request.getInstructions().size());
            return inner.call(request);
        }

        @Override
        public float[] embed(Document document) {
            return inner.embed(document);
        }

        @Override
        public int dimensions() {
            return inner.dimensions();
        }
    }

    @Autowired
    private StringRedisTemplate redis;

    private static String unique(String text) {
        return text + " " + System.nanoTime();
    }

    @Test
    void secondCallForTheSameTextIsServedFromRedis() {
        var delegate = new CountingModel();
        var cached = new CachingEmbeddingModel(delegate, redis, Duration.ofMinutes(5));
        String text = unique("pantry rice lentils");

        float[] first = cached.embed(text);
        float[] second = cached.embed(text);

        assertThat(second).containsExactly(first);
        assertThat(delegate.texts.get()).isEqualTo(1);
        assertThat(cached.hits()).isEqualTo(1);
        assertThat(cached.misses()).isEqualTo(1);
    }

    @Test
    void aSeparateInstanceSharesTheCacheThroughRedis() {
        String text = unique("winter skincare cream");
        new CachingEmbeddingModel(new CountingModel(), redis, Duration.ofMinutes(5)).embed(text);
        var delegate = new CountingModel();

        float[] vector = new CachingEmbeddingModel(delegate, redis, Duration.ofMinutes(5)).embed(text);

        assertThat(delegate.texts.get()).isZero();
        assertThat(vector).containsExactly(new HashingEmbeddingModel(32).embed(text));
    }

    @Test
    void differentTextIsAMiss() {
        var delegate = new CountingModel();
        var cached = new CachingEmbeddingModel(delegate, redis, Duration.ofMinutes(5));

        cached.embed(unique("one"));
        cached.embed(unique("two"));

        assertThat(delegate.texts.get()).isEqualTo(2);
        assertThat(cached.hits()).isZero();
    }

    @Test
    void aBatchWithHitsAndMissesKeepsTheOriginalOrder() {
        var delegate = new CountingModel();
        var cached = new CachingEmbeddingModel(delegate, redis, Duration.ofMinutes(5));
        String a = unique("alpha beta");
        String b = unique("gamma delta");
        String c = unique("epsilon zeta");
        cached.embed(b);
        delegate.texts.set(0);

        List<float[]> vectors = cached.embed(List.of(a, b, c));

        var plain = new HashingEmbeddingModel(32);
        assertThat(vectors.get(0)).containsExactly(plain.embed(a));
        assertThat(vectors.get(1)).containsExactly(plain.embed(b));
        assertThat(vectors.get(2)).containsExactly(plain.embed(c));
        assertThat(delegate.texts.get()).isEqualTo(2);
    }

    @Test
    void entriesExpireAfterTheTtl() throws InterruptedException {
        var delegate = new CountingModel();
        var cached = new CachingEmbeddingModel(delegate, redis, Duration.ofSeconds(1));
        String text = unique("short lived");
        cached.embed(text);

        long deadline = System.currentTimeMillis() + 3000;
        int before = delegate.texts.get();
        while (delegate.texts.get() == before && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            cached.embed(text);
        }

        assertThat(delegate.texts.get()).isGreaterThan(before);
    }

    @Test
    void anUnreachableRedisFallsThroughToTheModel() {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        factory.afterPropertiesSet();
        var broken = new StringRedisTemplate(factory);
        broken.afterPropertiesSet();
        var delegate = new CountingModel();
        var cached = new CachingEmbeddingModel(delegate, broken, Duration.ofMinutes(5));

        float[] vector = cached.embed("still works");

        assertThat(vector).containsExactly(new HashingEmbeddingModel(32).embed("still works"));
        assertThat(delegate.texts.get()).isEqualTo(1);
        factory.destroy();
    }

    @Test
    void theApplicationEmbeddingModelIsWrappedWithTheCache(@Autowired EmbeddingModel model) {
        assertThat(model).isInstanceOf(CachingEmbeddingModel.class);
        assertThat(model.dimensions()).isEqualTo(256);
    }
}
