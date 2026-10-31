# VoxelWorlds — Release engineering and operator checklist

This project uses read-only CI for pull requests and branch pushes. A normal commit to `main` **must not** create or overwrite a GitHub Release. CI artifacts are test outputs, not certified public distributions.

## Version policy

- Adopt independent [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`, with pre-releases such as `1.0.0-rc.1`.
- Minecraft server versions belong in distribution JAR names and the tested compatibility table, **not** in the plugin's SemVer.
- Retain the existing Maven `-SNAPSHOT` version until the product passes its release checks; do not mark an unverified build stable merely to exercise the workflow.
- Before a release, update every module/parent `pom.xml` as appropriate. Check plugin descriptor versions, supported server versions, dependencies, and current changelog.
- Keep the JARs produced by a single reviewed release build together with a `SHA256SUMS` file.

## Creating a release candidate (owner only)

1. Merge the reviewed release/version PR, then verify the default-branch CI and manual Java/Bedrock acceptance matrix.
2. Ensure all release dependencies and source revisions are pinned and compatible. No `-SNAPSHOT` dependencies may be used for a stable addon release.
3. Create an **existing annotated tag** on the approved `main` commit, such as `v1.0.0-rc.1`, where the tag name matches the Maven version on that commit.
4. From the repository's Actions tab, run **Prepare VoxelWorlds Release Draft** on `main` with that tag. Only the owner account `Fibbicles` is currently allowed to initiate the workflow.
5. The workflow verifies the existing tag and its ancestry on `main`, checks version metadata, tests/builds from that exact commit, produces hashes, and creates a **draft** GitHub Release, without exposing a public download.
6. Inspect release files, verify SHA-256 checksums and installation on the certified server matrix, then manually publish the draft. Never replace assets on an already published version.
7. Enable a protected `release` environment and immutable GitHub Releases in repository settings, and protect `v*` tags, before creating the first public stable release.

**Required GitHub governance:** A developer with Write access must not be able to trigger a public release by pushing to `main`. The release workflow deliberately does not run on `push` and the write token is granted only to its final draft-creation job. An environment name by itself is *not* an approval rule; configure required reviewers separately on GitHub.

**Important:** The draft workflow is intentionally blocked while the Maven version is `-SNAPSHOT`. Addon release jobs also reject `-SNAPSHOT` VoxelCore/Furniture dependencies. Review any pinned dependency commit references before the first release.
