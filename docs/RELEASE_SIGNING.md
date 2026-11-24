# Android APK release signing

The ALVM GitHub Actions workflow builds an unsigned-by-developer debug APK for testing. Release signing is optional and runs only when all four repository secrets are configured:

- `ALVM_RELEASE_KEYSTORE_BASE64`: the base64-encoded JKS/PKCS12 keystore file.
- `ALVM_RELEASE_KEYSTORE_PASSWORD`: keystore password.
- `ALVM_RELEASE_KEY_ALIAS`: signing key alias.
- `ALVM_RELEASE_KEY_PASSWORD`: private-key password.

Create and protect the keystore outside the repository. Never commit the keystore, passwords, base64 payload or signing key to source control. On Linux, encode it with:

```sh
base64 -w0 release.jks
```

On macOS:

```sh
base64 < release.jks | tr -d '\n'
```

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add the four values. Keep the original keystore in a secure backup. Loss of the keystore can prevent future updates from being signed with the same application identity.

The workflow uses Android Build Tools `zipalign` and `apksigner`, then runs `apksigner verify` on the resulting release APK. If any secret is absent, release signing is skipped; the debug APK remains a test build and must not be represented as a Play Store release build.

The emulator job installs and launches the Studio debug APK. Signing configuration does not prove compatibility with a physical device. Before publishing, install the release APK on at least one physical ARM64 device, test the generated Player app separately, and record the Android version, ABI, runtime version, install/launch result and logcat output.
