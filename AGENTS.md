# AGENTS.md

## Repository Overview

`compliance-toolkit-batch-job` is a small Spring Boot batch job: on a cron
schedule it archives old rows out of `test_run`/`test_run_details` into
archive tables once a "keep latest N" threshold is exceeded, and can
force-revert (un-archive) specific collection IDs. See `README.md` for setup.

Sibling/companion to
[`mosip-compliance-toolkit`](https://github.com/mosip/mosip-compliance-toolkit)
(main API+UI) — shares its `mosip_toolkit` Postgres DB and table ownership,
but exposes no toolkit UI/APIs itself; it's a separate cron-only K8s workload.

Flat repo: one Java module (`compliance-toolkit-batch-job/`) + one Helm chart
(`helm/compliance-toolkit-batch-job/`). No db_scripts/db_upgrade_scripts here
— schema is owned by `mosip-compliance-toolkit`.

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

Run from `compliance-toolkit-batch-job/`, not the repo root:

```shell
cd compliance-toolkit-batch-job
./mvnw clean package          # compile, test, package the jar
./mvnw test                   # tests only
./mvnw clean package -DskipTests
```

Only one test class exists
(`ToolkitBatchJobApplicationTests.java`), and its one method is commented
out. Treat `TestRunArchivalService`/`TestRunArchivalTasklet` as untested
unless you add coverage.

CI: `.github/workflows/push-trigger.yml` (Maven build + Docker image, via
`mosip/kattu`; Nexus publish + Sonar outside PRs) has **no `paths:` filter**
— runs on every push/PR to `develop`/`master`/`release-1*`/`0.*`/`1.*`/`MOSIP*`
regardless of which files changed. `.github/workflows/chart-lint-publish.yml`
is path-scoped (`helm/**`) only for `pull_request`; its `push` trigger has no
path filter either.

## Configuration

Layered like the rest of MOSIP:

1. `bootstrap.properties` — Spring Cloud Config bootstrap
   (`spring.cloud.config.uri/label/name`), `server.port=8098`,
   `server.servlet.context-path=/v1/toolkit`.
2. `application.properties` — local defaults, incl. batch-job settings:
   - `mosip.toolkit.batchjob.enable.testrun.archival` — on/off switch
   - `mosip.toolkit.batchjob.testrun.archive.offset` — keep-latest-N per
     collection; `-1` disables archival (see Repository-Specific
     Considerations for the exact semantics)
   - `mosip.toolkit.batchjob.archival.revert.collectionids` — comma-separated
     IDs to force-revert
   - `mosip.toolkit.batchjob.schedule.cron.testRunArchivalJob` — cron expr
     (default midnight daily)
3. Remote Spring Cloud Config server can override any of the above at
   runtime; cron schedule is refreshable via the `/refresh` actuator
   endpoint (`@RefreshScope` on `SchedulerConfig`).

No local secrets file (no `init_values.yaml`). Supplied at deploy time by:

- `helm/compliance-toolkit-batch-job/copy_cm*.sh` — copies
  `artifactory-share`/`config-server-share` ConfigMaps into the
  `compliance-toolkit` namespace before install.
- The Dockerfile's `CMD`, which `wget`s the IAM adapter jar at container
  startup from an env-injected URL, with **no digest/checksum/signature
  check** — a misconfigured or compromised URL runs arbitrary code with the
  app's full privileges. Deployment must use an allowlisted HTTPS URL and
  verify a pinned digest/signature; fail closed on mismatch.

Never commit real DB passwords, config-server URLs, or IAM/adapter URLs into
`application.properties`, `bootstrap.properties`, or `values.yaml`.

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

`test_run`/`test_run_details`/`*_archive` tables are owned by
`mosip-compliance-toolkit` — a schema change belongs in that repo, not here.
No subfolder-level `AGENTS.md` files; this single root file covers both
folders.

## Development Workflow

1. Branch from `develop`.
2. Change job logic under `compliance-toolkit-batch-job/src/...`, or
   deployment under `helm/compliance-toolkit-batch-job/`.
3. Run `./mvnw clean package` before opening a PR.
4. If touching `helm/**`, run `helm lint helm/compliance-toolkit-batch-job`
   locally first — chart-lint-publish will run it on your PR regardless.

## Pull Request Guidelines

- Target `develop`; sign off commits (`git commit -s`).
- Keep Java/job-logic and Helm/deployment changes separate (different CI
  workflows) unless genuinely coupled.
- No automated test coverage for archival logic — describe manual validation
  in the PR (e.g. against a local/dev Postgres with sample `test_run` rows).
- Match existing style: tab-indented, `LoggerConfiguration.logConfig(...)`
  for logging, `@Transactional` on any method writing to a table + its
  archive counterpart.

## Repository-Specific Considerations

- **Cron job, not a request-driven API.** Actuator/web-starter deps exist,
  but behavior is entirely `@Scheduled`-driven (`SchedulerConfig`) — no REST
  controllers in this codebase.
- **Archival is destructive.** `TestRunArchivalService.archiveTestRun` copies
  then **deletes** rows in one `@Transactional` method — a bug here can
  permanently lose data. `archiveOffset`: only `>= 0` triggers archival,
  `-1` disables it entirely (`0` is not equivalent to negative).
  `archival.revert.collectionids` is `.split(',')`'d even when empty
  (yields a one-element empty-string list) — handled correctly today, but
  watch this if you touch the parsing.
- **Per-collection skip**: `performArchival` only archives a `collectionId`
  if `ComplianceTestRunSummaryRepository...ForCollectionId` returns empty —
  a collection with an existing summary row is left alone regardless of
  `archiveOffset`.
- **Shared DB**: coordinate entity/column changes with
  `mosip-compliance-toolkit`, which owns the schema.
- **Dockerfile `-D`/`-jar` order**: the glowroot CMD branch already mixes
  `-D` flags before *and* after `-jar` — leave that as-is, but any **new**
  `-D` flag you add must go **before** `-jar` (flags after `-jar <file>` go
  to the app's `main(args)`, not the JVM).
- Windows: use `mvnw.cmd`, not `./mvnw`.

## Agent rules

### Do

1. Run `./mvnw clean package` (or `test`) after any Java change; confirm it
   builds before proposing a PR.
2. New `-D` system properties go before `-jar` in Docker/run commands.
3. Pair archive/revert copy+delete inside one `@Transactional` method.
4. Verify property names against `application.properties`/
   `bootstrap.properties` before citing them — don't invent names.

### Do not

1. Commit real credentials/config-server/IAM-adapter URLs anywhere.
2. Add/modify DB schema files here — belongs to `mosip-compliance-toolkit`.
3. Cite the commented-out test in `ToolkitBatchJobApplicationTests.java` as
   real coverage.
4. Change `archive.offset`'s `-1`-disables semantics without updating both
   `application.properties` and this file.
5. Target `master` for PRs — active branch is `develop`.
