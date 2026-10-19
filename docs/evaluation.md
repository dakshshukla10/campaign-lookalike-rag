# Evaluation

All numbers on this page come from the synthetic data in `data/` and the default offline
models. The campaigns are generated from word lists, the briefs are paraphrases of those lists,
and the labels come from the generator's theme assignment by rule. A high score here says the
pipeline works on easy lexical structure. It says nothing about real campaigns.

## What is measured

- coverage@1 and coverage@3: the share of briefs where the first recommendation, or one of the
  first three, cites at least one expected campaign.
- grounding rate: citations in the ranker's first answer that point at a retrieved campaign,
  divided by all citations in that answer. Retries and fallbacks are not counted.
- Next to those, the report shows the same coverage for the reranked list alone, the share of
  briefs with an expected campaign among the retrieved candidates, precision@3, and how many
  briefs needed a fallback slot.

## Latest run

`./gradlew eval` on 40 labelled briefs (20 with filters, 20 text only), 120 campaigns:

| Metric | Value |
| --- | --- |
| coverage@1 | 95.0% |
| coverage@3 | 95.0% |
| grounding rate (first attempt) | 100.0% |
| rerank-only coverage@1 / @3 | 95.0% / 95.0% |
| retrieval recall | 95.0% |
| precision@3 | 86.7% |
| briefs needing a fallback slot | 0.0% |

The two briefs that miss (B-008 and B-036) have no expected campaign among the 20 retrieved
candidates, so the loss happens in vector search and the reranker cannot repair it. Coverage@1
and coverage@3 are equal because each theme has five campaigns, so when the top hit is right the
next two usually are too. The grounding rate is 100% because the offline ranker copies ids from
its prompt. It would only move with a different chat model or a code change that breaks
citations, which is what the gate is there to catch.

## The CI gate

`eval/baseline.json` holds the reference values and how far each may fall:

```json
{ "metrics": { "coverageAt1": 0.95, "coverageAt3": 0.95, "groundingRate": 1.0 },
  "maxDrop": { "coverageAt1": 0.025, "coverageAt3": 0.025, "groundingRate": 0.0 } }
```

`./gradlew eval` runs `EvalGateTest` against the Testcontainers Postgres and Redis, prints the
report, writes `build/reports/eval/eval-report.{md,json}` and fails when a metric is below its
floor. `check` depends on `eval`, so `./gradlew build` in CI runs it and the job summary shows
the table. With 40 briefs one brief is worth 2.5 points, so a single lost brief sits exactly on
the allowed drop and two lost briefs fail the build.

To change the baseline on purpose, for example after a prompt or model change that moves the
numbers, run `./gradlew eval -PevalUpdateBaseline=true`, look at the diff, and commit it.

## Feedback replay

`OutcomeReplayTest` records outcomes by running half of the briefs and marking each recommended
campaign helpful when it was an expected one. It then scores the other half with and without
re-weighting.

- Held-out half, default strength 0.05: no ranking changed, coverage stayed at 90% (20 briefs).
- A one-off sweep of strengths 0.02, 0.05, 0.10, 0.15 and 0.25 on both halves showed no change
  up to 0.05. From 0.10 up, one held-out brief in the first split dropped out of the top 3
  (90% to 85%), because a campaign that was helpful for another brief outranked the right one.
  The second split stayed at 100% throughout. That is why the default is 0.05.
- On the briefs the outcomes came from, strength 0.5: precision@3 went from 93.3% to 95.0% with
  true outcomes. With the verdicts flipped it stayed at 93.3% while two rankings changed.

These are 20-brief samples, so one brief is 5 points. They show the mechanism moves rankings in
the direction the outcomes say. They do not show that feedback improves recommendations on real
data.
