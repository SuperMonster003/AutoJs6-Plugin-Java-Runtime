<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Plugin de compilation et d'exécution de source Java 8 (mono-fichier / paquet source multi-fichiers) pour AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Langues (Languages)

******

Le README.md actuel prend en charge les langues suivantes:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-en.md)
- Français [fr] # actuel
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ar.md)

******

### Présentation

******

Le plugin AutoJs6 Java Runtime permet à AutoJs6 de compiler et d'exécuter directement du code source Java (`.java`), sous forme d'un fichier unique ou d'un paquet source borné de 2 à 32 fichiers. Il embarque le compilateur Eclipse ECJ 3.26.0 (niveau de langage Java 8) et le convertisseur de bytecode D8 8.13.23, et exécute le résultat compilé dans un processus worker jetable ; ni le compilateur ni le script ne s'exécutent jamais dans le processus AutoJs6.

Ce plugin et [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) sont des plugins jumeaux : ils peuvent être installés côte à côte, chacun servant respectivement le source Java / Kotlin, et AutoJs6 mémorise le composant de compilation choisi pour chaque langage.

******

### Fonctionnalités

******

- Fournit le service de compilation/exécution `org.autojs.plugin.JVM_SOURCE` et le service de découverte `org.autojs.plugin.INFO` pour le centre de plugins, tous deux protégés par signature et exécutés dans des processus auxiliaires séparés.
- Embarque le compilateur Eclipse ECJ 3.26.0 (niveau de langage Java 8) ; la sortie est convertie en DEX par D8 8.13.23 avant exécution, en 1 à 4 fichiers `classesN.dex` contigus (32 MiB au total ; les appareils API 26 restent en DEX unique).
- Prend en charge six ponts de capacités hôte autorisés individuellement : sortie console en direct `console().log/error`, lancement d'application `app().launch`, `sleep` interruptible, messages `toast`, plus la lecture `clipboard().getText` et l'écriture `clipboard().setText` du presse-papiers, accordées indépendamment.
- Prend en charge les arguments de script de l'hôte : `context.args()` renvoie un instantané JSON en lecture seule des arguments de la configuration d'exécution, sans autorisation supplémentaire ; les scripts sans arguments existants fonctionnent inchangés.
- Accepte un fichier source unique ou un paquet source canonique de 2 à 32 fichiers ; le nom simple de la classe d'entrée peut être choisi explicitement par l'appelant et vaut `Main` par défaut.
- Desugaring contrôlé de la core library : `java.time` et certaines méthodes Stream améliorées fonctionnent dès l'API 24 ; les méthodes non prises en charge échouent à la compilation au lieu de faire planter les anciens appareils à l'exécution.
- Cache de compilation authentifié : réexécuter un source identique touche le cache et saute la compilation ; tout changement de version de la chaîne d'outils invalide automatiquement tous les caches précédents.
- Les erreurs de compilation sont transmises une par une dans l'ordre source d'ECJ, avec noms de fichiers sûrs et positions ligne/colonne ; chaque exécution se termine par un résumé d'observation borné : durées par phase, résultat du cache et échantillons de ressources.
- README et CHANGELOG sont disponibles en dix langues : chinois simplifié, chinois traditionnel (HK/TW), anglais, français, espagnol, japonais, coréen, russe et arabe.

******

### Démarrage rapide

******

- **Installer** — Téléchargez l'APK depuis [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) et installez-le, ou compilez localement comme décrit dans la section Compilation ci-dessous. Attention : le plugin doit être signé avec le même certificat qu'AutoJs6, et le code de version de l'hôte AutoJs6 doit être d'au moins 5281.
- **Activer** — L'exécution de source JVM est actuellement une fonctionnalité expérimentale d'AutoJs6 : activez l'interrupteur expérimental dans l'hôte, puis sélectionnez explicitement ce plugin comme composant de compilation pour le langage Java. Sinon, l'exécution signale respectivement les codes d'erreur stables `JVM_SOURCE_EXPERIMENT_DISABLED` ou `JVM_SOURCE_PROVIDER_NOT_SELECTED` ; si plusieurs composants de compilation Java sont activés à la fois, AutoJs6 demande aussi une sélection explicite au préalable.
- **Exécuter** — Créez un fichier `.java` dans l'éditeur AutoJs6, écrivez une classe d'entrée implémentant l'interface `AutoJsJvmEntry`, puis lancez l'exécution (voir l'exemple ci-dessous). Le nom physique du fichier est libre ; le nom simple d'entrée vaut `Main` par défaut, et les appelants de script peuvent choisir un autre nom simple ASCII via l'API d'entrée explicite. La déclaration `package` est optionnelle — tout package ASCII légal convient.
- **Dépanner** — En cas d'échec de compilation, la console affiche les diagnostics ECJ avec noms de fichiers sûrs et positions ligne/colonne, et les erreurs multiples sont transmises une par une ; les échecs d'exécution exposent un code d'erreur stable et la phase d'échec (comme `SOURCE_TOO_LARGE`, `TIMEOUT`, `BUSY`), et les exceptions d'exécution n'affichent qu'un nom de classe assaini avec la ligne source. Les sorties attendues de chaque exemple sont dans le [guide des exemples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md) ; la table complète des codes d'erreur figure dans le [guide Java Context API](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md).

******

### Exemple d'utilisation

******

Un exemple minimal prêt à l'emploi démontrant quatre capacités de base : sortie console, toast, sleep interruptible et lancement d'application:

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

`console().log/error` est diffusé ligne par ligne pendant l'exécution du script ; `sleep` est rapidement interrompu par une action d'arrêt. Plus d'exemples dans le répertoire [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples) : test de capacités `m5-capabilities.java`, presse-papiers `capability-set-2-clipboard.java`, arguments de script `script-args.java`, paquet source multi-fichiers `multi-file-source-package/`, entrée non défaut `arbitrary-entry.java`, etc., chacun avec des preuves enregistrées sur appareil.

******

### Limites

******

Pour garantir un comportement sûr et prévisible, la version actuelle maintient délibérément les limites suivantes:

- Le source Java mono-fichier est plafonné à 4 MiB ; un paquet source multi-fichiers doit être une archive canonique de 2 à 32 fichiers `.java` dont les chemins correspondent exactement aux déclarations `package`, partageant le même plafond total de 4 MiB. Les fichiers class, JAR et entrées DEX ne sont pas pris en charge.
- La classe d'entrée doit être une classe concrète publique implémentant `AutoJsJvmEntry` (Entry API 4) avec un constructeur public sans argument ; la sortie compilée doit contenir exactement une implémentation concrète.
- Le niveau de langage est fixé à Java 8 (ECJ `-source 8 -target 8`) ; le traitement d'annotations est désactivé ; le source doit être en UTF-8 strict et les échappements Unicode `\uXXXX` sont interdits partout, y compris commentaires et chaînes.
- Les expressions lambda ne font pas encore partie du profil exécutable (les stubs de compilation API 24 n'ont pas `java.lang.invoke`) — utilisez des classes anonymes ; aucune dépendance Maven ou tierce n'est résolue.
- Le délai de session vaut 30 secondes par défaut, compilation et exécution comprises, avec un plafond dur de 120 secondes ; une seule session active à la fois — une deuxième session reçoit un `BUSY` réessayable.
- Le processus worker est à usage unique et se retire après chaque exécution ; les threads d'arrière-plan ne survivent pas au retour de `run`.

******

### Bibliothèques de script

******

La surface d'API disponible pour la compilation et l'exécution des scripts forme une liste blanche exactement verrouillée:

#### Disponible

- Framework Android : les symboles de compilation proviennent de stubs class-only API 24 ; le comportement à l'exécution dépend toujours de la version du système de l'appareil.
- AutoJs6 JVM Entry API 4 : `JvmScriptContext` est le seul pont hôte pris en charge (console / lancement d'application / sleep / toast / presse-papiers / instantané d'arguments / vues d'annulation).
- Desugaring contrôlé de la core library : `java.time` (surface API 26) plus `Stream.iterate` à trois arguments et `toList` améliorés, exécutables sur les appareils API 24.
- Les valeurs de retour et les arguments suivent un profil JSON : `null` / booléens / nombres / chaînes / `Map` à clés String / `Iterable` / tableaux ; les valeurs de retour sont plafonnées à 64 KiB avec 64 niveaux d'imbrication.

#### Indisponible

- La bibliothèque JDK 8 de bureau complète et les API Android au-dessus de l'API 24 : `Stream.ofNullable` / `takeWhile` / `dropWhile` / `mapMulti*`, le `java.nio.file` complet et similaires échouent directement à la compilation.
- Le support à l'exécution des expressions lambda et références de méthodes (`java.lang.invoke` absent des stubs API 24) ; les POJO, records et enums ne peuvent pas servir de valeurs de retour.
- Processeurs d'annotations, dépendances Maven transitives et JAR tiers.

******

### Sécurité et isolation

******

Le plugin est conçu en refus par défaut ; les restrictions suivantes sont toujours en vigueur:

- Le compilateur et le worker s'exécutent dans des processus séparés et n'entrent jamais dans le processus AutoJs6 ; les services n'acceptent que les appels d'un hôte de même signature.
- Chaque capacité hôte est autorisée par requête, la lecture et l'écriture du presse-papiers étant deux autorisations indépendantes ; les appels non autorisés sont rejetés par des listes blanches des deux côtés avant dispatch.
- Source, artefacts, sorties, diagnostics et valeurs de retour ont tous des plafonds durs — en dépasser un fait échouer l'exécution au lieu de tronquer silencieusement ; les DEX passent une validation structurelle complète et des règles de publication en lecture seule avant exécution.
- Les erreurs externes ne portent que des codes stables, des phases d'échec et un texte assaini ; les exceptions d'exécution n'exposent que les noms de classes `java.*`/`javax.*` ou l'`UserException` unifiée — messages et piles d'exception ne franchissent jamais la frontière du processus.
- Le cache de compilation est authentifié et sa clé ne quitte jamais le processus du compilateur ; tout changement de version de la chaîne d'outils invalide automatiquement tous les caches précédents.

******

### Historique des versions

******

# v0.8.4

###### 2026/10/04

* `Amélioration` Les icônes de l'application et du centre de plugins utilisent les images fournies par le responsable, avec leurs couleurs et proportions, des marges transparentes et une taille optique commune pour afficher toute la silhouette, avec réglage et génération dans Icon Studio

# v0.8.3

###### 2026/09/19

* `Correction` Le nettoyage des ressources et la sortie du worker simultanés ne peuvent plus finaliser deux fois la même session ni envoyer le rappel final avant que les observations soient prêtes, évitant les délais dépassés intermittents de l'hôte
* `Correction` Avertissements de lecture SDK XML v4 avec AGP 9.1 et contrôles d'alignement natif des APK déclenchés par erreur lors de l'assemblage des tests unitaires JVM, avec les plugins de compilation partagés 1.8.3

# v0.8.2

###### 2026/09/15

* `Amélioration` compileSdk et targetSdk passent à 37 (Android 17) ; le comportement du plugin ne dépend pas de la nouvelle cible

##### Pour plus d'historique, voir

* [CHANGELOG-fr.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-fr.md)

******

### Compilation

******

Le dépôt inclut des AAR de protocole gelés (`protocol/`) et se compile hors ligne sans checkout d'AutoJs6. JDK 21 recommandé ; le SDK Android doit fournir les platforms 24 / 26 / 30 / 34 / 36 (les platforms basses alimentent les stubs de compilation contrôlés). Build debug:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Build release:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

Les paramètres de build sont centralisés dans `version.properties` : version actuelle 0.8.4-m9 (build 63), minSdk 24, targetSdk 37. Avant de committer, lancez les scripts de contrôle `scripts/verify.ps1` / `scripts/verify.sh` (tests unitaires Debug/Release + lint + APK debug, entièrement hors ligne).

Les APK release/debug doivent être signés avec le même certificat qu'AutoJs6 pour être acceptés par l'hôte ; le matériel de signature local réside dans `sign.properties` et `app/sm003.jks`, ignorés par le contrôle de version.

******

### Organisation des ressources

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` localise le nom et la description du plugin ; README et CHANGELOG sont générés par `.python/generate_markdown.py` à partir des sources JSON. Pour modifier la documentation, éditez les sources JSON plutôt que le Markdown généré.

Le [dessin original](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.python/icons/java-runtime.svg) est fourni par le responsable du projet. `.icons/recipe.json` conserve les réglages; utilisez `python .python/generate_icon_studio.py --check` pour vérifier la recette et les ressources.

******

### Liens

******

- Documentation AutoJs6: https://docs.autojs6.com
- Page du projet AutoJs6: https://github.com/SuperMonster003/AutoJs6
- Plugin jumeau Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Projet Eclipse JDT Core (ECJ): https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API et limites d'exécution (méthodes/plafonds/codes d'erreur): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- Répertoire d'exemples: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- Feuille de route (avec les registres de vérification par jalon): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- Mentions tierces: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
