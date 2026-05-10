# Sample Dataset

## Source

`reviews_seed.csv` is a curated subset of:

> Kumar, A. (2024). *Synthetic E-commerce Product Reviews Dataset.*
> Kaggle.
> https://www.kaggle.com/datasets/aryan208/synthetic-e-commerce-product-reviews-dataset
> Licence: **CC0 (Public Domain)**.

The original dataset contains 4 million synthetic reviews
across 8 product categories (Electronics, Home & Kitchen, Fashion,
Beauty, Toys & Games, Books, Health & Personal Care, Sports & Outdoors).
Each row provides `product_id`, `product_title`, `category`,
`review_text`, `rating` (1–5), and `sentiment` (Positive/Neutral/Negative).

The dataset is synthetic by design and its synthetic nature is acceptable for this thesis because
the focus is on the **architectural integration** of ML components in
web applications, not on model behavior or input data quality.

## Roles of the dataset in this study

The dataset serves two distinct purposes that are deliberately kept
separate:
1. **Seed data (`reviews_seed.csv`)** is a small fixed 
set of products and reviews loaded into the database at startup so
the UI is populated. These reviews represent the state of the application *before* the experiments — populated by
an unspecified prior classifier and treated as ground truth for
display only.
2. **Experimental input pool (`reviews_experimental.csv`)** is a much larger corpus, *not loaded into the
database*, read directly by the experiment harness and submitted
via the REST API to the variant under measurement. This way the
variants only ever classify newly submitted reviews, never the
seeded ones.

The two corpora are **disjoint**: no review appears in both. This means
the model under measurement never encounters the same input twice, and
the seed data cannot inadvertently contaminate the experimental
results.

## Curation

The full 4M rows are **not** committed to this repository. The bundled
`reviews_seed.csv` is a 160-row subset produced as follows:

1. **Filtering for consistency between the rating and the sentiment rating.** 
Rows in which the rating contradicted the sentiment rating (e.g., a 5-star review 
labeled as `Negative`) were discarded. The Analysis of the dataset revealed this to 
be necessary, as the original sentiment labels were randomly assigned to the 
template-based review texts, resulting in many rows with inconsistent content.
2. **Sampling of 5 reviews per (product_id, product_title, category) tuple.** 
For each (product_id, product_title, category) tuple, exactly 3 Positive, 1 Neutral,
and 1 Negative review were taken.
3. **Mapping of sentiment labels** from `Positive`/`Neutral`/`Negative` to the enums 
`POSITIVE`/`NEUTRAL`/`NEGATIVE` used in the application.

This subset is bundled in the JAR so that a fresh clone of the project
can boot a populated database without external dependencies.

## Regeneration of the seed subset

To regenerate `reviews_seed.csv` from the original 4M-row
dataset:

1. Download the original from the URL above.
2. Save it as `ecommerce_product_reviews_dataset.csv`.
3. Run the script below (Python 3, no extra dependencies):

_AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author._
```python
import csv

SENTIMENT_MAP = {'Positive': 'POSITIVE', 'Neutral': 'NEUTRAL', 'Negative': 'NEGATIVE'}
TARGET_PER_PRODUCT = {'Positive': 3, 'Neutral': 1, 'Negative': 1}

def is_consistent(rating, sentiment):
    rating = int(rating)
    if sentiment == 'Positive' and rating >= 4: return True
    if sentiment == 'Neutral' and rating == 3: return True
    if sentiment == 'Negative' and rating <= 2: return True
    return False

products = {}

with open('ecommerce_product_reviews_dataset.csv', 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        if not is_consistent(row['rating'], row['sentiment']):
            continue
        key = (row['product_id'], row['product_title'], row['category'])
        bucket = products.setdefault(key,
                {'Positive': [], 'Neutral': [], 'Negative': []})
        sent_bucket = bucket[row['sentiment']]
        if len(sent_bucket) < TARGET_PER_PRODUCT[row['sentiment']]:
            sent_bucket.append((row['review_text'], row['rating'], row['sentiment']))
        if len(products) >= 32:
            done = all(
                len(p['Positive']) >= 3 and len(p['Neutral']) >= 1
                and len(p['Negative']) >= 1
                for p in products.values()
            )
            if done:
                break

with open('reviews_seed.csv', 'w', encoding='utf-8', newline='') as f:
    w = csv.writer(f)
    w.writerow(['product_id', 'product_title', 'category',
                'review_text', 'rating', 'sentiment'])
    for (pid, title, cat), buckets in sorted(products.items()):
        for sent_label in ['Positive', 'Neutral', 'Negative']:
            for text, rating, sent in buckets[sent_label]:
                w.writerow([pid, title, cat, text, rating, SENTIMENT_MAP[sent]])
```

## Loader behaviour

`SampleDataLoader` is a
Spring Boot `CommandLineRunner` that:

- Runs at application startup
- Checks whether the `products` table is already populated
- If empty: parses the bundled CSV, creates `Product` entities (one per
  unique product ID) and `Review` entities (one per CSV row), commits
  in a single transaction.
- If non-empty: logs and exits, leaving existing data untouched

Product fields not in the dataset are filled with explicit
placeholders rather than fabricated values:

- `description` — fixed placeholder string flagging the field as
  out-of-scope for the study.
- `price` — fixed sentinel value (€9.99) applied uniformly.
- `imageUrl` — `null`.
