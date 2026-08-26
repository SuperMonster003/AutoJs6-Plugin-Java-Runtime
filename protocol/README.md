# Frozen AutoJs6 protocol inputs

This independent plugin consumes three repository-local AAR snapshots:

- `common-plugin-api.aar` for `org.autojs.plugin.INFO` metadata;
- `protocol-wire-api.aar` for the bounded binary wire format;
- `jvm-source-api.aar` for the JVM source Binder protocol and entry ABI.

`protocol-artifacts.lock.json` records the clean host source revision and the exact SHA-256 of every
artifact. The Gradle `verifyPinnedInputs` task checks the complete file/module set, rejects symlinked
artifacts, and verifies every digest before compilation or assembly.

The current snapshot records `sourceDirty=false` and points to host commit
`1b79603bc7304ae44fe878b27ef01d2d77b2a963`, which finalized Protocol 1.6, tagged-wire schema 1.3,
the canonical bounded multi-file source-package codec, and Entry API 4. Refresh all three AARs and
their lock together whenever the host protocol source changes.
