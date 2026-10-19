package io.github.dakshshukla.lookalike.grounding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.Citation;
import io.github.dakshshukla.lookalike.domain.Recommendation;

/**
 * Rejects any recommendation that cites a campaign id which was not retrieved for the request,
 * so the LLM cannot invent sources.
 *
 * <p>Rules, applied per recommendation:
 * <ul>
 *   <li>no citations: rejected ({@link Violation.Reason#NO_CITATIONS});</li>
 *   <li>a null or blank cited id: rejected ({@link Violation.Reason#BLANK_ID});</li>
 *   <li>a cited id outside the retrieved set: rejected ({@link Violation.Reason#NOT_RETRIEVED}).</li>
 * </ul>
 * A recommendation is accepted only if all of its citations are grounded. Matching is exact
 * (case-sensitive, no trimming) so ids are never
 * silently "fixed".
 */
@Component
public class GroundingValidator {

    public GroundingResult validate(List<Recommendation> recommendations, Collection<Campaign> retrieved) {
        Set<String> retrievedIds = retrieved.stream().map(Campaign::id).collect(Collectors.toCollection(HashSet::new));

        List<Recommendation> accepted = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        int checked = 0;
        int grounded = 0;

        for (Recommendation rec : recommendations) {
            if (rec.citations().isEmpty()) {
                violations.add(new Violation(rec.rank(), null, Violation.Reason.NO_CITATIONS));
                continue;
            }
            boolean recGrounded = true;
            for (Citation citation : rec.citations()) {
                checked++;
                String id = citation.campaignId();
                if (id == null || id.isBlank()) {
                    violations.add(new Violation(rec.rank(), id, Violation.Reason.BLANK_ID));
                    recGrounded = false;
                } else if (!retrievedIds.contains(id)) {
                    violations.add(new Violation(rec.rank(), id, Violation.Reason.NOT_RETRIEVED));
                    recGrounded = false;
                } else {
                    grounded++;
                }
            }
            if (recGrounded) {
                accepted.add(rec);
            }
        }
        return new GroundingResult(accepted, violations, checked, grounded);
    }
}
