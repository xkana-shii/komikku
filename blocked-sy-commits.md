# Blocked TachiyomiSY candidates

| SY commit | Blocker and revisit condition |
| --- | --- |
| `66f9b495d9ad48f12e8f993c8e82b38bbc6b4e47` | WebKit 1.17.0 must be evaluated with the project's Android Gradle Plugin and dependency line. Komikku deliberately remains on WebKit 1.15.0 pending that coordinated upgrade. |
| `9b464c884e5f415567ec2455b55ed633260a6ae2` | AGP 9.3.1 requires build-logic and split-version-catalog migration from Komikku's AGP 8.13.2. |
| `e5ffba461dd4f8a0c44f0b4d0b3a96f10ce21bdd` | AboutLibraries 15.0.4 pulls AndroidX Core metadata requiring the AGP 9 line. Revisit with the AGP migration. |
| `188f396609f82b52bf2efaedad070b6c29c07c39` / `a8e0465c999afbe3e06b41c8fef3e8577188fcde` | SY's alpha Compose BOM and workaround removal require a single coordinated Compose migration; Komikku intentionally uses the stable BOM. |
| `380496a5becb5be0d83a4bc86112e1ff266a6448` | SyncYomi protocol v2 requires a compatibility design for Komikku's WebDAV/Google Drive transports and custom bookmark/blacklist payloads. |
| `2c03122571afd23905dbbc8d551787a4c1cc5cea` | Upstream later disabled resumable image downloads (`c148d18c01`), so porting its progress correction would implement a retired feature. |