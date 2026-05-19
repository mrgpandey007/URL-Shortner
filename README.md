# URL Shortener System

A production-grade URL shortener built with Spring Boot, Redis, and PostgreSQL.

---

## Architecture

```
Client
  │
  ▼
Spring Boot (REST API)
  │
  ├── POST /api/shorten → UrlService → UrlRepository → PostgreSQL
  │
  └── GET /{shortCode}
        │
        ▼
      Redis (cache hit → 1ms)
        │ cache miss
        ▼
      PostgreSQL (DB lookup → 3-5ms)
        │
        ▼
      302 Redirect → Original URL
```

---

## Tech Stack

| Layer        | Technology         | Why                                              |
|--------------|--------------------|--------------------------------------------------|
| Framework    | Spring Boot 3.2    | Industry standard, production-ready              |
| Database     | PostgreSQL 15      | ACID compliance, B-tree index on short_code      |
| Cache        | Redis 7            | In-memory, sub-millisecond lookups               |
| ORM          | Spring Data JPA    | Boilerplate-free DB access                       |
| Encoding     | Custom Base62      | Collision-free, URL-safe, 56B combinations       |
| Validation   | Bean Validation    | Reject bad input before it reaches service layer |

---

## Running Locally

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker + Docker Compose

### 1. Start infrastructure
```bash
docker-compose up -d
```
This starts PostgreSQL on port 5432 and Redis on port 6379.
Redis Commander (visual UI) available at http://localhost:8081

### 2. Run the application
```bash
mvn spring-boot:run
```
Application starts on http://localhost:8080

---

## API Reference

### Shorten a URL
```http
POST /api/shorten
Content-Type: application/json

{
  "url": "https://www.amazon.in/laptop/dell-inspiron-15/dp/B09XK7KZRM",
  "customAlias": "dell-laptop",   // optional
  "expiryHours": 24               // optional, null = never expires
}
```

**Response: 201 Created**
```json
{
  "shortUrl": "https://short.ly/aX9kLm",
  "shortCode": "aX9kLm",
  "originalUrl": "https://www.amazon.in/...",
  "createdAt": "2024-01-15T10:30:00",
  "expiresAt": "2024-01-16T10:30:00",
  "clickCount": 0
}
```

### Redirect
```http
GET /{shortCode}
```
**Response: 302 Found** → redirects to original URL

### Get Stats
```http
GET /api/stats/{shortCode}
```
**Response: 200 OK** → returns ShortenResponse with click count

### Delete
```http
DELETE /api/{shortCode}
```
**Response: 204 No Content**

---

## Key Design Decisions

### Why Base62 encoding from DB ID?
- **No collisions**: each DB row has a unique auto-increment ID. Base62 encodes it deterministically.
- **56 billion combinations**: 62^6 codes from just 6 characters.
- **Alternative rejected**: random string generation risks collisions and needs retry logic.

### Why 302 (not 301) redirect?
- **301 = Permanent**: browser caches it. Your server is never contacted again. Click count = always 0.
- **302 = Temporary**: browser always checks your server. Every click is recorded.

### Why Redis TTL = 24 hours?
- Hot URLs stay cached (99% of traffic is for the top 1% of links).
- TTL ensures stale/deleted links eventually stop resolving from cache.
- `@CacheEvict` on delete handles immediate invalidation.

### Why index on short_code?
- Every redirect does a `WHERE short_code = ?` lookup.
- Without index: full table scan, O(n). With B-tree index: O(log n).
- At 10M rows, this is the difference between 10ms and 0.01ms per query.

### Why increment click_count with a direct UPDATE query?
- Loading the entity to increment it adds unnecessary overhead.
- `UPDATE urls SET click_count = click_count + 1 WHERE short_code = ?` is atomic and cheap.

---

## Running Tests
```bash
mvn test
```
Key tests:
- `Base62EncoderTest` — validates 100,000 consecutive encodes produce zero collisions.

---

## Potential Improvements (Scale)
1. **Async click tracking**: push to a message queue (Kafka/SQS) instead of synchronous DB write
2. **Rate limiting**: prevent abuse (e.g., 100 shortens/hour per IP)
3. **User accounts**: track per-user URLs
4. **Analytics**: time-series click data, geographic breakdown
5. **Bloom filter**: fast "does this short code exist?" check before hitting Redis
6. **Database sharding**: partition by short_code prefix at extreme scale
