# How-to

## Publishing

`gradle.properties` stores the base `project.version`. When `stable_version` is
false, the build convention appends `-SNAPSHOT`. A stable publish must use the
exact ref `refs/tags/vX.Y.Z`, where `X.Y.Z` matches `project.version`.

The build publishes both `tasks-jvm` and `tasks-kotlin-coroutines`. Maven
Central releases artifacts automatically after publication. Snapshot
publishing must be enabled for the `org.funfix` namespace in the
[Central Portal](https://central.sonatype.com/).

### Prepare a stable release

1. Set `project.version` in `gradle.properties` to the release version.
2. Update the Javadoc link and dependency examples in `README.md` to that
   version.
3. Run `make check-all`.

Commit those changes and create and push the matching tag, such as `v1.2.3`.
Then run `manual-publish` with `ref_to_publish` set to `refs/tags/v1.2.3` and
enable `stable_version`.

The manual publish workflow uses these secrets:

- `GH_TOKEN`
- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`
- `SIGNING_KEY_ID`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_KEY`

OpenCode also requires `OPENCODE_API_KEY`. Renovate and OpenCode require
`AUTOMATION_APP_ID` and `AUTOMATION_APP_PRIVATE_KEY`.

## Automation GitHub App

Renovate and OpenCode use the existing organization-owned `Funfix` GitHub App.
Its installation includes `funfix/tasks`. Each workflow requests an
installation token scoped to this repository.

The app uses these permissions:

- Organization: `Members: read-only`
- Repository: `Administration: read-only`
- Repository: `Checks: read and write`
- Repository: `Commit statuses: read and write`
- Repository: `Contents: read and write`
- Repository: `Dependabot alerts: read-only`
- Repository: `Issues: read and write`
- Repository: `Pull requests: read and write`
- Repository: `Workflows: read and write`
- Repository: `Metadata: read-only`, granted automatically by GitHub

`Contents: read and write` permits Git pushes. `Workflows: read and write` is
needed when automation changes files under `.github/workflows`.
