# Synthetic evaluation data

All data in this directory is synthetic. The descriptions, names and performance figures are invented. CTR, ROAS, impressions and other metric values have no real-world meaning.

## Files

- `generate_campaigns.py` uses only the Python 3 standard library. Its default seed is 42.
- `sample_campaigns.csv` contains 120 campaigns and a header row. It keeps the existing columns in their original order and adds `description` as the last column.
- `eval_briefs.json` contains 40 briefs under the `briefs` key. Each brief has an id, description, optional filters and a sorted list of expected campaign ids.

Each of the eight categories has three themes, with five campaigns per theme. Themes cover specific uses, such as pantry staples, skincare launches and dog walks. A theme appears in the campaign name and drives its two-sentence description. The theme assignment stays inside the generator; there is no theme column in the CSV.

Campaign descriptions use three phrasings per theme. Each phrasing contains three theme anchor words. The second sentence describes the audience, channel and objective in plain language. The category, audience, channel and objective columns retain their lowercase slug values.

## Labels and difficulty

Labels come from the generator's theme assignment by rule, without human judgement. A brief is written about one category and theme. Its `expected` list contains every campaign in that group that satisfies the brief's filters.

Brief text uses a separate pool of four phrasings per theme. There are 10 hard briefs with one shared theme anchor word, 20 medium briefs with two, and 10 easy briefs with two and more direct wording about the intended use. These are wording choices, with no guarantee about measured retrieval difficulty or scores. Briefs cover all 24 themes, with 16 themes used a second time.

Twenty briefs set structured filters. All twenty set `category`; some also set `channel`, `objective` or `audience`. Thirteen of those briefs set `budgetUsd`. Unset fields are `null`.

For these labels, category, audience, channel and objective filters require exact matches. `budgetUsd` is the planned spend of the new campaign. It does not change the labels, so it only matters to rankers that use budget closeness. The generator takes the filter values and the budget from one actual member of that group. Every brief has at least two expected ids.

The labels support coverage@1 and coverage@3: a result counts as covered when at least one expected campaign appears in the first one or three results. These scores describe this synthetic dataset only.

## Regenerate and check

Run from the repository root:

```bash
python3 data/generate_campaigns.py
python3 data/generate_campaigns.py --check
```

The first command writes both data files beside the script, then reports the checks. Repeating it with seed 42 reproduces both files byte for byte. The second command checks the existing files against the generated values without rewriting them.

The checks require 120 unique campaign ids, 40 unique brief ids, non-empty labels, existing expected ids, and agreement with every filter. They also check theme sizes, phrasing anchors, difficulty counts, filter counts and complete expected lists.

To generate a separate reproducible variant, choose a seed and an output directory:

```bash
python3 data/generate_campaigns.py --seed 17 --output-dir data/variant
python3 data/generate_campaigns.py --seed 17 --output-dir data/variant --check
```
