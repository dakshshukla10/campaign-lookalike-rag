package io.github.dakshshukla.lookalike.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextAnalyzerTest {

    @Test
    void lowercasesAndSplitsOnPunctuation() {
        assertThat(TextAnalyzer.tokens("Rice, Lentils; in-store-screens!")).containsExactly("rice", "lentil", "store", "screen");
    }

    @Test
    void dropsStopWords() {
        assertThat(TextAnalyzer.tokens("the pantry and the kitchen")).containsExactly("pantry", "kitchen");
    }

    @Test
    void stemsPluralsAndVerbEndings() {
        assertThat(TextAnalyzer.tokens("toys")).containsExactly("toy");
        assertThat(TextAnalyzer.tokens("batteries")).containsExactly("battery");
        assertThat(TextAnalyzer.tokens("walking")).containsExactly("walk");
        assertThat(TextAnalyzer.tokens("stacked")).containsExactly("stack");
    }

    @Test
    void leavesShortWordsAndDoubleSAlone() {
        assertThat(TextAnalyzer.tokens("gas glass")).containsExactly("gas", "glass");
    }

    @Test
    void nullOrBlankInputGivesNoTokens() {
        assertThat(TextAnalyzer.tokens(null)).isEmpty();
        assertThat(TextAnalyzer.tokens("   ")).isEmpty();
    }
}
