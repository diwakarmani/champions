# Contributing

`main` only changes through a pull request, and every check in `.github/workflows/ci.yml` must pass.

## Before you open a PR
```bash
mvn spotless:apply   # tidy files you changed
mvn verify           # needs Docker: tests run against PostGIS in Testcontainers
```

## Rules the build enforces
- **Tests never touch a real database.** Spring tests extend `AbstractPostgisIntegrationTest` (profile `test`).
- **Flyway owns the schema.** Add `src/main/resources/db/migration/VN__name.sql`; never edit an applied one. Hibernate only validates. Data changes for production go in `scripts/`, safe to run twice, with a test in `DatabaseMigrationAndScriptsTest`.
- **Listings belong to active agents.** If you add a public query, it must apply the active-agent filter and be covered by `everyPublicSurfaceHidesListingsWithoutAnActiveAgent`.
- **Clients never choose their own role.** Roles come only from an admin.
- **API changes are reviewed.** If `ApiContractSnapshotTest` fails and the change is intended, run `mvn test -Dtest=ApiContractSnapshotTest -Dcontract.update=true` and commit `contracts/openapi.json`.
- **Breaking API changes** fail CI unless the PR has the `api-breaking` label and explains the migration.
- **New controllers use a service.** They may not use a repository directly (`ArchitectureRulesTest`).
- **Coverage only goes up.** See the JaCoCo floor in `pom.xml`.
- **No secrets in the repo.** This repo is public, and CI scans the full history.

Production refuses to start with a default secret or password; see `ProductionConfigGuard`.
