package io.github.dakshshukla.lookalike.feedback;

/** How often reports called a campaign helpful. */
public record OutcomeStats(int helpful, int total) {

    public OutcomeStats {
        if (helpful < 0 || total < helpful) {
            throw new IllegalArgumentException("helpful must be between 0 and total");
        }
    }

    /**
     * Where this campaign stands between -1 (always unhelpful) and +1 (always helpful). One
     * imaginary report each way is mixed in, so a single report moves the value only a little and
     * a campaign with no reports sits at 0.
     */
    public double signal() {
        double smoothed = (helpful + 1.0) / (total + 2.0);
        return 2 * smoothed - 1;
    }
}
