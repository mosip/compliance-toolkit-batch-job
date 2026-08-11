# AGENTS.md

## Repository Overview

`compliance-toolkit-batch-job` is a small, single-purpose Spring Boot batch job
service for the MOSIP Compliance Toolkit. Its only job is **test run
archival**: on a cron schedule it moves old test runs (and their details) out
of the live `test_run` / `test_run_details` tables into archive tables, once a
configurable number of "keep the latest N" runs has been exceeded. It can also
forcefully revert (un-archive) specific collection IDs.

This is a sibling/companion service to
[`mosip-compliance-toolkit`](https://github.com/mosip/mosip-compliance-toolkit)
(the main toolkit API and UI). This repo does not expose the toolkit UI or
most toolkit APIs — it is deployed as its own Kubernetes workload, triggered
purely on a cron schedule, and shares the same `mosip_toolkit` Postgres
database as the main toolkit service.

The repository is flat: one Java module (`compliance-toolkit-batch-job/`) plus
one Helm chart (`helm/compliance-toolkit-batch-job/`). There are no db_scripts,
db_upgrade_scripts, or other MOSIP-repo-family folders here — schema/tables
are owned and migrated by `mosip-compliance-toolkit`, not by this repo.

## Technology Stack

- **Language / runtime**: Java 11
- **Framework**: Spring Boot 2.0.2.RELEASE, Spring Batch, Spring Cloud Config
  Client, Spring Data JPA
- **Database**: PostgreSQL (via `javax.persistence.jdbc.*` properties and
  Hibernate; H2 is a test/compile-scope dependency only)
- **Build**: Maven (Maven Wrapper `mvnw` / `mvnw.cmd`, wrapper pinned to Maven
  3.9.5 — see `compliance-toolkit-batch-job/.mvn/wrapper/maven-wrapper.properties`)
- **MOSIP shared libraries**: `kernel-core`, `kernel-logger-logback`,
  `kernel-dataaccess-hibernate` (version `1.2.0.1`, from the `kernel.version`
  property in `pom.xml`)
- **Packaging**: Spring Boot executable JAR (`spring-boot-maven-plugin`,
  `repackage` goal), then a Docker image (`compliance-toolkit-batch-job/Dockerfile`,
  base image `eclipse-temurin:11-jre-jammy`)
- **Deployment**: Helm chart at `helm/compliance-toolkit-batch-job/`

## Build & Test Commands

All commands below are run from `compliance-toolkit-batch-job/` (the Maven
module directory), not the repo root.

```shell
cd compliance-toolkit-batch-job

# Build (compiles, runs tests, packages the executable jar)
./mvnw clean package

# Run tests only
./mvnw test

# Skip tests during a package build
./mvnw clean package -DskipTests
```

There is currently only one test class,
`src/test/java/io/mosip/compliance/toolkit/batch/job/ToolkitBatchJobApplicationTests.java`,
and its only test method is commented out. Do not assume meaningful test
coverage exists — treat any change to `TestRunArchivalService` /
`TestRunArchivalTasklet` as untested unless you add coverage yourself.

CI (`.github/workflows/push-trigger.yml`) builds the Maven module via the
shared `mosip/kattu` reusable workflow on push to `develop`, `master`,
`release-1*`, `0.*`, `1.*`, `MOSIP*`, and on pull requests — with **no `paths:`
filter**, so it runs for every push/PR regardless of which files changed, not
just Java changes. It also builds and pushes a Docker image, and (outside pull
requests) publishes to Nexus and runs Sonar analysis.

The Helm chart has its own workflow, `.github/workflows/chart-lint-publish.yml`,
which **is** path-scoped: it only runs on pull requests that touch `helm/**`.

## Configuration

Runtime configuration is layered, matching the rest of MOSIP:

1. `compliance-toolkit-batch-job/src/main/resources/bootstrap.properties` —
   Spring Cloud Config bootstrap: points at
   `spring.cloud.config.uri=https://dev1.mosip.net/config`,
   `spring.cloud.config.label=develop`,
   `spring.cloud.config.name=compliance-toolkit`, and sets
   `server.port=8098` / `server.servlet.context-path=/v1/toolkit`.
2. `compliance-toolkit-batch-job/src/main/resources/application.properties` —
   local defaults, including the batch-job-specific settings:
   - `mosip.toolkit.batchjob.enable.testrun.archival` — master on/off switch
   - `mosip.toolkit.batchjob.testrun.archive.offset` — how many latest test
     runs per collection to keep (rest get archived); `-1` disables archival
   - `mosip.toolkit.batchjob.archival.revert.collectionids` — comma-separated
     collection IDs to forcefully un-archive
   - `mosip.toolkit.batchjob.schedule.cron.testRunArchivalJob` — cron
     expression for the scheduled job (default: midnight daily)
3. The remote Spring Cloud Config server (`compliance-toolkit` config,
   `develop` label) can override any of the above at runtime; the
   `@RefreshScope` on `SchedulerConfig` means the cron schedule is
   refreshable without a restart via the `/refresh` actuator endpoint (see
   `management.endpoints.web.exposure.include=refresh` in
   `bootstrap.properties`).

There is **no local secrets file** in this repo (no `init_values.yaml` or
equivalent). Secrets/config are supplied at deploy time by:

- `helm/compliance-toolkit-batch-job/copy_cm.sh` /
  `copy_cm_func.sh`, which copy the `artifactory-share` and
  `config-server-share` ConfigMaps from other namespaces into the
  `compliance-toolkit` namespace before install (see `values.yaml`'s
  `extraEnvVarsCM: [global, config-server-share, artifactory-share]`).
- The Dockerfile's `CMD`, which downloads the IAM adapter jar at container
  startup (`wget "${iam_adapter_url_env}" -O .../kernel-auth-adapter.jar`)
  rather than baking it into the image — the URL is an environment variable
  injected by the deployment, not a value checked into this repo. `wget`
  performs no digest, checksum, or signature check on the download, so a
  misconfigured or compromised URL can execute arbitrary code from
  `${loader.path}` with the app's full privileges. The deployment must use
  an allowlisted HTTPS URL and verify a pinned digest or signature before
  starting the adapter; fail closed on mismatch.

Never commit real database passwords, config-server URLs for production
environments, or IAM/adapter URLs into `application.properties`,
`bootstrap.properties`, or `values.yaml` — these are environment-specific and
are expected to come from the config server / ConfigMaps / Helm `--set` at
deploy time.

## Project Structure Notes

```text
compliance-toolkit-batch-job/          (Maven module — the batch job itself)
├── pom.xml
├── Dockerfile
├── mvnw, mvnw.cmd, .mvn/
└── src/main/java/io/mosip/compliance/toolkit/batchjob/
    ├── ToolkitBatchJobApplication.java   # Spring Boot entry point
    ├── config/
    │   ├── BatchJobConfig.java          # defines the Spring Batch Job/Step
    │   ├── SchedulerConfig.java         # @Scheduled trigger, cron-driven
    │   └── LoggerConfiguration.java
    ├── entity/                          # JPA entities for test_run(_details)
    │                                     # and their *_archive counterparts
    ├── repository/                      # Spring Data JPA repositories
    ├── impl/TestRunArchivalService.java # the actual archival/revert logic
    └── tasklets/TestRunArchivalTasklet.java  # wraps the service as a Tasklet

helm/compliance-toolkit-batch-job/      (Helm chart — deployment only)
├── Chart.yaml, values.yaml
├── templates/                          # Deployment, Service, VirtualService, etc.
├── install.sh, delete.sh, restart.sh   # cluster install/uninstall/restart helpers
└── copy_cm.sh, copy_cm_func.sh         # ConfigMap-copying helpers used by install.sh
```

This repo does not have its own database migration scripts (`db_scripts` /
`db_upgrade_scripts`) — the `test_run`, `test_run_details`, and their
`*_archive` tables are owned by `mosip-compliance-toolkit`. If a change here
needs a new/changed table or column, the migration belongs in that repo, not
this one.

Given the flat, two-folder layout, this single root `AGENTS.md` is intended to
cover the whole repository. No subfolder-level `AGENTS.md` files are added —
the Java module and the Helm chart are small enough that splitting the
guidance would only fragment it without adding clarity.

## Development Workflow

1. Fork and clone the repository.
2. Branch from `develop` (the active integration branch — see CI trigger
   branches in `.github/workflows/push-trigger.yml`).
3. Make changes under `compliance-toolkit-batch-job/src/...` for job logic, or
   under `helm/compliance-toolkit-batch-job/` for deployment changes.
4. Build and run `./mvnw clean package` from `compliance-toolkit-batch-job/`
   before opening a PR, to catch compile errors.
5. If you touch `helm/**`, be aware the chart-lint-publish workflow will lint
   it on your PR — run `helm lint helm/compliance-toolkit-batch-job` locally
   first if you have Helm installed.
6. Open a pull request against `develop`.

## Pull Request Guidelines

- Target the `develop` branch.
- Keep the PR focused: Java/job-logic changes and Helm/deployment changes are
  reviewed differently (different CI workflows), so avoid mixing large
  unrelated changes across both in one PR unless they are genuinely coupled.
- Since there is effectively no automated test coverage for the archival
  logic, describe in the PR description how the change was manually
  validated (e.g., against a local/dev Postgres instance with sample
  `test_run` rows).
- Follow the existing code style (tab-indented, MOSIP kernel logger usage via
  `LoggerConfiguration.logConfig(...)`, `@Transactional` on any method that
  writes to both a table and its archive counterpart).

## Repository-Specific Considerations

- **Single scheduled job, not a request-driven API.** Although this service
  exposes Spring Boot Actuator endpoints and a web starter dependency, its
  real behavior is driven entirely by the `@Scheduled` cron trigger in
  `SchedulerConfig`. Do not assume there are REST controllers to add
  endpoints to — there are none in this codebase today.
- **Archival is destructive.** `TestRunArchivalService.archiveTestRun` copies
  rows to the archive tables and then **deletes** them from the live tables
  in the same `@Transactional` method. Any change to this logic should be
  reviewed carefully, since a bug can permanently lose test run data if the
  archive copy step and the delete step ever get out of sync.
  `archiveOffset < 0` disables archival entirely — do not treat `0` and
  negative values as equivalent; only `>= 0` triggers archival, and `-1` is
  explicitly documented in `application.properties` as "will not allow any
  archival."
  - `mosip.toolkit.batchjob.archival.revert.collectionids` is parsed with
    `.split(',')` even when empty, which yields a one-element list containing
    an empty string — `TestRunArchivalService` handles this correctly, but
    keep it in mind if you touch that parsing logic.
- **Shared database with `mosip-compliance-toolkit`.** This job reads and
  writes tables it does not own the schema for. Coordinate any entity/column
  changes with that repository first.
- **`-jar` and `-D` flag order in the Dockerfile.** The existing
  `compliance-toolkit-batch-job/Dockerfile` CMD passes `-D...` system
  properties both before and after `-jar` in the glowroot branch
  (`java -Dloader.path=... -jar -javaagent:... -Dspring.cloud.config.label=... compliance-toolkit-batch-job.jar`).
  This is how the file exists in the repo today; if you add new `-D` flags in
  a Dockerfile or run command, put them **before** `-jar`, not after —
  arguments placed after `-jar <file>` are passed to the application's
  `main(String[] args)`, not read as JVM system properties.
- **Windows contributors**: use `mvnw.cmd` instead of `./mvnw` on Windows
  shells.

## Agent rules

### Do

1. Run `./mvnw clean package` (or `./mvnw test`) from
   `compliance-toolkit-batch-job/` after any Java change, and confirm it
   builds before proposing a PR.
2. Put new JVM `-D` system properties before `-jar` in any Docker/run command
   you write or edit.
3. Keep archive/revert logic changes inside a single `@Transactional` method
   pairing a copy with its corresponding delete, matching the existing
   pattern in `TestRunArchivalService`.
4. Treat `helm/**` changes and Java changes as separately reviewable; mention
   in the PR description which CI workflow(s) are expected to run.
5. Verify config property names against `application.properties` /
   `bootstrap.properties` before referencing them in docs or code — do not
   invent property names.

### Do not

1. Do not commit real credentials, config-server URLs, or IAM adapter URLs
   into `application.properties`, `bootstrap.properties`, or `values.yaml`.
2. Do not add or modify database schema/migration files in this repo — schema
   for `test_run`/`test_run_details`/archive tables belongs to
   `mosip-compliance-toolkit`.
3. Do not assume the commented-out test in
   `ToolkitBatchJobApplicationTests.java` provides real coverage — do not cite
   it as evidence that archival logic is tested.
4. Do not change `mosip.toolkit.batchjob.testrun.archive.offset` semantics
   (`-1` disables archival) without updating both the property comment in
   `application.properties` and this file.
5. Do not target `master` for pull requests — this repo's active integration
   branch is `develop`.
