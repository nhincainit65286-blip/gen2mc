GoogleChat Forge 1.8.9 (client-side)

This directory contains a standalone Forge 1.8.9 port focused on practical chat translation:

- Incoming chat translation (server -> client language)
- Outgoing chat translation (client -> server language)
- Async requests, bounded parallelism, and LRU cache
- Unofficial Google Translate HTTP endpoint (`translate.googleapis.com`)

Important notes:

- This is a separate legacy module, not wired into the root Fabric build.
- Forge 1.8.9 tooling is old (ForgeGradle 2.1) and should be built from this directory.
- Use Java 8 for this module.

Build (inside `forge-1.8.9`):

1. Use a Java 8 JDK (`JAVA_HOME` points to JDK 8)
2. Run `gradle setupDecompWorkspace` (first time)
3. Run `gradle build`

Configuration file:

- `config/googlechatforge189.cfg`

Default behavior:

- Outgoing normal chat is translated before sending.
- Commands (`/something`) are not translated.
- Incoming action bar/status messages are left untouched.
