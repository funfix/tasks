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

- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`
- `SIGNING_KEY_ID`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_KEY`

The agent also requires `DEEPSEEK_API_KEY`. Renovate and the agent require
`AUTOMATION_APP_ID` and `AUTOMATION_APP_PRIVATE_KEY`.

## Automation GitHub App

Renovate and the agent use the existing organization-owned `Funfix` GitHub App.
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

## Agent workflow

Pi uses DeepSeek's API and the Funfix credentials above. Add the repository
Actions secret `DEEPSEEK_API_KEY`.

Users with write access or higher can start an issue or PR conversation comment
with `/agent`, followed by a request:

```text
/agent Please take care of this issue, but keep the JDK version at 17.
/agent Please review this PR, especially cancellation and null-safety.
```

The agent can review contributor PRs, but cannot push to their forks. On fork PRs,
use a conversation comment, not an inline comment.

Edit `.github/workflows/agent.md`, then regenerate the lock file with
[gh-aw v0.91.7](https://github.com/github/gh-aw/releases/tag/v0.91.7):

```bash
gh aw compile agent --strict --validate
```
