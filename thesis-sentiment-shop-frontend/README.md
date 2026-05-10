# thesis-sentiment-shop-frontend

Angular 19 + Tailwind CSS frontend for the sentiment-shop reference
application. Sibling project to the Spring Boot backend
(`thesis-sentiment-shop`).

## Folder structure

```
src/app/
|–– app.component.ts          <- shell with header navigation
|–– app.config.ts             <- root providers (router, HTTP client)
|–– app.routes.ts             <- lazy-loaded route definitions
|–– core/
|   |–– api/                  <- HTTP services (one per backend resource)
|   └–– models/               <- TypeScript types matching backend DTOs
|–– features/
|   |–– products/             <- product list + detail pages
|   └–– sentiment/            <- aggregate overview page
└–– shared/
    |–– sentiment-badge.component.ts   <- reusable color-coded badge
    └–– star-rating.component.ts       <- reusable interactive component
```

## Prerequisites

- **Node.js 20.x or 22.x** (Angular 19 requirement)
- The backend running on `http://localhost:8080` for the dev proxy to work

## Running in development

```bash
npm start
```

This runs `ng serve` on `http://localhost:4200/` with hot reload. The
dev proxy (`proxy.conf.json`) forwards `/api/*` and `/actuator/*` to the
Spring Boot backend on `:8080`, so frontend code can call relative URLs
like `/api/products` and they reach the backend transparently.

## Running tests

```bash
npm test
```

## What is not here

- **Authentication UI**: backend uses a hardcoded test user, frontend
  doesn't have a login flow, since out-of-scope in the thesis
- **Pagination controls**: services accept `page`/`size` params but the
  list components currently fetch only the first page. Sufficient for
  the thesis dataset size.

## Use of AI assistance

Parts of this source code were designed with the aid of AI and subsequently reviewed and revised by
the author. The code fragments created in this way are clearly marked inline at the point where they
appear. Where no such marking is present, the code is the author’s own original work. 