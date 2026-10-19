#!/usr/bin/env python3
"""Generate a small SYNTHETIC campaigns CSV for development.

Every value is randomly generated with a fixed seed. It contains no real company, brand or
campaign data, and the numbers carry no real-world meaning.

Usage: python3 data/generate_campaigns.py [rows] > data/sample_campaigns.csv
"""
import csv
import random
import sys

CATEGORIES = ["grocery", "beauty", "home", "electronics", "apparel", "toys", "pet", "health"]
AUDIENCES = ["families", "young-adults", "students", "seniors", "frequent-shoppers", "new-customers"]
CHANNELS = ["display", "sponsored-search", "social", "email", "video", "in-store-screens"]
OBJECTIVES = ["awareness", "consideration", "conversion", "retention"]
SEASONS = ["Spring", "Summer", "Back-to-School", "Holiday", "Winter", "Weekend"]

FIELDS = [
    "campaign_id", "name", "category", "audience", "channel", "objective",
    "budget_usd", "duration_days", "impressions", "ctr", "roas",
]


def main() -> None:
    rows = int(sys.argv[1]) if len(sys.argv) > 1 else 30
    rng = random.Random(42)
    writer = csv.DictWriter(sys.stdout, fieldnames=FIELDS, lineterminator="\n")
    writer.writeheader()
    for i in range(1, rows + 1):
        category = rng.choice(CATEGORIES)
        objective = rng.choice(OBJECTIVES)
        budget = rng.randrange(5, 200) * 1000
        impressions = int(budget * rng.uniform(15, 60))
        writer.writerow({
            "campaign_id": f"C-{i:04d}",
            "name": f"{rng.choice(SEASONS)} {category} {objective} push",
            "category": category,
            "audience": rng.choice(AUDIENCES),
            "channel": rng.choice(CHANNELS),
            "objective": objective,
            "budget_usd": budget,
            "duration_days": rng.choice([7, 14, 21, 28, 42]),
            "impressions": impressions,
            "ctr": round(rng.uniform(0.002, 0.04), 4),
            "roas": round(rng.uniform(0.6, 6.0), 2),
        })


if __name__ == "__main__":
    main()
