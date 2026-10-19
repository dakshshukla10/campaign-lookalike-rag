package io.github.dakshshukla.lookalike.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import io.github.dakshshukla.lookalike.text.TextAnalyzer;

/**
 * Offline embedder: hashes unigrams and bigrams into a fixed-size signed vector and
 * L2-normalises it. It is lexical, so it matches shared words and not meaning, but it is
 * deterministic and needs no network, which is what tests and CI want.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private static final double BIGRAM_WEIGHT = 0.5;

    private final int dimensions;

    public HashingEmbeddingModel(int dimensions) {
        if (dimensions < 16) {
            throw new IllegalArgumentException("dimensions must be at least 16, got " + dimensions);
        }
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> out = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            out.add(new Embedding(embedText(texts.get(i)), i));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    float[] embedText(String text) {
        double[] vector = new double[dimensions];
        List<String> tokens = TextAnalyzer.tokens(text);
        for (int i = 0; i < tokens.size(); i++) {
            add(vector, tokens.get(i), 1.0);
            if (i + 1 < tokens.size()) {
                add(vector, tokens.get(i) + " " + tokens.get(i + 1), BIGRAM_WEIGHT);
            }
        }
        double norm = 0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        float[] result = new float[dimensions];
        if (norm == 0) {
            return result;
        }
        for (int i = 0; i < dimensions; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    private void add(double[] vector, String feature, double weight) {
        long hash = fnv1a(feature);
        int slot = (int) Long.remainderUnsigned(hash, dimensions);
        // The top bit picks the sign so unrelated words that collide tend to cancel out.
        double sign = (hash >>> 63) == 0 ? 1.0 : -1.0;
        vector[slot] += sign * weight;
    }

    private static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
