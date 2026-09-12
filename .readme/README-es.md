<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de compilación y ejecución de código Java 8 (archivo único / paquete de código multiarchivo) para AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Idiomas (Languages)

******

El README.md actual admite los siguientes idiomas:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-fr.md)
- Español [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ar.md)

******

### Introducción

******

El plugin AutoJs6 Java Runtime permite a AutoJs6 compilar y ejecutar directamente código fuente Java (`.java`), como archivo único o como paquete acotado de 2 a 32 archivos. Integra el compilador Eclipse ECJ 3.26.0 (nivel de lenguaje Java 8) y el conversor de bytecode D8 8.13.23, y ejecuta el resultado compilado en un proceso worker desechable; ni el compilador ni el script se ejecutan nunca dentro del proceso de AutoJs6.

Este plugin y [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) son plugins hermanos: pueden instalarse juntos, sirviendo respectivamente al código Java / Kotlin, y AutoJs6 recuerda el componente de compilación elegido para cada lenguaje.

******

### Funciones

******

- Proporciona el servicio de compilación/ejecución `org.autojs.plugin.JVM_SOURCE` y el servicio de descubrimiento `org.autojs.plugin.INFO` del centro de plugins, ambos protegidos por firma y ejecutados en procesos auxiliares separados.
- Integra el compilador Eclipse ECJ 3.26.0 (nivel de lenguaje Java 8); la salida se convierte a DEX con D8 8.13.23 antes de ejecutarse, en 1–4 archivos `classesN.dex` contiguos (32 MiB en total; los dispositivos API 26 se mantienen en DEX único).
- Admite seis puentes de capacidades del host autorizados individualmente: salida de consola en vivo `console().log/error`, lanzamiento de aplicaciones `app().launch`, `sleep` interrumpible, mensajes `toast`, más la lectura `clipboard().getText` y la escritura `clipboard().setText` del portapapeles, concedidas de forma independiente.
- Admite argumentos de script del host: `context.args()` devuelve una instantánea JSON de solo lectura de los argumentos de la configuración de ejecución, sin autorización adicional; los scripts existentes sin argumentos siguen funcionando sin cambios.
- Acepta un archivo fuente único o un paquete canónico de 2 a 32 archivos; el nombre simple de la clase de entrada puede elegirse explícitamente por el llamador y es `Main` por defecto.
- Desugaring controlado de la core library: `java.time` y ciertos métodos mejorados de Stream funcionan desde API 24; los métodos no admitidos fallan en compilación en lugar de hacer fallar dispositivos antiguos en ejecución.
- Caché de compilación autenticada: reejecutar el mismo código acierta la caché y omite la compilación; cualquier cambio de versión de las herramientas invalida automáticamente todas las cachés anteriores.
- Los errores de compilación se transmiten uno a uno en el orden fuente de ECJ, con nombres de archivo seguros y posiciones línea/columna; cada ejecución termina con un resumen de observación acotado: tiempos por fase, resultado de la caché y muestras de recursos.
- README y CHANGELOG están disponibles en diez idiomas: chino simplificado, chino tradicional (HK/TW), inglés, francés, español, japonés, coreano, ruso y árabe.

******

### Inicio rápido

******

- **Instalar** — Descargue el APK desde [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) e instálelo, o compile localmente como se describe en la sección Compilación. Atención: el plugin debe firmarse con el mismo certificado que AutoJs6, y el código de versión del host AutoJs6 debe ser al menos 5281.
- **Activar** — Ejecutar código JVM es actualmente una función experimental de AutoJs6: active el interruptor experimental en el host y seleccione explícitamente este plugin como componente de compilación para el lenguaje Java. Si falta alguno de los pasos, la ejecución informa los códigos estables `JVM_SOURCE_EXPERIMENT_DISABLED` o `JVM_SOURCE_PROVIDER_NOT_SELECTED` respectivamente; si hay varios componentes de compilación Java activados a la vez, AutoJs6 también pide primero una selección explícita.
- **Ejecutar** — Cree un archivo `.java` en el editor de AutoJs6, escriba una clase de entrada que implemente la interfaz `AutoJsJvmEntry` y pulse ejecutar (vea el ejemplo de uso más abajo). El nombre físico del archivo es libre; el nombre simple de entrada es `Main` por defecto, y los llamadores de script pueden elegir otro nombre simple ASCII mediante la API de entrada explícita. La declaración `package` es opcional — sirve cualquier paquete ASCII legal.
- **Solucionar problemas** — Si la compilación falla, la consola muestra los diagnósticos de ECJ con nombres de archivo seguros y posiciones línea/columna, y los errores múltiples se transmiten uno a uno; los fallos de ejecución exponen un código de error estable y la fase de fallo (como `SOURCE_TOO_LARGE`, `TIMEOUT`, `BUSY`), y las excepciones de ejecución muestran solo un nombre de clase saneado con la línea fuente. Las salidas esperadas de cada ejemplo están en la [guía de ejemplos](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md); la tabla completa de códigos de error está en la [guía de Java Context API](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md).

******

### Ejemplo de uso

******

Un ejemplo mínimo listo para ejecutar que demuestra cuatro capacidades básicas: salida de consola, toast, sleep interrumpible y lanzamiento de aplicaciones:

```java
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) throws Exception {
        context.console().log("Hello from Java 8");
        context.toast("AutoJs6 Java Runtime");
        context.sleep(500L);
        boolean launched = context.app().launch("org.autojs.autojs6");
        return launched;
    }
}
```

`console().log/error` se transmite línea a línea mientras el script se ejecuta; `sleep` se interrumpe rápidamente con una acción de parada. Más ejemplos en el directorio [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples): prueba de capacidades `m5-capabilities.java`, portapapeles `capability-set-2-clipboard.java`, argumentos de script `script-args.java`, paquete multiarchivo `multi-file-source-package/`, entrada no predeterminada `arbitrary-entry.java`, etc., cada uno con evidencia registrada en dispositivo.

******

### Límites

******

Para garantizar un comportamiento seguro y predecible, la versión actual mantiene deliberadamente los siguientes límites:

- El código Java de archivo único tiene un tope de 4 MiB; un paquete multiarchivo debe ser un archivo canónico de 2 a 32 ficheros `.java` cuyas rutas coincidan exactamente con sus declaraciones `package`, compartiendo el mismo tope total de 4 MiB. No se admiten archivos class, JAR ni entradas DEX.
- La clase de entrada debe ser una clase concreta pública que implemente `AutoJsJvmEntry` (Entry API 4) con un constructor público sin argumentos; la salida compilada debe contener exactamente una implementación concreta.
- El nivel de lenguaje está fijado en Java 8 (ECJ `-source 8 -target 8`); el procesamiento de anotaciones está desactivado; el código debe ser UTF-8 estricto y los escapes Unicode `\uXXXX` están prohibidos en todas partes, incluidos comentarios y cadenas.
- Las expresiones lambda aún no forman parte del perfil ejecutable (los stubs de compilación API 24 carecen de `java.lang.invoke`) — use clases anónimas; no se resuelven dependencias Maven ni de terceros.
- El tiempo límite de sesión es de 30 segundos por defecto, cubriendo compilación y ejecución, con un tope duro de 120 segundos; solo puede haber una sesión activa a la vez — una segunda sesión recibe un `BUSY` reintentable.
- El proceso worker es de un solo uso y se retira tras cada ejecución; los hilos en segundo plano no sobreviven al retorno de `run`.

******

### Bibliotecas de script

******

La superficie de API disponible para compilar y ejecutar scripts forma una lista blanca exactamente fijada:

#### Disponible

- Framework de Android: los símbolos de compilación provienen de stubs class-only de API 24; el comportamiento en ejecución sigue dependiendo de la versión del sistema del dispositivo.
- AutoJs6 JVM Entry API 4: `JvmScriptContext` es el único puente de host admitido (consola / lanzamiento de aplicaciones / sleep / toast / portapapeles / instantánea de argumentos / vistas de cancelación).
- Desugaring controlado de la core library: `java.time` (la superficie de API 26) más `Stream.iterate` de tres argumentos y `toList` mejorados, ejecutables en dispositivos API 24.
- Los valores de retorno y los argumentos siguen un perfil JSON: `null` / booleanos / números / cadenas / `Map` con claves String / `Iterable` / arrays; los valores de retorno tienen un tope de 64 KiB con 64 niveles de anidamiento.

#### No disponible

- La biblioteca completa del JDK 8 de escritorio y las API de Android por encima de API 24: `Stream.ofNullable` / `takeWhile` / `dropWhile` / `mapMulti*`, el `java.nio.file` completo y similares fallan directamente en compilación.
- El soporte en ejecución de expresiones lambda y referencias a métodos (`java.lang.invoke` no está en los stubs de API 24); los POJO, records y enums no pueden usarse como valores de retorno.
- Procesadores de anotaciones, dependencias Maven transitivas y JAR de terceros.

******

### Seguridad y aislamiento

******

El plugin está diseñado con denegación por defecto; las siguientes restricciones siempre están en vigor:

- El compilador y el worker se ejecutan en procesos separados y nunca entran en el proceso de AutoJs6; los servicios solo aceptan llamadas de un host con la misma firma.
- Cada capacidad del host se autoriza por petición, con la lectura y la escritura del portapapeles como dos concesiones independientes; las llamadas no autorizadas se rechazan mediante listas blancas de ambos lados antes del despacho.
- El código, los artefactos, la salida, los diagnósticos y los valores de retorno tienen topes duros — superar uno hace fallar la ejecución en lugar de truncar en silencio; los DEX pasan una validación estructural completa y reglas de publicación de solo lectura antes de ejecutarse.
- Los errores externos solo llevan códigos estables, fases de fallo y texto saneado; las excepciones de ejecución exponen solo nombres de clase `java.*`/`javax.*` o la `UserException` unificada — los mensajes y las pilas nunca salen de la frontera del proceso.
- La caché de compilación está autenticada y su clave nunca sale del proceso del compilador; cualquier cambio de versión de las herramientas invalida automáticamente todas las cachés anteriores.

******

### Historial de versiones

******

# v0.8.0

###### 2026/09/12

* `Mejora` Reorganizar el README y el historial de versiones en torno al uso, los ejemplos y los límites de capacidades, con generación coherente para diez idiomas a partir de fuentes JSON
* `Mejora` La verificación de compilación rechaza dependencias nativas accidentales y genera un informe JSON

# v0.8.0-m9

###### 2026/08/27

* `Nota` El tiempo límite de sesión se mantiene en 30 segundos por defecto / tope duro de 120 segundos, y la concurrencia se mantiene en una sesión activa (una segunda sesión recibe un `BUSY` reintentable) — ambas evaluaciones de relajación concluyeron no proceder por ahora
* `Nota` Evaluación de actualización de ECJ: las versiones recientes (3.42/3.46) no pueden ejecutarse en Android, así que la línea publicada sigue fijada en ECJ 3.26.0 y Java 8
* `Novedad` Añadidos los paquetes de código multiarchivo (Protocol 1.6): una petición puede enviar un archivo canónico de 2 a 32 ficheros `.java` cuyas rutas deben coincidir exactamente con sus declaraciones `package`; el cruce de rutas, las entradas comprimidas, los enlaces simbólicos y los duplicados se rechazan todos
* `Novedad` Apertura de extremo a extremo de nombres de clase de entrada arbitrarios: los llamadores pueden elegir explícitamente un nombre simple ASCII (el predeterminado sigue siendo `Main`), con el nuevo ejemplo de entrada no predeterminada `arbitrary-entry.java`
* `Mejora` Confirmado que el soporte Kotlin/JVM queda en el plugin hermano Kotlin Runtime; los dos repositorios comparten el protocolo congelado y las pruebas de conformidad, sin dependencia de ejecución
* `Dependencia` Actualizado el D8/R8 de ejecución de 8.13.17 a 8.13.23; las claves de la caché de compilación se invalidan automáticamente con la versión de las herramientas

# v0.7.0-m9

###### 2026/08/26

* `Nota` Las observaciones nunca contienen código, argumentos, rutas ni identidades de procesos; los builds release no exportan contadores de caché acumulativos
* `Novedad` Cada ejecución termina ahora con un resumen de observación acotado (Protocol 1.5): tiempos de las fases de compilación/ejecución/limpieza, resultado de la caché por petición, arranques en frío/calor y hasta seis muestras de recursos, mostrados uniformemente por la consola de AutoJs6

##### Para más historial, consulte

* [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.changelog/CHANGELOG-es.md)

******

### Compilación

******

El repositorio incluye AAR de protocolo congelados (`protocol/`) y se compila sin conexión sin checkout de AutoJs6. Se recomienda JDK 21; el SDK de Android debe proporcionar las platforms 24 / 26 / 30 / 34 / 36 (las inferiores alimentan los stubs de compilación controlados). Build debug:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Build release:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

Los parámetros de build se centralizan en `version.properties`: versión actual 0.8.0-m9 (build 8), minSdk 24, targetSdk 36. Antes de confirmar cambios, ejecute los scripts de control `scripts/verify.ps1` / `scripts/verify.sh` (pruebas unitarias Debug/Release + lint + APK debug, todo sin conexión).

Los APK release/debug deben firmarse con el mismo certificado que AutoJs6 para que el host los acepte; el material de firma local reside en `sign.properties` y `app/sm003.jks`, ignorados por el control de versiones.

******

### Estructura de recursos

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` localiza el nombre y la descripción del plugin; README y CHANGELOG se generan con `.python/generate_markdown.py` a partir de las fuentes JSON. Para modificar la documentación, edite las fuentes JSON en lugar del Markdown generado.

******

### Enlaces

******

- Documentación de AutoJs6: https://docs.autojs6.com
- Página del proyecto AutoJs6: https://github.com/SuperMonster003/AutoJs6
- Plugin hermano Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Proyecto Eclipse JDT Core (ECJ): https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API y límites de ejecución (métodos/topes/códigos de error): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- Directorio de ejemplos: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- Hoja de ruta (con registros de verificación por hito): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- Avisos de terceros: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
