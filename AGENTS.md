# Moapic working instructions

- The user authorized publishing completed updates from this chat to https://github.com/jhm1128-lgtm/moapic without asking again for each ordinary update.
- Finish requested changes and relevant checks before uploading. Report actual verification limits; do not claim device tests were performed when they were not.
- Keep source, user instructions, and current tested APK downloads in sync. Increment versionCode/versionName for an installable update.
- Fetch/check the remote before pushing; preserve other contributors' changes and never force push to main.
- Do not commit local.properties, build caches, SDK/JDK downloads, signing keys, credentials, or machine-specific verification logs.
- Keep the existing local test signing key private. APK updates must retain the same signing certificate to preserve the installed app's data.
- Maintain offline Korean and English text recognition and the lightweight ARM64 / ARM32 optimized APK variants.
- A future update upload means work performed during an active task; no unattended filesystem monitor or recurring job is configured.
