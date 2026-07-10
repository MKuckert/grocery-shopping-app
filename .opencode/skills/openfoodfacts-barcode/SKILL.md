---
name: openfoodfacts-barcode
description: Use when integrating Open Food Facts API for product lookups. Covers v3.6 barcode resolution, search endpoints, authentication-free read operations, rate limiting (15 req/min product reads, 10 req/min search), User-Agent requirements, and production-grade patterns. Includes staging/testing, caching strategies, and data quality guidance.
metadata:
  created: 2026-06-21
  updated: 2026-06-21
  libraries: Open Food Facts API v3.6
  tags: api, barcode, openfoodfacts
  verified: false
---

# Open Food Facts API Integration

## Overview

Open Food Facts is a free, collaborative database of food products worldwide. Its API provides unauthenticated read access to product data indexed by barcode (UPC/EAN/GTIN codes) and supports full-text product search. The API is **read-free** (no API key required) but mandates a custom User-Agent header and enforces strict per-IP rate limits.

This skill covers production-grade integration patterns for:

- **Barcode lookup** (primary use case): resolve UPC/EAN codes to product metadata
- **Search queries** (secondary): find products by name, brand, or category
- **Data validation & caching** strategies to respect rate limits
- **Staging/testing** workflows for safe development

**Current Recommended Version:** `v3.6` (stable, latest features; v2 legacy support available)

---

## Architecture & Environment Tiers

### Production vs. Staging

**Production:** `https://world.openfoodfacts.org`

- Use for live product lookups and end-user-facing features.
- All reads are unauthenticated.
- Public API; contribute to real database.

**Staging (Testing):** `https://world.openfoodfacts.net`

- Requires HTTP Basic Auth (Username: `off`, Password: `off`) to prevent search engine indexing.
- Safe for development and testing without polluting production data.
- **Recommended:** Use this environment during integration development.

### No API Key Required for Reads

Unlike most commercial APIs, Open Food Facts **does not require an API key or token for read operations**. This is intentional and by design:

- Unauthenticated reads reduce friction and encourage integration.
- However, this places heavier responsibility on callers to **respect rate limits and use descriptive User-Agent headers**.

### Write Operations (Advanced)

If your application needs to edit or upload product data:

- **Authentication Required:** Use the Login API endpoint to obtain a session cookie.
- **Not covered in this skill:** Focus is on read-only operations.

---

## Critical Requirements: User-Agent Header

The Open Food Facts team **mandates a custom User-Agent header** on every HTTP request. This serves two purposes:

1. **Identification:** Allows the Open Food Facts ops team to contact you if your traffic is anomalous.
2. **IP Ban Prevention:** Generic or library-default User-Agent strings (e.g., `python-requests/2.31.0`) trigger bot-detection filters and increase the risk of IP-based rate limiting.

### Correct Format

```
User-Agent: AppName/Version (ContactEmail)
```

### Examples

✓ **Good:**

```
User-Agent: MyGroceryApp/1.0 (dev@example.com)
User-Agent: GroceryApp/2.1.4 (support@groceryco.com)
```

✗ **Bad:**

```
User-Agent: python-requests/2.31.0
User-Agent: Mozilla/5.0 (generic client)
User-Agent: MyApp (missing version/email)
```

### Implementation Checklist

When building client code:

1. **Define a constant** at application startup that includes a real app name and contact email.
2. **Inject this header into every HTTP request** made to the API (v2 and v3).
3. **Do not use** library defaults or generic User-Agent strings.
4. **Document the header requirement** in your client's setup instructions.

---

## Endpoints Overview

| Use Case           | Endpoint                               | Rate Limit    | Notes                                               |
| ------------------ | -------------------------------------- | ------------- | --------------------------------------------------- |
| **Barcode Lookup** | `GET /api/v3.6/product/{barcode}.json` | 15/min        | **Primary**; recommended for all new integrations   |
| Search             | `GET /api/v2/search`                   | 10/min        | Full-text product search; deprecated for new builds |
| Image Access       | `GET /api/v3.3/product/{code}/images`  | 15/min        | Structured image endpoint                           |
| Write/Edit         | `POST /api/v3/product/{barcode}`       | Auth required | Requires login session or user credentials          |

---

## Barcode Lookup (Primary Endpoint)

### Request

```
GET https://world.openfoodfacts.org/api/v3.6/product/{barcode}.json
Headers:
  User-Agent: AppName/Version (email@example.com)
```

**Example cURL:**

```bash
curl -X GET "https://world.openfoodfacts.org/api/v3.6/product/3274080005003.json" \
     -H "User-Agent: MyGroceryApp/1.0 (dev@example.com)"
```

### Response Schema

Success (200 OK, status="ok"):

```json
{
  "status": "ok",
  "code": "3560007301171",
  "product": {
    "product_name": "Nutella",
    "generic_name": "Hazelnut chocolate spread",
    "brands": "Ferrero",
    "categories": "Spreads, Chocolates",
    "ingredients_text": "Sugar, vegetable oil, hazelnuts 13%, ...",
    "nutriscore_grade": "e",
    "nova_group": 4,
    "allergens": "Hazelnuts, Milk",
    "labels": ["palm-oil-free"],
    "ecoscore_grade": "d",
    "ecoscore_score": 45,
    "images": {
      "front": { "url": "https://..." },
      "ingredients": { "url": "https://..." },
      "nutrition_facts": { "url": "https://..." }
    },
    "nutriments": {
      "energy_kcal": 530,
      "fat_g": 31,
      "saturated_fat_g": 10.6,
      "carbohydrates_g": 56,
      "sugars_g": 56,
      "proteins_g": 8.7,
      "salt_g": 0.1
    }
  }
}
```

Not Found (200 OK, status="not found"):

```json
{
  "status": "not found"
}
```

### Response Status Codes

| Status Code                 | Meaning                                  | Action                                 |
| --------------------------- | ---------------------------------------- | -------------------------------------- |
| 200 OK (status="ok")        | Product found.                           | Extract and return product data.       |
| 200 OK (status="not found") | Product not indexed.                     | Return null; do not error.             |
| 404 Not Found               | Malformed request.                       | Return null.                           |
| 503 Service Unavailable     | Rate limit or infrastructure overloaded. | Backoff and retry (exponential delay). |

---

## Search Endpoint (v2)

Use for full-text product discovery (secondary use case).

### Request

```
GET https://world.openfoodfacts.org/api/v2/search
Query Parameters:
  q (required): Search term (product name, brand, category)
  page (optional): Page number (default: 1)
  page_size (optional): Results per page (default: 10, max: 100)
```

**Example cURL:**

```bash
curl -X GET "https://world.openfoodfacts.org/api/v2/search?q=nutella&page=1&page_size=5" \
     -H "User-Agent: MyGroceryApp/1.0 (dev@example.com)"
```

### Rate Limiting Note

Search is rate-limited to **10 req/min**. This is **not suitable for autocomplete or search-as-you-type features**. For fast search UX:

- Use client-side caching of popular products
- Pre-load a curated product list
- Consider the specialized **Search-a-licious** service at `search.openfoodfacts.org` (more powerful, separate API)

---

## Critical Implementation Details

### 1. Barcode Format Handling

The API accepts multiple formats:

| Format                   | Example         | Notes                              |
| ------------------------ | --------------- | ---------------------------------- |
| EAN-13                   | `3560007301171` | Most common in Europe              |
| UPC-A                    | `012345678905`  | US/Canada (12 digits)              |
| EAN-8                    | `12345670`      | Shorter codes                      |
| Open Food Facts Internal | `200XXXXXXX`    | Assigned for products without GTIN |

**Best Practice:** Strip whitespace and hyphens; pass clean numeric string.

### 2. Rate Limiting Behavior

- **Read operations (barcode lookup):** 15 requests/minute per IP.
- **Search queries:** 10 requests/minute per IP.
- **Mobile apps:** Limits applied per authenticated user (not per IP).
- **Violation response:** `HTTP 503 Service Unavailable` with HTML body (not JSON).

**Mitigation Strategy:**

- Implement **local caching** (SQLite, Redis, in-memory) for product data.
- Add **exponential backoff** (retry with 2s, 4s, 8s delays) on 503 responses.
- For bulk analysis, **do not use the API**—download daily CSV/JSONL exports from `world.openfoodfacts.org/data`.
- Respect the 15 req/min limit; use caching for repeat queries.

### 3. JSON Response Gotchas

| Issue                       | Symptom                                 | Resolution                                                   |
| --------------------------- | --------------------------------------- | ------------------------------------------------------------ |
| **Missing Product**         | `"status": "not found"`                 | Return null; do not error.                                   |
| **Data Quality**            | Incomplete fields (null, empty strings) | Validate all fields before display.                          |
| **Allergen Field Variance** | Sometimes string, sometimes array       | Normalize to array for consistency.                          |
| **Image URLs Stale**        | 404 on image URLs from response         | Fall back to placeholder; don't assume all URLs are current. |

### 4. Staging Environment (Development)

Use `https://world.openfoodfacts.net` for testing:

```
GET https://world.openfoodfacts.net/api/v3.6/product/{barcode}.json
Headers:
  Authorization: Basic <base64(off:off)>
  User-Agent: MyApp/1.0 (dev@example.com)
```

---

## Common Response Fields

When processing product responses, expect these fields:

### Identification & Metadata

- `code`: The barcode (UPC/EAN/GTIN).
- `product_name`: Official product name.
- `generic_name`: Short descriptive text.
- `brands`: Manufacturer/brand.
- `categories`: Hierarchical category classification.

### Media

- `images.front.url`: Product front image (best for UI display).
- `images.ingredients.url`: Ingredient list photo.
- `images.nutrition_facts.url`: Scanned nutrition label (if available).

### Nutritional Data

- `nutriments`: Nested object with per-100g values:
  - `energy_kcal`: Calories.
  - `fat_g`, `saturated_fat_g`, `carbohydrates_g`, `sugars_g`, `proteins_g`, `salt_g`.
  - **Note:** Data is **crowdsourced and voluntary**. Accuracy is not guaranteed.

### Scoring Systems

- `nutriscore_grade`: Letter grade (a–e; lower is better).
- `nova_group`: NOVA classification (1–4; lower is better for health).
- `ecoscore_grade`: Environmental impact score (a–e; lower is better).

### Allergens & Ingredients

- `allergens`: Comma-separated string or array (e.g., "Milk, Nuts, Soy").
- `ingredients_text`: Raw ingredients list as a string.
- `labels`: Array of attributes (e.g., ["organic", "vegan", "gluten-free"]).

### Data Completeness

- **Caveat:** Open Food Facts is crowdsourced. Many products have incomplete or missing fields.
- **Fallback Strategy:** When critical data (e.g., nutrition facts) is unavailable, gracefully degrade (show "Data not available" rather than crashing or using placeholder data).

---

## Code Pattern: Minimal Lookup (Python)

```python
import requests
import time
from functools import lru_cache

class OpenFoodFactsClient:
    BASE_URL = "https://world.openfoodfacts.org/api/v3.6/product"
    USER_AGENT = "YourApp/1.0 (contact@example.com)"

    def __init__(self, cache_ttl=3600):
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": self.USER_AGENT})
        self.cache_ttl = cache_ttl
        self._last_request = 0

    def lookup(self, barcode: str) -> dict | None:
        """
        Lookup product by barcode.

        Args:
            barcode: EAN-13, UPC, or EAN-8 numeric string

        Returns:
            Dict with keys: name, nutriscore, ingredients, allergens, or None
        """
        # Rate limiting: 15 req/min = 1 req per 4 seconds
        elapsed = time.time() - self._last_request
        if elapsed < 4:
            time.sleep(4 - elapsed)

        try:
            url = f"{self.BASE_URL}/{barcode}.json"
            response = self.session.get(url, timeout=10)
            response.raise_for_status()

            data = response.json()
            if data.get("status") == "ok":
                product = data.get("product", {})
                self._last_request = time.time()
                return {
                    "name": product.get("product_name"),
                    "nutriscore": product.get("nutriscore_grade"),
                    "nova": product.get("nova_group"),
                    "ingredients": product.get("ingredients_text"),
                    "allergens": self._normalize_allergens(
                        product.get("allergens")
                    ),
                    "labels": product.get("labels", []),
                }
            else:
                return None

        except requests.exceptions.HTTPError as e:
            if e.response.status_code == 503:
                # Rate limit hit; back off exponentially
                print(f"Rate limited (503). Backing off...")
                return None
            raise
        except requests.exceptions.RequestException as e:
            print(f"Request error: {e}")
            return None

    @staticmethod
    def _normalize_allergens(allergens_raw) -> list[str]:
        """Convert allergens field (string or array) to list."""
        if isinstance(allergens_raw, list):
            return allergens_raw
        elif isinstance(allergens_raw, str):
            return [a.strip() for a in allergens_raw.split(",")]
        return []

# Usage
client = OpenFoodFactsClient()
product = client.lookup("3560007301171")
if product:
    print(f"Product: {product['name']}, Nutri-Score: {product['nutriscore']}")
```

---

## Code Pattern: Minimal Lookup (JavaScript/Node)

```javascript
class OpenFoodFactsClient {
  constructor(userAgent = "YourApp/1.0 (contact@example.com)") {
    this.baseURL = "https://world.openfoodfacts.org/api/v3.6/product";
    this.userAgent = userAgent;
    this.lastRequest = Date.now();
  }

  async lookup(barcode) {
    /**
     * Lookup product by barcode.
     * @param {string} barcode - EAN-13, UPC, or EAN-8 numeric string
     * @returns {object|null} Product data or null if not found
     */

    // Rate limiting: 4 seconds between requests (15/min)
    const elapsed = Date.now() - this.lastRequest;
    if (elapsed < 4000) {
      await new Promise((r) => setTimeout(r, 4000 - elapsed));
    }

    try {
      const url = `${this.baseURL}/${barcode}.json`;
      const response = await fetch(url, {
        method: "GET",
        headers: { "User-Agent": this.userAgent },
        timeout: 10000,
      });

      if (!response.ok && response.status !== 404) {
        if (response.status === 503) {
          console.warn("Rate limited (503). Back off exponentially.");
          return null;
        }
        throw new Error(`HTTP ${response.status}`);
      }

      const json = await response.json();
      if (json.status === "ok") {
        const p = json.product;
        this.lastRequest = Date.now();
        return {
          name: p.product_name,
          nutriscore: p.nutriscore_grade,
          nova: p.nova_group,
          ingredients: p.ingredients_text,
          allergens: Array.isArray(p.allergens)
            ? p.allergens
            : (p.allergens || "").split(",").map((a) => a.trim()),
          labels: p.labels || [],
        };
      }
      return null;
    } catch (err) {
      console.error("Lookup error:", err);
      return null;
    }
  }
}

// Usage
const client = new OpenFoodFactsClient();
const product = await client.lookup("3560007301171");
if (product) {
  console.log(`${product.name} — Nutri-Score: ${product.nutriscore}`);
}
```

---

## Integration Patterns

### Pattern 1: Simple Barcode Lookup (Mobile App)

**Scenario:** User scans a barcode with their phone; your app fetches product data in real-time.

```
1. Validate barcode (length, format).
2. Call GET /api/v3.6/{barcode}.json with User-Agent header.
3. If status="ok": display product name, image, nutrition facts.
4. If status="not found": show "Product not found in database."
5. If HTTP 503: show "Too many requests. Please try again later."
6. Cache successful results locally (7+ days TTL) to reduce API calls.
```

### Pattern 2: Bulk Testing (Staging Environment)

**Scenario:** You want to load test data without hitting the production API.

```
1. Use staging environment: https://world.openfoodfacts.net
2. Authenticate with Basic Auth (off:off).
3. Load a small dataset of barcodes for testing.
4. Verify response structure and field completeness.
5. Once validated, switch to production: https://world.openfoodfacts.org
```

### Pattern 3: Graceful Degradation (Missing Data)

**Scenario:** Your app relies on nutrition data, but the API is unavailable or product data is incomplete.

```
1. Attempt to fetch product data.
2. If fetch fails or nutriments are missing:
   - Display product name and image (if available).
   - Show: "Nutrition information not available."
   - Offer: "Want to add nutrition data? Visit openfoodfacts.org."
3. Never use placeholder/fake nutritional data.
4. Log errors for monitoring; alert if API is down.
```

---

## Data Quality & Validation

**Crowdsourced Data Warnings:**

- **Allergen Information:** Do **not** use for medical/legal compliance without manual verification.
- **Ingredient Completeness:** May be missing or incorrect.
- **Image URLs:** May be outdated or no longer hosted.
- **Nutrition Data:** Sourced from user uploads; verify critical values before using in medical/legal contexts.

**Recommended Validation:**

```python
def validate_product(product: dict) -> bool:
    """Return False if product lacks critical fields."""
    required = ["name", "ingredients", "allergens"]
    return all(product.get(k) for k in required)
```

---

## Caching Strategy

For production use, implement **local caching** to:

1. Reduce API calls and respect rate limits
2. Improve UX (instant lookups for repeat scans)
3. Handle temporary API downtime gracefully

**Recommended TTL:** 7–30 days (product data changes slowly)

```python
# SQLite example
import sqlite3
import json
import time

conn = sqlite3.connect("products.db")
cursor = conn.cursor()

# Create table if not exists
cursor.execute("""
    CREATE TABLE IF NOT EXISTS products (
        barcode TEXT PRIMARY KEY,
        data TEXT,
        timestamp REAL
    )
""")

# Cache hit (7-day TTL)
cursor.execute(
    "SELECT data FROM products WHERE barcode = ? AND timestamp > ?",
    (barcode, time.time() - 86400 * 7)
)
cached = cursor.fetchone()

# Cache miss: call API, store result
if not cached:
    product = client.lookup(barcode)
    if product:
        cursor.execute(
            "INSERT OR REPLACE INTO products (barcode, data, timestamp) VALUES (?, ?, ?)",
            (barcode, json.dumps(product), time.time())
        )
        conn.commit()
```

---

## Known Limitations & Anti-Patterns

### ✗ Anti-Pattern: Search-as-You-Type

**Problem:** Using the Search API (10 req/min) for autocomplete will exhaust your rate limit immediately.

**Solution:** Use client-side caching, pre-loaded product lists, or the specialized Search-a-licious service.

### ✗ Anti-Pattern: Bulk Product Pulls

**Problem:** Fetching hundreds of products via the API will be blocked by rate limiting.

**Solution:** Download the bulk CSV/JSONL dataset from Open Food Facts if you need large-scale data.

### ✗ Anti-Pattern: Omitting User-Agent

**Problem:** Requests without or with generic User-Agent strings trigger bot-detection filters.

**Solution:** Always include a custom `User-Agent: AppName/Version (email)` header on every request.

### ✗ Anti-Pattern: Trusting Data Accuracy Blindly

**Problem:** Open Food Facts is crowdsourced. Data accuracy is not guaranteed.

**Solution:** Display a disclaimer where appropriate, cache and verify data locally, offer users a way to correct data.

---

## Error Handling Decision Tree

When implementing product lookup, use this decision tree:

```
Request Open Food Facts API
    ↓
[200 OK & status="ok"]  → Extract and return product data
[200 OK & status="not found"]  → Product not in database; return null
[404]                → Malformed request; return null
[503]                → Rate limited; backoff and retry (up to N times)
[Timeout]            → Network error; backoff and retry
[Connection Error]   → Network unavailable; return null or cached data
```

---

## Production Checklist

- [ ] **User-Agent header set** (format: `AppName/Version (email)`)
- [ ] **Rate limiting logic** (4s delay between requests, 503 backoff)
- [ ] **Caching layer** (local SQLite/Redis with 7+ day TTL)
- [ ] **Error handling** (null on "not found", exponential backoff on 503)
- [ ] **Allergen normalization** (string → array conversion)
- [ ] **Data validation** (require non-null name, ingredients)
- [ ] **Staging tests** (dev against `world.openfoodfacts.net` with Basic Auth `off:off`)
- [ ] **Monitoring** (log API errors and rate-limit violations)
- [ ] **Graceful degradation** (never use placeholder nutritional data)

---

## Common Pitfalls for LLMs

1. **Assume API requires authentication:** ❌ Read operations are free; only write needs login.
2. **Ignore User-Agent requirement:** ❌ Will result in IP flagging; always include it.
3. **Treat 503 as permanent failure:** ❌ It's rate limiting; implement exponential backoff.
4. **Parse allergens as always-string:** ❌ Field can be string or array; normalize.
5. **Trust allergen data for medical decisions:** ❌ Crowdsourced; verify manually for critical use.
6. **Forget to cache:** ❌ API has strict rate limits; caching is essential for UX and compliance.
7. **Use v2 or older:** ❌ v3.6 is stable and recommended; legacy versions less reliable.
8. **Hardcode production base URL:** ❌ Always allow staging (`world.openfoodfacts.net`) for testing.
9. **Assume all nutritional data is complete:** ❌ Data is voluntary and crowdsourced; validate and show "N/A" when missing.
10. **Use API for bulk imports without caching:** ❌ Rate limit is 15 req/min; local caching is mandatory for production.

---

## References & Resources

- **Official API Documentation:** https://world.openfoodfacts.org/api/
- **Search-a-licious (Advanced Search):** https://search.openfoodfacts.org/
- **Bulk Data Downloads:** https://world.openfoodfacts.org/data (for bulk analysis)
- **Open Food Facts GitHub:** https://github.com/openfoodfacts/openfoodfacts-server
- **Issues & Support:** https://github.com/openfoodfacts/openfoodfacts-server/issues
- **Status Page:** https://status.openfoodfacts.org/

---

## Summary for Agents

When integrating Open Food Facts into your application:

1. **Always include a custom User-Agent** (format: `AppName/Version (email)`).
2. **Respect the 15 req/min rate limit** for product reads; use caching and bulk downloads for high volume.
3. **Handle missing/incomplete data gracefully**; never fill with placeholder values.
4. **Implement backoff and retry logic** for 503 responses.
5. **Use staging (`openfoodfacts.net`) for development** to avoid polluting production data.
6. **Cache products locally** to reduce API calls and improve responsiveness.
7. **Remember:** Data is crowdsourced. Accuracy is not guaranteed. Display appropriate disclaimers where necessary.
8. **Validate all data** before using it in medical, legal, or critical contexts.
