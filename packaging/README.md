# Package manager manifests

These files are inputs for external package indexes. They are not installed by
the Gradle build.

## Scoop (Windows)

`packaging/scoop/noir.json` installs the portable Windows zip from the matching
GitHub release. It follows the standard Scoop manifest layout and includes
`checkver` / `autoupdate` for future releases.

To publish it, add the manifest to a Scoop bucket (for example
`Verlintas/scoop-bucket`) and install it with:

```powershell
scoop bucket add verlintas https://github.com/Verlintas/scoop-bucket
scoop install noir
```

The manifest currently points at `v0.4.3`; update `version`, `url`, and `hash`
when publishing a new release.

## WinGet (Windows)

`packaging/winget/<version>/` contains the three manifests expected by
[winget-pkgs](https://github.com/microsoft/winget-pkgs):

```
Verlintas.NOIR.yaml
Verlintas.NOIR.installer.yaml
Verlintas.NOIR.locale.en-US.yaml
```

Validate them on Windows with:

```powershell
winget validate --manifest packaging\winget\0.4.3
```

Then submit them to `microsoft/winget-pkgs` (normally with `wingetcreate` or a
pull request). WinGet manifests are version-specific, so a new directory is
needed for each release.

Both manifests use the `SHA256SUMS.txt` values from the GitHub release. Verify
the hash before submitting:

```bash
sha256sum -c SHA256SUMS.txt
```
