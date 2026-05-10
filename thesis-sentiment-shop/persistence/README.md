# persistence

JPA/Hibernate base infrastructure shared by the domain modules. Not a
domain module itself — defines no entities representing real things in
the application.

## Role in the architecture

`catalog` and `reviews` both need an audited base entity, and `web`
needs JPA wired into the Spring context. Putting both concerns into a
single shared module means the domain modules don't depend on each
other through Spring Data, only through this module.

## Public surface

| Type                       | Purpose                                                                                                                                                                                                     |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `BaseEntity`               | `@MappedSuperclass` providing `id`, `createdAt`, and `updatedAt`. All persistent entities in `catalog` and `reviews` extend it.                                                                             |
| `PersistenceConfiguration` | `@Configuration` that enables JPA auditing via `@EnableJpaAuditing`. Discovered via Spring Boot's component scan from the `web` module. |

## Schema management

Hibernate's `ddl-auto=update` is used in development. The production-grade
schema migrations are out of scope for this thesis and are not implemented.

## Dependencies

- `spring-boot-starter-data-jpa` (compile)
- 