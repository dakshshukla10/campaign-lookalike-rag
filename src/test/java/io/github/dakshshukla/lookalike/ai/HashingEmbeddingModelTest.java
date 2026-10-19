package io.github.dakshshukla.lookalike.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel(128);

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    @Test
    void sameTextGivesTheSameVectorAcrossInstances() {
        assertThat(model.embed("winter skincare launch")).containsExactly(new HashingEmbeddingModel(128).embed("winter skincare launch"));
    }

    @Test
    void vectorLengthMatchesTheConfiguredDimensions() {
        assertThat(model.embed("anything")).hasSize(128);
        assertThat(model.dimensions()).isEqualTo(128);
    }

    @Test
    void nonEmptyTextIsUnitLength() {
        float[] vector = model.embed("rice and lentils for a pantry");

        assertThat(cosine(vector, vector)).isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void emptyTextStillGivesAUnitVectorSoCosineStaysDefined() {
        float[] empty = model.embed("");

        assertThat(cosine(empty, empty)).isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(model.embed("the and of")).containsExactly(empty);
    }

    @Test
    void everyPairOfTextsHasPositiveSimilarity() {
        assertThat(cosine(model.embed("rice lentils pantry"), model.embed("dog leash harness"))).isPositive();
        assertThat(cosine(model.embed(""), model.embed("dog leash harness"))).isPositive();
    }

    @Test
    void sharedWordsScoreHigherThanUnrelatedText() {
        float[] query = model.embed("skincare launch for dry winter skin");
        float[] related = model.embed("a winter skincare launch with night cream");
        float[] unrelated = model.embed("leashes and harnesses for dog walks");

        assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated) + 0.2);
    }

    @Test
    void wordOrderChangesTheVectorOnlyALittle() {
        float[] a = model.embed("fresh bread daily");
        float[] b = model.embed("daily fresh bread");

        assertThat(cosine(a, b)).isBetween(0.5, 0.999);
    }

    @Test
    void pluralsAndCaseDoNotMatter() {
        assertThat(model.embed("Toys")).containsExactly(model.embed("toy"));
    }

    @Test
    void tooFewDimensionsAreRejected() {
        assertThatThrownBy(() -> new HashingEmbeddingModel(8)).isInstanceOf(IllegalArgumentException.class);
    }
}
