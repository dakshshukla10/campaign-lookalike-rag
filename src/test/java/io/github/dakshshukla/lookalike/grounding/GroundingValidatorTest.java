package io.github.dakshshukla.lookalike.grounding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.Citation;
import io.github.dakshshukla.lookalike.domain.Recommendation;

class GroundingValidatorTest {

    private final GroundingValidator validator = new GroundingValidator();

    private static Campaign campaign(String id) {
        return new Campaign(id, "name " + id, "grocery", "families", "display", "awareness", 1000, 14, 100_000L, 0.01, 2.0);
    }

    private static Recommendation rec(int rank, String... ids) {
        return new Recommendation(
                rank, "summary " + rank, java.util.Arrays.stream(ids).map(id -> new Citation(id, "because")).toList());
    }

    private final List<Campaign> retrieved = List.of(campaign("C-001"), campaign("C-002"), campaign("C-003"));

    @Test
    void acceptsRecommendationsWhoseCitationsWereAllRetrieved() {
        GroundingResult result = validator.validate(List.of(rec(1, "C-001", "C-002"), rec(2, "C-003")), retrieved);

        assertThat(result.accepted()).hasSize(2);
        assertThat(result.violations()).isEmpty();
        assertThat(result.fullyGrounded()).isTrue();
        assertThat(result.citationsChecked()).isEqualTo(3);
        assertThat(result.citationsGrounded()).isEqualTo(3);
    }

    @Test
    void rejectsCitationOfCampaignThatWasNotRetrieved() {
        GroundingResult result = validator.validate(List.of(rec(1, "C-001"), rec(2, "C-999")), retrieved);

        assertThat(result.accepted()).extracting(Recommendation::rank).containsExactly(1);
        assertThat(result.violations()).containsExactly(new Violation(2, "C-999", Violation.Reason.NOT_RETRIEVED));
        assertThat(result.fullyGrounded()).isFalse();
    }

    @Test
    void rejectsWholeRecommendationWhenOneOfItsCitationsIsInvented() {
        GroundingResult result = validator.validate(List.of(rec(1, "C-001", "C-404")), retrieved);

        assertThat(result.accepted()).isEmpty();
        assertThat(result.citationsChecked()).isEqualTo(2);
        assertThat(result.citationsGrounded()).isEqualTo(1);
    }

    @Test
    void rejectsRecommendationWithoutCitations() {
        GroundingResult result = validator.validate(List.of(rec(1)), retrieved);

        assertThat(result.accepted()).isEmpty();
        assertThat(result.violations()).containsExactly(new Violation(1, null, Violation.Reason.NO_CITATIONS));
    }

    @Test
    void rejectsNullAndBlankCitationIds() {
        Recommendation withNull = new Recommendation(1, "s", List.of(new Citation(null, "x")));
        Recommendation withBlank = new Recommendation(2, "s", List.of(new Citation("  ", "x")));

        GroundingResult result = validator.validate(List.of(withNull, withBlank), retrieved);

        assertThat(result.accepted()).isEmpty();
        assertThat(result.violations()).extracting(Violation::reason).containsOnly(Violation.Reason.BLANK_ID);
        assertThat(result.violations()).hasSize(2);
    }

    @Test
    void matchesIdsExactlyWithoutFixingCaseOrWhitespace() {
        GroundingResult result = validator.validate(List.of(rec(1, "c-001"), rec(2, " C-002")), retrieved);

        assertThat(result.accepted()).isEmpty();
        assertThat(result.violations()).extracting(Violation::reason).containsOnly(Violation.Reason.NOT_RETRIEVED);
    }

    @Test
    void rejectsEverythingWhenNothingWasRetrieved() {
        GroundingResult result = validator.validate(List.of(rec(1, "C-001")), List.of());

        assertThat(result.accepted()).isEmpty();
        assertThat(result.violations()).hasSize(1);
    }

    @Test
    void noRecommendationsIsTriviallyGrounded() {
        GroundingResult result = validator.validate(List.of(), retrieved);

        assertThat(result.accepted()).isEmpty();
        assertThat(result.fullyGrounded()).isTrue();
        assertThat(result.citationsChecked()).isZero();
    }
}
