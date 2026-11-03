# VoxelWorlds security policy

## Supported versions

This repository is under active development. Until a versioned public release is explicitly announced, development snapshots and release candidates are provided for evaluation and are **not** a promise of production security support.

After release, security fixes will be prioritised for the latest supported version listed in that release's notes. Older server-version adapters may receive fixes where practical; report potential issues affecting any supported Minecraft version.

## Report a vulnerability privately

Go to this repository's **Security → Advisories → Report a vulnerability** and submit a private report. If private vulnerability reporting is not enabled yet, contact a VoxelHorizons maintainer privately through the official support community to arrange a confidential reporting channel, **without posting exploit details publicly**.

Please include (only in the private report) the affected plugin and version, Minecraft and server implementation versions, Java/Bedrock client details when relevant, reproduction steps, potential impact, and any known safe mitigation. **Never submit live passwords, access tokens, webhook secrets, private customer data, or full production logs.** Redact sensitive details first.

Do not disclose the issue in public GitHub issues, discussions, or Discord channels before coordinated review. Maintainers will assess exploitability, affected shipped or provided dependencies, supported versions, remediation, and disclosure on a case-by-case basis. No fixed response or patch timeframe is implied.

## Dependency alerts and source changes

GitHub Dependabot alerts, security advisories, and reviewed dependency-update PRs should be investigated. CI success alone does not establish that a vulnerable library is non-exploitable. For Java plugin builds, distinguish runtime/shaded dependencies from test-only or `provided` dependencies when assessing exposure.

Every security fix must pass the repository's applicable CI and Minecraft compatibility checks. Do not disable security checks, automatically dismiss Critical/High findings, or publish a release solely to make alert counts go down.
