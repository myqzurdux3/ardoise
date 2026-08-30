# Ardoise — Design

**Date:** 2026-08-30
**Statut:** validé

## Problème

Sur Android, aucune application n'affiche une liste de tâches Google Tasks
en permanence sur l'écran de verrouillage. Les widgets d'écran de
verrouillage de Google sont bridés en taille, en fréquence de
rafraîchissement et en placement. Les applications de notes persistantes
n'ont pas de synchronisation. Les applications de tâches qui ont une
notification persistante (TickTick) ne se synchronisent pas avec Google
Tasks.

## Intuition centrale

Sur Android, la surface réellement « toujours visible » de l'écran de
verrouillage n'est pas le widget — c'est **la notification**, et
accessoirement **le fond d'écran**.

Une notification `ongoing` en `BigTextStyle` affiche six à huit lignes de
texte, accepte des boutons d'action, n'est pas soumise au throttling des
widgets, et survit au redémarrage. Le fond d'écran de verrouillage, lui,
est un `Bitmap` que l'application peut redessiner à volonté
(`WallpaperManager.setBitmap(…, FLAG_LOCK)`), ce qui donne un contrôle
typographique total.

Ardoise exploite ces deux surfaces à partir d'une source unique.

## Périmètre

**Dans le périmètre**

- Lecture seule des tâches Google Tasks (listes, titres, échéances, statut).
- Une action d'écriture : cocher une tâche comme terminée.
- Rendu notification permanente.
- Rendu fond d'écran de verrouillage.
- Écran de configuration : choix de la liste, nombre de tâches, surfaces
  actives, thème.

**Hors périmètre (YAGNI)**

- Création et édition de tâches (l'application Google Tasks le fait déjà).
- Sous-tâches, notes, pièces jointes.
- Multi-comptes.
- Widget d'écran d'accueil.
- Thèmes personnalisés au-delà de clair / sombre / suivi du système.

## Architecture

Trois couches, une dépendance descendante stricte.

```
  UI (Compose)            SettingsScreen, OnboardingScreen
        │
  Domain                  TaskRepository, RenderSnapshot
        │
  Data                    TasksApi (REST), AuthProvider, SnapshotStore
```

Les deux moteurs de rendu (notification, fond d'écran) consomment le même
`RenderSnapshot` et ne connaissent ni le réseau ni l'authentification.

### Authentification

`AuthorizationClient` de Google Identity Services
(`com.google.android.gms:play-services-auth`), avec le scope
`https://www.googleapis.com/auth/tasks`.

`authorize()` renvoie directement un jeton d'accès lorsque l'utilisateur a
déjà consenti, et un `PendingIntent` de consentement sinon. Conséquence
importante : **l'application ne stocke aucun jeton et n'a pas besoin de
secret client**. Pas de serveur, pas de rafraîchissement à gérer, pas de
secret dans le dépôt.

Chaque synchronisation demande un jeton frais avant l'appel réseau.

### Accès aux données

L'API Google Tasks est du REST simple. La bibliothèque officielle
`google-api-services-tasks` est lourde et pénible sur Android ; on
l'évite. Deux points d'accès suffisent :

| Usage | Appel |
|---|---|
| Lister les listes | `GET /tasks/v1/users/@me/lists` |
| Lister les tâches | `GET /tasks/v1/lists/{id}/tasks?showCompleted=false` |
| Cocher une tâche | `PATCH /tasks/v1/lists/{id}/tasks/{taskId}` avec `{"status":"completed"}` |

OkHttp + kotlinx.serialization. Environ 150 lignes.

### Synchronisation

L'API Google Tasks n'offre ni webhook ni push. **Le polling est
obligatoire** — c'est la contrainte principale du projet.

`WorkManager`, tâche périodique, intervalle de 30 minutes par défaut
(configurable : 15 / 30 / 60), contrainte réseau `CONNECTED`. Une
synchronisation immédiate est également déclenchée à l'ouverture de
l'application et après avoir coché une tâche.

Les actions déclenchées depuis l'écran de verrouillage passent par le même
worker plutôt que par le `BroadcastReceiver`. Un receiver ne dispose que d'une
dizaine de secondes, même avec `goAsync()` ; un appel à Google sur une
connexion mobile médiocre peut dépasser ce délai, et c'est précisément dans ces
conditions que l'utilisateur appuie sur le bouton. Le receiver se contente
d'empiler le travail.

### Persistance

Pas de base de données. Le cache est un unique document JSON
(`RenderSnapshot`) écrit via DataStore. Room serait de l'ingénierie
excessive pour une cinquantaine de lignes de texte.

Le snapshot permet aux deux moteurs de rendu de fonctionner hors ligne et
au redémarrage, avant la première synchronisation.

### Rendu — notification

Canal `ardoise_tasks_visible`, importance **`DEFAULT`**, son coupé au niveau
du canal. `ongoing = true`, `showWhen = false`, catégorie `REMINDER`,
visibilité `PUBLIC`.

> **Corrigé après essai sur appareil.** `IMPORTANCE_LOW` était le choix
> initial, et il annulait la fonctionnalité : Android range `LOW` parmi les
> notifications silencieuses et les réduit à une pastille sur l'écran de
> verrouillage, sans aucun texte. `DEFAULT` avec `setSound(null, null)` donne
> le même silence tout en gardant la notification visible et dépliable.

`BigTextStyle` : une tâche par ligne, préfixée d'une puce. Les tâches en
retard sont marquées. Deux actions : « Terminer » (la première tâche) et
« Actualiser ».

Sur l'écran de verrouillage, la notification s'affiche **repliée** jusqu'à ce
que l'utilisateur la déplie. La ligne repliée porte donc la prochaine tâche,
pas un simple compteur ; le décompte passe en `subText`, dans la ligne
d'en-tête.

Reprogrammée au démarrage via un `BroadcastReceiver` sur
`BOOT_COMPLETED`.

### Rendu — fond d'écran

`Canvas` → `Bitmap` → `WallpaperManager.setBitmap(bitmap, null, true,
FLAG_LOCK)`.

Le rendu réserve le tiers supérieur de l'écran (horloge système) et
compose la liste en dessous. Palette ardoise : fond charbon, texte craie,
accent ocre pour les échéances dépassées.

> **Corrigé après essai sur appareil.** Le système réclame un fond d'écran de
> 4848 × 2424 pour un écran de 1080 × 2424 — la largeur est doublée pour le
> parallaxe de l'écran d'accueil. Sans indication, un bitmap à la taille de
> l'écran s'y fait redimensionner et la composition casse. `setBitmap` reçoit
> donc un `visibleCropHint` couvrant tout le bitmap. Le pied de page a par
> ailleurs été remonté à 90 % de la hauteur : à 94,5 % il passait sous la
> barre de gestes.

Deux garde-fous :

- Le fond d'écran n'est réécrit que si le contenu a changé — redessiner à
  chaque synchronisation provoque un scintillement au déverrouillage.
- La surface est désactivée par défaut ; l'utilisateur l'active
  explicitement, après avoir été prévenu qu'elle remplace son fond d'écran
  de verrouillage.

## Contraintes à assumer

0. **L'enregistrement OAuth est incontournable.** Vérifié sur appareil : les
   deux voies d'authentification de Google le réclament.

   | Voie | Résultat |
   |---|---|
   | `AuthorizationClient` (Google Identity Services) | `ApiException 8` — `UNREGISTERED_ON_API_CONSOLE` |
   | `GoogleAuthUtil.getToken` (gestionnaire de comptes, antérieur à GIS) | `GoogleAuthException: UnregisteredOnApiConsole` |

   L'ancienne voie ne demandait historiquement aucun identifiant client ; ce
   n'est plus vrai. Une implémentation de repli complète a été écrite, essayée
   sur un Pixel 9a avec un compte Google réel, puis retirée — elle échoue à la
   même cause.

   Conséquence : **aucune version distribuable ne peut fonctionner clé en
   main.** Chaque personne qui installe Ardoise doit enregistrer son propre
   identifiant OAuth pour son couple package + clé de signature. C'est une
   contrainte de Google, pas un défaut de conception, et c'est ce qui justifie
   l'écran « Configuration requise » affichant ces deux valeurs.

1. **Vérification OAuth Google.** Le scope `tasks` est sensible. Publier
   sur le Play Store imposerait une vérification. En usage personnel,
   l'application reste en mode « test » sur la console Google Cloud et
   l'utilisateur s'auto-autorise. Aucune friction tant qu'on ne publie
   pas.
2. **Réglage système requis.** *Notifications sur l'écran de verrouillage
   → afficher tout le contenu*, sans quoi le texte est masqué.
   L'onboarding détecte le réglage et guide l'utilisateur.
3. **Latence de synchronisation.** Jusqu'à 30 minutes entre une
   modification faite ailleurs et son affichage. Inhérent au polling.
4. **`setBitmap` sur FLAG_LOCK** n'est pas supporté par tous les
   constructeurs. Détecté à l'exécution, la surface se désactive
   proprement si l'appel échoue.

## Tests

- `TasksApi` : réponses JSON figées, désérialisation et gestion d'erreur.
- `RenderSnapshot` : tri, filtrage des tâches terminées, calcul du retard.
- Rendu notification : construction sans lancement, vérification du texte.
- Rendu fond d'écran : génération du Bitmap en test unitaire Robolectric,
  comparaison de dimensions et non-vacuité.

Le chemin authentification et l'écriture effective du fond d'écran sont
vérifiés manuellement — ils dépendent des services Google Play et du
système.

## Identité

**Ardoise.** Une surface sombre où l'on écrit en clair, qu'on essuie et
qu'on réécrit. C'est la description exacte d'un écran de verrouillage.

- Typographie : sans-serif géométrique, graisse moyenne.
- Palette : charbon `#1C1E21`, craie `#F2EFE9`, ocre `#C9884A`.
- Logo : une ardoise arrondie, une coche tracée à la craie.
