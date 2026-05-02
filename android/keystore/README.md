# Default Tiny Swords Keystore

This directory ships with a **public** Android keystore so that every push to
every branch can produce installable, signed release APKs through the GitHub
Actions workflow.

| Field | Value |
| --- | --- |
| File | `tinyswords.jks` |
| Alias | `tinyswords` |
| Store password | `tinyswords` |
| Key password | `tinyswords` |
| Validity | 100 years |
| CN | `Tiny Swords Realm War` |

**Do not use this keystore for any other application.** Anyone with access to
this repo can re-sign APKs as `com.tinyswords.realmwar`. If you ever publish
the project, regenerate a private keystore and override
`keystore.properties` (or set the `KEYSTORE_*` env vars in CI).
