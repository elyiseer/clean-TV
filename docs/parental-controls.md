# Parental controls

Parental controls are optional and remain off until someone creates a PIN from **Home tools → Parental controls**. The PIN must contain four to six digits and is entered with the remote's arrows and OK button. There is no preset PIN.

After authentication, select the apps that should ask for the PIN. Protected apps show a small lock badge on Home. The same setting controls app tiles, module cards, the app library, launcher app actions, and app information or shortcut management. The PIN is checked again for each protected action; authentication is not kept as an open session. From the parental controls screen, the owner can change the PIN, adjust the protected app list, or turn the feature off.

The PIN verifier, random salt, retry counter, lockout deadline, and protected package names are stored in the app's private data directory. The PIN itself is not stored. A salted PBKDF2-HMAC-SHA-256 verifier is compared in constant time. Failed attempts are persisted and trigger increasing delays, up to 30 minutes. Clearing the app's data or uninstalling it removes this local configuration.

## Scope and limitations

This is a launcher-level access prompt, not an Android account or device restriction. It asks for the PIN only for actions initiated through CleanYourTV. A protected app may still be opened through Android Settings, another app, voice assistant, system UI, or another system mechanism. It cannot prevent an already-open app from launching another activity, and it does not lock the whole device. Anyone with sufficient device or app-data access may also remove or alter local state. Use Android's device-level parental controls where system-wide enforcement is needed.
