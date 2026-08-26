package demo.entries;

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class ScriptEntry implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) {
        return ScriptEntry.class.getName();
    }
}
