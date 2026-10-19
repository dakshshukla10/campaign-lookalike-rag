# Sample data

`sample_campaigns.csv` holds 30 **synthetic** campaigns produced by `generate_campaigns.py`
(fixed random seed, so it is reproducible). It contains no real brand, retailer or campaign
data, and the metrics (CTR, ROAS, impressions) are random numbers with no real-world meaning.
It exists only so ingestion and retrieval code has something to run against.

Regenerate with:

```bash
python3 data/generate_campaigns.py 30 > data/sample_campaigns.csv
```
