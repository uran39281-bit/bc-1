# Preview-signed CI builds

Android installs an update only when it is signed with the same certificate as the installed app.
CI builds are otherwise signed with a throwaway debug key, so they cannot update an existing
BC-1 Preview. When the secrets below are set, the `build` job also uploads
**BC-1-v1.1.0-preview-signed-apk**, re-signed with the retained preview key
(certificate SHA-256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`). That APK installs over
the existing app and keeps its projects.

## One-time setup

In the repository: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Value |
| --- | --- |
| `BC1_KEYSTORE_BASE64` | The keystore file as one line of base64: `base64 -w0 preview.keystore` (macOS: `base64 -i preview.keystore`) |
| `BC1_KEYSTORE_PASSWORD` | Keystore password |
| `BC1_KEY_ALIAS` | Alias of the signing key in the keystore (`keytool -list -keystore preview.keystore`) |
| `BC1_KEY_PASSWORD` | Key password; may be omitted when it equals the keystore password |

The key never enters the repository. The workflow decodes it into the runner's temporary directory
only for signing, deletes it afterwards, and fails the step if the resulting certificate is not the
preview certificate above (so a wrong keystore cannot produce an APK that silently refuses to update).
Without the secrets the step is skipped with a warning and only the debug-signed APK is uploaded.
Forks never receive these secrets.
