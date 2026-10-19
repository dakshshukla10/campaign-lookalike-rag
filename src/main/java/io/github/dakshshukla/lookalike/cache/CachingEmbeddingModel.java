package io.github.dakshshukla.lookalike.cache;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Keeps embeddings in Redis keyed by a hash of the text, so a repeated brief does not hit the
 * embedding provider twice. Redis is treated as an optimisation: any Redis error falls through
 * to the wrapped model.
 */
public class CachingEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(CachingEmbeddingModel.class);

    private final EmbeddingModel delegate;
    private final StringRedisTemplate redis;
    private final Duration ttl;
    private volatile String keyPrefix;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public CachingEmbeddingModel(EmbeddingModel delegate, StringRedisTemplate redis, Duration ttl) {
        this.delegate = delegate;
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        float[][] vectors = new float[texts.size()][];
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            vectors[i] = lookup(texts.get(i));
            if (vectors[i] == null) {
                missing.add(i);
            }
        }
        hits.addAndGet(texts.size() - missing.size());
        misses.addAndGet(missing.size());

        if (!missing.isEmpty()) {
            List<String> missingTexts = missing.stream().map(texts::get).toList();
            EmbeddingResponse fresh = delegate.call(new EmbeddingRequest(missingTexts, request.getOptions()));
            for (int j = 0; j < missing.size(); j++) {
                float[] vector = fresh.getResults().get(j).getOutput();
                vectors[missing.get(j)] = vector;
                store(texts.get(missing.get(j)), vector);
            }
        }

        List<Embedding> results = new ArrayList<>();
        for (int i = 0; i < vectors.length; i++) {
            results.add(new Embedding(vectors[i], i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    private float[] lookup(String text) {
        try {
            String encoded = redis.opsForValue().get(key(text));
            return encoded == null ? null : decode(encoded);
        } catch (RuntimeException e) {
            log.warn("Embedding cache read failed, using the model: {}", e.toString());
            return null;
        }
    }

    private void store(String text, float[] vector) {
        try {
            redis.opsForValue().set(key(text), encode(vector), ttl);
        } catch (RuntimeException e) {
            log.warn("Embedding cache write failed: {}", e.toString());
        }
    }

    private String keyPrefix() {
        if (keyPrefix == null) {
            // Model name and size are part of the key so switching providers never serves stale
            // vectors. Resolved lazily because a remote model may need a call to report its size.
            keyPrefix = "lookalike:emb:" + delegate.getClass().getSimpleName() + ":" + delegate.dimensions() + ":";
        }
        return keyPrefix;
    }

    private String key(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return keyPrefix() + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.asFloatBuffer().put(vector);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    static float[] decode(String encoded) {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
        float[] vector = new float[buffer.remaining() / Float.BYTES];
        buffer.asFloatBuffer().get(vector);
        return vector;
    }
}
