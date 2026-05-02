# Default Release Keystore

This directory contains a self-signed release keystore committed to the repository
intentionally so that CI builds (and any local clone) can produce signed release APKs
out-of-the-box.

- `release.jks` — RSA 2048, valid for 100 years
- `keystore.properties` — credentials referenced by `app/build.gradle.kts`

Default credentials (storePassword / keyAlias / keyPassword): `tinyswords`

This is acceptable for this private project. Do **not** publish APKs signed with this
keystore to a public Play Store account you intend to keep — Play Store binds a
signing key to an app forever and a leaked default key cannot be rotated.
