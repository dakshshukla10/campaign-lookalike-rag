#!/usr/bin/env python3
"""Write deterministic synthetic campaigns and rule-labelled evaluation briefs."""

import argparse
import csv
import json
import random
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

CATEGORIES = ["grocery", "beauty", "home", "electronics", "apparel", "toys", "pet", "health"]
AUDIENCES = ["families", "young-adults", "students", "seniors", "frequent-shoppers", "new-customers"]
CHANNELS = ["display", "sponsored-search", "social", "email", "video", "in-store-screens"]
OBJECTIVES = ["awareness", "consideration", "conversion", "retention"]
SEASONS = ["Spring", "Summer", "Back-to-School", "Holiday", "Winter", "Weekend"]

FIELDS = [
    "campaign_id", "name", "category", "audience", "channel", "objective",
    "budget_usd", "duration_days", "impressions", "ctr", "roas", "description",
]
FILTER_FIELDS = ("category", "audience", "channel", "objective")
DEFAULT_ROWS = 120
BRIEF_COUNT = 40


@dataclass(frozen=True)
class Theme:
    name: str
    anchors: tuple[str, str, str]
    campaigns: tuple[str, str, str]
    # Brief phrasing order: hard, medium, medium, easy.
    briefs: tuple[str, str, str, str]


THEMES = {
    "grocery": (
        Theme("pantry staples", ("pantry", "rice", "lentils"), (
            "Fill the pantry with rice and lentils for meals throughout the week.",
            "Show how rice and lentils keep a pantry ready for simple cooking.",
            "Put pantry basics first with rice and lentils in larger packs.",
        ), (
            "Plan a pantry refill around dry ingredients that last through several meals.",
            "Make rice a useful starting point for filling an empty pantry.",
            "Help cooks stock a pantry with lentils for affordable meals.",
            "Promote rice and lentils as cupboard basics for everyday cooking.",
        )),
        Theme("produce box", ("harvest", "greens", "crate"), (
            "Pack a harvest crate with fresh greens for the weekly shop.",
            "Bring greens from the harvest into a crate of seasonal produce.",
            "Open a harvest crate of greens picked for home cooking.",
        ), (
            "Encourage households to order a crate of seasonal vegetables each week.",
            "Offer greens in a crate that changes with the growing season.",
            "Tell a harvest story around a weekly crate of vegetables.",
            "Advertise harvest greens in a box ready for the weekly meal plan.",
        )),
        Theme("quick dinners", ("supper", "skillet", "noodles"), (
            "Cook noodles in one skillet for a quick supper after a busy day.",
            "Make supper easier with skillet noodles and a short ingredient list.",
            "Show noodles going from skillet to supper in a few simple steps.",
        ), (
            "Help busy cooks put supper on the table with very little washing up.",
            "Offer noodles as a quick supper for a busy evening.",
            "Show how a skillet can shorten the supper routine after a long day.",
            "Sell noodles for a skillet meal that takes little time to prepare.",
        )),
    ),
    "beauty": (
        Theme("skincare launch", ("serum", "hydration", "cleanser"), (
            "Introduce a serum and cleanser with hydration at the center of the routine.",
            "Pair a gentle cleanser with a serum for daily hydration.",
            "Explain how a cleanser and serum fit into a hydration routine.",
        ), (
            "Introduce a serum with simple instructions for a daily facial routine.",
            "Present a cleanser and serum as two steps in daily face care.",
            "Build interest in a serum made for a hydration routine.",
            "Launch a cleanser for daily face care with hydration as the main message.",
        )),
        Theme("holiday gift sets", ("ribbon", "fragrance", "keepsake"), (
            "Wrap a fragrance keepsake in ribbon for a seasonal gift.",
            "Make a ribbon package around a fragrance keepsake ready for giving.",
            "Present a fragrance keepsake with ribbon and a handwritten gift card.",
        ), (
            "Help shoppers choose a fragrance present for someone close to them.",
            "Suggest a ribbon package with a fragrance for seasonal giving.",
            "Show a keepsake filled with fragrance as a thoughtful present.",
            "Promote a ribbon wrapped keepsake as a ready to give beauty present.",
        )),
        Theme("sample-box trial", ("sachet", "swatch", "miniature"), (
            "Try a miniature beauty kit with a sachet and a color swatch.",
            "Put a sachet beside a swatch in a miniature discovery box.",
            "Let a miniature kit introduce each sachet and matching swatch.",
        ), (
            "Invite hesitant shoppers to test a sachet before buying the full size.",
            "Use a miniature discovery pack with a sachet to encourage a first try.",
            "Offer a swatch alongside a miniature beauty selection for testing at home.",
            "Offer a sachet and swatch in a small trial pack before a larger purchase.",
        )),
    ),
    "home": (
        Theme("room refresh", ("cushions", "curtains", "rug"), (
            "Refresh a sitting room with cushions, curtains and a soft rug.",
            "Match curtains to cushions and a rug for a small room update.",
            "Show how a rug, curtains and cushions change a familiar room.",
        ), (
            "Encourage a sitting room update by changing the cushions on the sofa.",
            "Coordinate curtains with a rug for a small change at home.",
            "Suggest cushions and curtains to give a familiar room a fresh look.",
            "Sell cushions with a matching rug for a coordinated living space.",
        )),
        Theme("kitchen storage", ("jars", "stackable", "cupboard"), (
            "Arrange stackable jars to make more space inside a cupboard.",
            "Keep a cupboard tidy with stackable jars for dry ingredients.",
            "Move loose packets into stackable jars on a cupboard shelf.",
        ), (
            "Help home cooks clear a crowded cupboard with matching containers.",
            "Offer stackable containers to make a cupboard easier to organize.",
            "Promote jars that free up cupboard space and keep ingredients visible.",
            "Show stackable jars holding dry ingredients on tidy kitchen shelves.",
        )),
        Theme("bedding renewal", ("duvet", "pillow", "linen"), (
            "Dress the bed with a linen duvet cover and matching pillow cases.",
            "Pair a pillow cover with a linen duvet for a bedroom update.",
            "Replace tired bedding with a duvet and pillow set in linen.",
        ), (
            "Encourage a bedroom update with a new duvet cover in soft fabric.",
            "Show a duvet and pillow set that gives the bed a coordinated look.",
            "Introduce a linen duvet cover for a simple bedroom change.",
            "Sell linen pillow covers as part of a coordinated bedding refresh.",
        )),
    ),
    "electronics": (
        Theme("wireless audio", ("earbuds", "bass", "wireless"), (
            "Demonstrate wireless earbuds with bass for everyday listening.",
            "Put wireless earbuds through a bass test during a daily commute.",
            "Explain the bass controls on wireless earbuds for music listeners.",
        ), (
            "Help commuters choose earbuds for listening to music on the way home.",
            "Compare wireless earbuds for comfortable listening on daily journeys.",
            "Show earbuds playing music with adjustable bass for personal listening.",
            "Promote wireless listening with bass controls that are easy to adjust.",
        )),
        Theme("study devices", ("tablet", "stylus", "notes"), (
            "Use a stylus and tablet to keep class notes in one place.",
            "Show a tablet turning handwritten notes into pages with a stylus.",
            "Make notes on a tablet with a stylus during a lesson.",
        ), (
            "Introduce a tablet for organizing coursework and reading between classes.",
            "Help learners take notes with a stylus during lessons.",
            "Show a tablet holding notes that can be reviewed before a test.",
            "Promote a tablet and stylus for handwritten coursework and revision.",
        )),
        Theme("connected rooms", ("sensor", "socket", "timer"), (
            "Link a sensor to a socket and timer for simple room controls.",
            "Demonstrate a socket timer that responds to a room sensor.",
            "Use a sensor with a timer to switch a socket on at home.",
        ), (
            "Introduce a socket that lets households control an appliance from another room.",
            "Show a sensor and socket working together to control a lamp.",
            "Use a timer and socket to schedule lights at home.",
            "Promote a sensor with a timer for automatic lighting in occupied rooms.",
        )),
    ),
    "apparel": (
        Theme("wet weather", ("raincoat", "waterproof", "hood"), (
            "Show a waterproof raincoat with a hood for wet days outside.",
            "Pull up the hood of a waterproof raincoat during a rainy walk.",
            "Explain the hood fit on a waterproof raincoat for changing weather.",
        ), (
            "Help shoppers choose a raincoat for walking outside in wet weather.",
            "Offer a waterproof raincoat for everyday trips in the rain.",
            "Show a raincoat with a hood that adjusts for a closer fit.",
            "Promote waterproof outerwear with a hood for rainy day travel.",
        )),
        Theme("exercise clothing", ("leggings", "stretch", "workout"), (
            "Try stretch leggings through a short workout at home.",
            "Show how leggings stretch during a workout with bending and lunges.",
            "Choose leggings with stretch for a comfortable workout routine.",
        ), (
            "Invite shoppers to try leggings for a short exercise session.",
            "Demonstrate leggings during a workout with plenty of movement.",
            "Promote stretch clothing that stays comfortable throughout a workout.",
            "Sell stretch leggings for bending and moving during exercise.",
        )),
        Theme("everyday layers", ("cotton", "cardigan", "layering"), (
            "Use a cotton cardigan for layering on cooler mornings.",
            "Show layering ideas built around a cotton cardigan.",
            "Pair a cotton cardigan with everyday clothes for easy layering.",
        ), (
            "Introduce a cardigan that is easy to carry when the weather changes.",
            "Suggest a cotton cardigan for cool mornings and warmer afternoons.",
            "Show layering choices with a cardigan over a simple top.",
            "Promote cotton clothing for layering through changes in temperature.",
        )),
    ),
    "toys": (
        Theme("building sets", ("bricks", "tower", "construction"), (
            "Stack bricks into a tower during a hands-on construction activity.",
            "Show a construction set of bricks becoming a tall tower.",
            "Turn loose bricks into a tower with simple construction steps.",
        ), (
            "Encourage children to build with bricks and try their own designs.",
            "Show bricks becoming a tower during creative play.",
            "Introduce construction play with bricks that can be used again.",
            "Sell a construction set for making a tower and rebuilding it differently.",
        )),
        Theme("pretend kitchen", ("apron", "pretend", "utensils"), (
            "Put on an apron for pretend cooking with toy utensils.",
            "Arrange utensils and an apron for a pretend kitchen game.",
            "Use pretend utensils and an apron to act out making a meal.",
        ), (
            "Invite children to put on an apron and act out serving lunch.",
            "Introduce pretend cooking with utensils sized for small hands.",
            "Show an apron as part of a pretend cafe game at home.",
            "Promote toy utensils and an apron for imaginative kitchen play.",
        )),
        Theme("puzzle time", ("jigsaw", "pieces", "puzzle"), (
            "Fit jigsaw pieces together to finish a picture puzzle.",
            "Sort the pieces of a jigsaw before starting the puzzle.",
            "Share a puzzle session while joining jigsaw pieces around a table.",
        ), (
            "Encourage a quiet afternoon activity with a colorful jigsaw.",
            "Offer a jigsaw puzzle for a shared activity around the table.",
            "Suggest a puzzle with pieces that can be sorted by color.",
            "Promote a jigsaw with large pieces for relaxed picture building.",
        )),
    ),
    "pet": (
        Theme("cat feeding", ("kibble", "feline", "bowl"), (
            "Fill a bowl with kibble for a regular feline feeding routine.",
            "Show feline mealtimes with kibble measured into a bowl.",
            "Measure kibble for the bowl as part of daily feline care.",
        ), (
            "Help cat owners choose kibble for their pets' daily meals.",
            "Show kibble served in a bowl at regular cat mealtimes.",
            "Introduce feline feeding tips using a measured portion of kibble.",
            "Promote feline food with a bowl portion guide for everyday feeding.",
        )),
        Theme("dog walks", ("leash", "canine", "harness"), (
            "Fit a canine harness and attach a leash before a daily walk.",
            "Show a leash clipped to a canine harness for outdoor exercise.",
            "Check the harness fit before taking a canine companion on a leash.",
        ), (
            "Help dog owners choose a leash for regular walks in the neighborhood.",
            "Demonstrate a harness and leash for comfortable dog walking.",
            "Introduce a canine walking routine with a secure harness fit.",
            "Promote a canine leash for supervised outdoor exercise with a pet.",
        )),
        Theme("coat grooming", ("brush", "fur", "grooming"), (
            "Use a brush to remove loose fur during a short grooming session.",
            "Make grooming easier with a brush suited to thick fur.",
            "Show a brush moving gently through fur as part of regular grooming.",
        ), (
            "Encourage pet owners to remove loose fur during regular coat care.",
            "Introduce a brush for keeping fur tidy between baths.",
            "Show grooming steps that remove loose fur without a long session.",
            "Promote a grooming brush for gentle coat care at home.",
        )),
    ),
    "health": (
        Theme("oral care", ("toothbrush", "enamel", "floss"), (
            "Pair a toothbrush with floss for an enamel care routine.",
            "Explain daily enamel care using a toothbrush and floss.",
            "Place floss beside a toothbrush as a reminder of enamel care.",
        ), (
            "Encourage a consistent bathroom routine with a fresh toothbrush.",
            "Show a toothbrush and floss together for daily mouth care.",
            "Introduce floss as a step in an enamel care routine.",
            "Promote a toothbrush with simple enamel care instructions for daily use.",
        )),
        Theme("sun protection", ("sunscreen", "ultraviolet", "shade"), (
            "Use sunscreen and shade when planning for ultraviolet exposure outdoors.",
            "Explain ultraviolet protection with sunscreen and time in the shade.",
            "Pack sunscreen and find shade before spending time in ultraviolet light.",
        ), (
            "Remind shoppers to pack sunscreen for a day spent outside.",
            "Show sunscreen and shade as parts of an outdoor care routine.",
            "Explain sunscreen use before planned ultraviolet exposure.",
            "Encourage shade breaks when planning around ultraviolet exposure outdoors.",
        )),
        Theme("sleep habits", ("bedtime", "rest", "winddown"), (
            "Plan a bedtime winddown to make room for regular rest.",
            "Keep a winddown routine before bedtime and set aside time for rest.",
            "Introduce a bedtime reminder for a winddown period before rest.",
        ), (
            "Help people keep a regular bedtime by planning quieter evenings.",
            "Encourage a bedtime winddown with less noise and dimmer lighting.",
            "Suggest a winddown period that leaves enough time for rest.",
            "Promote bedtime habits that leave a regular period for rest each night.",
        )),
    ),
}

AUDIENCE_COPY = {
    "families": "households shopping for the whole family",
    "young-adults": "young adults managing their own purchases",
    "students": "students fitting purchases around their studies",
    "seniors": "older shoppers planning everyday purchases",
    "frequent-shoppers": "regular shoppers returning for familiar items",
    "new-customers": "first-time shoppers exploring the range",
}
CHANNEL_COPY = {
    "display": "display banners",
    "sponsored-search": "paid search listings",
    "social": "social feed posts",
    "email": "email messages",
    "video": "short video spots",
    "in-store-screens": "screens inside the shop",
}
OBJECTIVE_COPY = {
    "awareness": "introduce the range",
    "consideration": "help people compare their options",
    "conversion": "encourage a purchase",
    "retention": "encourage another visit",
}


def words(text):
    return set(re.findall(r"[a-z]+(?:-[a-z]+)*", text.lower()))


def generate_campaigns(seed):
    rng = random.Random(seed)
    groups = [(category, theme) for category in CATEGORIES for theme in THEMES[category]]
    rng.shuffle(groups)
    settings = {}
    for category, theme in groups:
        audiences = rng.sample(AUDIENCES, 2)
        channels = rng.sample(CHANNELS, 2)
        objectives = rng.sample(OBJECTIVES, 2)
        settings[(category, theme.name)] = list(zip(audiences, channels, objectives))

    campaigns = []
    assignments = {}
    for i in range(DEFAULT_ROWS):
        category, theme = groups[i % len(groups)]
        member = i // len(groups)
        audience, channel, objective = settings[(category, theme.name)][member // 3]
        budget = rng.randrange(5, 200) * 1000
        # Cycle through the pool so every theme uses all three campaign phrasings.
        opening = theme.campaigns[(member + seed) % len(theme.campaigns)]
        description = (
            f"{opening} Reach {AUDIENCE_COPY[audience]} through "
            f"{CHANNEL_COPY[channel]} to {OBJECTIVE_COPY[objective]}."
        )
        campaign_id = f"C-{i + 1:04d}"
        campaigns.append({
            "campaign_id": campaign_id,
            "name": f"{rng.choice(SEASONS)} {theme.name} {objective} push",
            "category": category,
            "audience": audience,
            "channel": channel,
            "objective": objective,
            "budget_usd": budget,
            "duration_days": rng.choice([7, 14, 21, 28, 42]),
            "impressions": int(budget * rng.uniform(15, 60)),
            "ctr": round(rng.uniform(0.002, 0.04), 4),
            "roas": round(rng.uniform(0.6, 6.0), 2),
            "description": description,
        })
        assignments[campaign_id] = (category, theme.name)
    return campaigns, assignments, groups


def satisfies_filters(campaign, brief):
    # budgetUsd is the planned spend, not a filter, so it does not change the labels.
    return all(brief[field] is None or campaign[field] == brief[field] for field in FILTER_FIELDS)


def generate_briefs(campaigns, assignments, groups, seed):
    rng = random.Random(seed + 1)
    pairs = groups.copy()
    rng.shuffle(pairs)
    difficulties = ["hard"] * 10 + ["medium"] * 20 + ["easy"] * 10
    rng.shuffle(difficulties)
    briefs = []
    for i, difficulty in enumerate(difficulties):
        category, theme = pairs[i % len(pairs)]
        members = [c for c in campaigns if assignments[c["campaign_id"]] == (category, theme.name)]
        phrasing = {"hard": 0, "medium": rng.choice((1, 2)), "easy": 3}[difficulty]
        brief = {
            "id": f"B-{i + 1:03d}",
            "description": theme.briefs[phrasing],
            "category": None,
            "audience": None,
            "channel": None,
            "objective": None,
            "budgetUsd": None,
        }
        if i % 2 == 0:
            reference = rng.choice(members)
            filter_sets = (
                ("category",),
                ("category", "channel"),
                ("category", "objective"),
                ("category", "audience", "channel"),
                FILTER_FIELDS,
            )
            for field in filter_sets[(i // 2) % len(filter_sets)]:
                brief[field] = reference[field]
            if i // 2 < 13:
                brief["budgetUsd"] = reference["budget_usd"]
        brief["expected"] = sorted(
            c["campaign_id"] for c in members if satisfies_filters(c, brief)
        )
        briefs.append(brief)
    return briefs, difficulties


def check(campaigns, briefs, assignments, groups, difficulties):
    by_id = {c["campaign_id"]: c for c in campaigns}
    assert len(campaigns) == len(by_id) == DEFAULT_ROWS, "Expected 120 unique campaign ids"
    assert len(briefs) == len({b["id"] for b in briefs}) == BRIEF_COUNT
    assert Counter(assignments.values()) == {key: 5 for key in assignments.values()}
    assert Counter(difficulties) == {"hard": 10, "medium": 20, "easy": 10}
    assert sum(any(b[f] is not None for f in (*FILTER_FIELDS, "budgetUsd")) for b in briefs) == 20
    assert sum(b["budgetUsd"] is not None for b in briefs) == 13
    assert set(THEMES) == set(CATEGORIES) and all(len(pool) == 3 for pool in THEMES.values())
    for category, theme in groups:
        anchors = set(theme.anchors)
        assert len(theme.campaigns) == 3 and len(set(theme.campaigns)) == 3
        assert len(theme.briefs) == 4 and len(set(theme.briefs)) == 4
        assert all(len(words(text) & anchors) == 3 for text in theme.campaigns)
        assert [len(words(text) & anchors) for text in theme.briefs] == [1, 2, 2, 2]
        members = [c for c in campaigns if assignments[c["campaign_id"]] == (category, theme.name)]
        assert all(len(words(c["description"]) & anchors) == 3 for c in members)
    for brief, difficulty in zip(briefs, difficulties):
        assert brief["expected"], f"{brief['id']} has no expected campaigns"
        assert brief["expected"] == sorted(set(brief["expected"]))
        assert all(cid in by_id for cid in brief["expected"])
        assert all(satisfies_filters(by_id[cid], brief) for cid in brief["expected"])
        group = assignments[brief["expected"][0]]
        assert all(assignments[cid] == group for cid in brief["expected"])
        assert brief["expected"] == sorted(
            c["campaign_id"] for c in campaigns
            if assignments[c["campaign_id"]] == group and satisfies_filters(c, brief)
        )
        theme = next(t for t in THEMES[group[0]] if t.name == group[1])
        assert len(words(brief["description"]) & set(theme.anchors)) == (1 if difficulty == "hard" else 2)
        assert len(brief["expected"]) >= 2
    for record in [*campaigns, *briefs]:
        text = record["description"]
        assert text.isascii() and not re.search(r"C-\d{4}", text)
    for campaign in campaigns:
        assert campaign["description"].count(".") == 2
        assert campaign["category"] in CATEGORIES
        assert campaign["audience"] in AUDIENCES
        assert campaign["channel"] in CHANNELS
        assert campaign["objective"] in OBJECTIVES


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--check", action="store_true", help="Check existing files without rewriting them")
    args = parser.parse_args()
    campaigns, assignments, groups = generate_campaigns(args.seed)
    briefs, difficulties = generate_briefs(campaigns, assignments, groups, args.seed)
    check(campaigns, briefs, assignments, groups, difficulties)
    csv_path = args.output_dir / "sample_campaigns.csv"
    json_path = args.output_dir / "eval_briefs.json"
    if args.check:
        with csv_path.open(encoding="utf-8", newline="") as source:
            reader = csv.DictReader(source)
            assert reader.fieldnames == FIELDS, "Unexpected CSV columns"
            actual = list(reader)
        assert actual == [{k: str(v) for k, v in c.items()} for c in campaigns], "Campaign file differs from generator"
        with json_path.open(encoding="utf-8") as source:
            assert json.load(source) == {"briefs": briefs}, "Brief file differs from generator"
    else:
        args.output_dir.mkdir(parents=True, exist_ok=True)
        with csv_path.open("w", encoding="utf-8", newline="") as destination:
            writer = csv.DictWriter(destination, fieldnames=FIELDS, lineterminator="\n")
            writer.writeheader()
            writer.writerows(campaigns)
        with json_path.open("w", encoding="utf-8", newline="\n") as destination:
            json.dump({"briefs": briefs}, destination, indent=2, ensure_ascii=True)
            destination.write("\n")
    print("Check passed: 120 unique campaigns, 40 briefs, all expected ids exist and satisfy their filters.")
    print("Labels: 24 themes, 10 hard / 20 medium / 10 easy, 20 filtered briefs, 13 budgets, at least 2 expected ids per brief.")


if __name__ == "__main__":
    main()
