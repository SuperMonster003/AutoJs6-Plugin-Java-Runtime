******

### Historique des versions

******

# v0.8.4

###### 2026/10/04

* `Amélioration` Les icônes de l'application et du centre de plugins utilisent les images fournies par le responsable, avec leurs couleurs et proportions, des marges transparentes et une taille optique commune pour afficher toute la silhouette, avec réglage et génération dans Icon Studio
* `Amélioration` Les icônes du centre de plugins utilisent les tailles, positions, images claires et sombres et fonds circulaires réglés dans Icon Studio, avec les sources et paramètres permettant de les reproduire

# v0.8.3

###### 2026/09/19

* `Correction` Le nettoyage des ressources et la sortie du worker simultanés ne peuvent plus finaliser deux fois la même session ni envoyer le rappel final avant que les observations soient prêtes, évitant les délais dépassés intermittents de l'hôte
* `Correction` Avertissements de lecture SDK XML v4 avec AGP 9.1 et contrôles d'alignement natif des APK déclenchés par erreur lors de l'assemblage des tests unitaires JVM, avec les plugins de compilation partagés 1.8.3

# v0.8.2

###### 2026/09/15

* `Amélioration` compileSdk et targetSdk passent à 37 (Android 17) ; le comportement du plugin ne dépend pas de la nouvelle cible

# v0.8.1

###### 2026/09/13

* `Correction` Le centre des extensions peut activer un fournisseur nouvellement installé via une entrée protégée; les métadonnées suivent le paquet installé
* `Amélioration` Harmonisation de l'activation, des métadonnées, de la documentation traduite et de la collecte des APK signés

# v0.8.0

###### 2026/09/12

* `Amélioration` Réorganiser le README et l'historique des versions autour de l'utilisation, des exemples et des limites des capacités, avec une génération cohérente en dix langues à partir de sources JSON
* `Amélioration` La vérification de compilation rejette les dépendances natives involontaires et produit un rapport JSON

# v0.8.0-m9

###### 2026/08/27

* `Note` Le délai de session reste à 30 secondes par défaut / plafond dur de 120 secondes, et la concurrence reste à une session active (une deuxième session reçoit un `BUSY` réessayable) - les deux évaluations d'assouplissement ont conclu de ne pas procéder pour l'instant
* `Note` Évaluation de la mise à niveau d'ECJ : les versions récentes (3.42/3.46) ne peuvent pas s'exécuter sur Android, la ligne publiée reste donc épinglée à ECJ 3.26.0 et Java 8
* `Nouveauté` Ajout des paquets source multi-fichiers (Protocol 1.6) : une requête peut soumettre une archive canonique de 2 à 32 fichiers `.java` dont les chemins doivent correspondre exactement aux déclarations `package` ; traversée de chemins, entrées compressées, liens symboliques et doublons sont tous rejetés
* `Nouveauté` Ouverture de bout en bout des noms de classe d'entrée arbitraires : les appelants peuvent choisir explicitement un nom simple ASCII (le défaut reste `Main`), avec le nouvel exemple d'entrée non défaut `arbitrary-entry.java`
* `Amélioration` Confirmation que le support Kotlin/JVM reste porté par le plugin jumeau Kotlin Runtime ; les deux dépôts partagent le protocole gelé et les tests de conformité, sans dépendance d'exécution
* `Dépendance` Mise à niveau du D8/R8 d'exécution de 8.13.17 à 8.13.23 ; les clés du cache de compilation s'invalident automatiquement avec la version de la chaîne d'outils

# v0.7.0-m9

###### 2026/08/26

* `Note` Les observations ne contiennent jamais source, arguments, chemins ni identités de processus ; les builds release n'exportent aucun compteur de cache cumulatif
* `Nouveauté` Chaque exécution se termine désormais par un résumé d'observation borné (Protocol 1.5) : durées des phases de compilation/exécution/nettoyage, résultat du cache par requête, démarrages à froid/chaud et jusqu'à six échantillons de ressources, affichés uniformément par la console AutoJs6

# v0.6.0-m9

###### 2026/08/26

* `Nouveauté` Les exceptions d'exécution exposent désormais un nom de classe assaini avec la ligne source (Protocol 1.4) : les noms `java.*`/`javax.*` restent tels quels tandis que toute autre classe s'affiche comme `UserException` ; messages et piles d'exception ne franchissent jamais la frontière du processus
* `Nouveauté` Ajout de l'exemple d'exception d'exécution `runtime-exception.java` démontrant la forme visible « classe + ligne »

# v0.5.0-m9

###### 2026/08/25

* `Note` Les arguments n'acceptent que des valeurs du profil JSON (null / booléens / nombres / chaînes / Map à clés String / Iterable / tableaux) ; passer des POJO ou des objets Android produit l'erreur stable `JVM_SOURCE_INVALID_ARGUMENTS`
* `Nouveauté` Ajout des arguments de script (Protocol 1.3 / Entry API 4) : `context.args()` renvoie un instantané en lecture seule des arguments de la configuration d'exécution de l'hôte (plafond 64 KiB, 64 niveaux d'imbrication) ; les scripts sans arguments existants continuent de fonctionner inchangés
* `Nouveauté` Ajout de l'exemple d'arguments de script `script-args.java` lisant Maps/Lists imbriqués, booléens, nombres, chaînes et null

# v0.4.0-m9

###### 2026/08/25

* `Note` Sur Android 10 et ultérieur, AutoJs6 doit être au premier plan (avec une Activity resumed) pour les appels presse-papiers ; les appels en arrière-plan échouent de façon fiable avant que le presse-papiers système ne soit touché
* `Nouveauté` Ajout des capacités presse-papiers (Protocol 1.2 / Entry API 3) : `clipboard().getText/setText`, lecture et écriture étant deux autorisations indépendantes, avec un plafond de texte de 16 KiB
* `Nouveauté` Ajout des artefacts multi-DEX : 1 à 4 fichiers `classesN.dex` contigus dans 32 MiB au total ; un exemple à 65 536 références de méthodes passe sur appareils API 24/36 (API 26 reste en DEX unique à cause d'une limite de plateforme)
* `Nouveauté` Ajout du desugaring contrôlé de la core library : `java.time` et `Stream.iterate` à trois arguments / `toList` améliorés fonctionnent dès l'API 24 ; les méthodes non prises en charge échouent à la compilation au lieu de faire planter les anciens appareils
* `Nouveauté` Ajout du guide chinois « Java Context API et limites d'exécution » : chaque méthode, la table des types de retour, les plafonds de ressources et la sémantique des codes d'erreur
* `Nouveauté` Ajout de la matrice d'exemples vérifiés sur appareil (annulation, familles de valeurs de retour, erreurs de compilation, dépassement de résultat), plus les scripts de contrôle hors ligne `scripts/verify.ps1` / `verify.sh`
* `Correction` L'assainissement des diagnostics remplace désormais les segments sensibles sur place et conserve le reste du message du compilateur, au lieu de dégrader tout le message en texte fixe
* `Correction` La voie d'opérations du cache de compilation récupère désormais une fois après refroidissement suite à un délai dépassé, au lieu de rester hors service jusqu'au redémarrage du processus
* `Amélioration` Les multiples problèmes ECJ d'une même compilation sont transmis un par un dans l'ordre source (budget diagnostique partagé de 64 KiB)
* `Amélioration` Les builds debug gagnent un canal d'observation JSONL privé (cinq durées de phase / démarrage à froid du worker / compteurs de cache) ; les builds release gardent zéro export, avec assertions de test
* `Amélioration` Achèvement de la ligne de base de performance API 24/36 en chemin de production et de l'évaluation du préchauffage du worker (le chemin sériel reste) ; les noms de classe d'entrée arbitraires deviennent prêts côté plugin
* `Amélioration` La persistance du cache entre processus a été rejetée après revue de sécurité ; la clé du cache reste valide uniquement dans le processus du compilateur

# v0.3.0-m5

###### 2026/08/25

* `Note` Première version jalon utilisable (couvrant M1-M5) ; vérification d'hôte de même signature, autorisation par capacité, processus worker jetables et validation fail-closed actifs dès le départ
* `Nouveauté` Plugin autonome de compilation/exécution de source Java établi : ECJ 3.26.0 (Java 8) et D8 8.13.17 embarqués, compilation et exécution dans des processus séparés
* `Nouveauté` Implémentation du profil source mono-fichier Protocol 1.1 : analyse de la classe d'entrée, diagnostics assainis et cartographie complète des phases d'échec
* `Nouveauté` Implémentation de quatre capacités hôte autorisées individuellement : lancement d'application, flux console, sleep et toast
* `Nouveauté` Implémentation du cache de compilation authentifié avec télémétrie locale
* `Correction` Correction de deux avertissements lint d'exigence d'API ; les exigences API 26 se propagent désormais aux appelants via `@RequiresApi`
* `Amélioration` Migration du build vers les plugins de convention platform-versions partagés, coordonnées de dépendances centralisées dans le catalogue de versions tout en gardant les assertions littérales à double source
