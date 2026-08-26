# Frozen AutoJs6 protocol inputs

This independent plugin consumes three repository-local AAR snapshots:

- `common-plugin-api.aar` for `org.autojs.plugin.INFO` metadata;
- `protocol-wire-api.aar` for the bounded binary wire format;
- `jvm-source-api.aar` for the JVM source Binder protocol and entry ABI.

`protocol-artifacts.lock.json` records the clean host source revision and the exact SHA-256 of every
artifact. The Gradle `verifyPinnedInputs` task checks the complete file/module set, rejects symlinked
artifacts, and verifies every digest before compilation or assembly.

The current snapshot records `sourceDirty=false` and points to host commit
`5c3f6f38e1fd1595b4ca576c6647854ec0204ae0`, which keeps Protocol 1.6, tagged-wire schema 1.3,
and Entry API 4 while adding the shared caller-selected Java entry-name profile used by both the
single-file and canonical bounded multi-file source-package paths. Existing launches still default
to `Main`. Refresh all three AARs and their lock together whenever the host protocol source changes.
