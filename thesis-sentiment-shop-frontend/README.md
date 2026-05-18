# thesis-sentiment-shop-frontend – Frontend

Angular 19 + Tailwind CSS frontend for the thesis reference application.
Displays a product catalog with customer reviews and a sentiment overview.
All API communication goes to the Spring Boot backend.

For project-level context, prerequisites, and installation instructions,
see the parent `README.md` one level up.

---

## Stack

| Technology | Version |
|---|---|
| Angular | 19 |
| TypeScript | 5.6 (strict mode) |
| Tailwind CSS | 3.4 |

The application uses standalone components, Angular's new control-flow syntax,
signals, and `inject()` throughout.

---

## Module structure

```
|–– src/
|    └–– app/
|         |–– core/
|         |    |–– api/                         <– One HTTP service per backend resource
|         |    └–– models/                      <– Typescript types matching backend DTOs
|         |–– features/
|         |    |–– products/                    <– Product list and detail pages
|         |    └–– sentiment/                   <– Aggregate overview page
|         |–– shared/
|         |    |–– sentiment-badge.component.ts <– Color-coded badge: OSITIVE (green), NEUTRAL (grey), NEGATIVE (red), 
|         |    |                                   null → animated "Pending" (amber)
|         |    └–– star-rating.component.ts     <– Read-only star display
|         |–– app.component.ts                  <– Shell with header navigation
|         |–– app.config.ts                     <– Root providers (router, HTTP client)
|         └–– app.routes.ts                     <– Lazy-loaded route definitions  
└–– proxy.conf.json                             <– Dev-server proxy: /api –> https://localhost:8080
```

---
## Running locally

```bash
npm start
```

This runs `ng serve` on `http://localhost:4200/` with hot reload. The
dev proxy (`proxy.conf.json`) forwards `/api/*` and `/actuator/*` to the
Spring Boot backend on `:8080`, so frontend code can call relative URLs
like `/api/products` and they reach the backend transparently. The 
Spring Boot backend must be running before submitting reviews.

## Running tests

```bash
npm test
```

## Async variant behaviour

The sentiment badge handles the case where `sentiment` is `null` by rendering
an animated "Pending" state. Under async variants (E-Async, S-Async, X-Async)
there is a brief window between review submission and classification callback
during which submitted reviews appear as pending. Under sustained load this
window widens – this is the intended observable behaviour for the thesis
performance analysis and requires no frontend change between variants.