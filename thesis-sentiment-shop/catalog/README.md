# catalog

The product domain including products and product lookup.

## Role in the architecture

`catalog` owns the `Product` entity and the read-side service used by
`web` to list and fetch products.

## Public surface

| Type                | Purpose                                          |
|---------------------|--------------------------------------------------|
| `Product`           | JPA entity (extends `BaseEntity`)                |
| `ProductRepository` | Spring Data JPA repository                       |
| `CatalogService`    | Read-only service: `findAll()`, `findById(...)`  |

## Stubbed behaviour

Some `Product` fields are placeholders rather than real data – see
`sample-data/README.md`. 

## Dependencies

- `persistence` (compile)
- `spring-boot-starter-data-jpa` (compile)
