<p align="center">
  <img src="art/banner.svg" alt="Ardoise" width="100%">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-10%2B-1C1E21?style=flat-square&labelColor=C9884A" alt="Android 10+">
  <img src="https://img.shields.io/badge/Kotlin-2.1-1C1E21?style=flat-square&labelColor=C9884A" alt="Kotlin 2.1">
  <img src="https://img.shields.io/badge/Compose-Material%203-1C1E21?style=flat-square&labelColor=C9884A" alt="Jetpack Compose">
  <img src="https://img.shields.io/github/actions/workflow/status/myqzurdux3/ardoise/ci.yml?branch=main&style=flat-square&label=CI&labelColor=C9884A&color=1C1E21" alt="CI">
  <img src="https://img.shields.io/badge/langues-EN%20%C2%B7%20FR-1C1E21?style=flat-square&labelColor=C9884A" alt="Anglais et français">
  <img src="https://img.shields.io/badge/licence-MIT-1C1E21?style=flat-square&labelColor=C9884A" alt="MIT">
</p>

---

## Le problème

Aucune application n'affiche une liste **Google Tasks** en permanence sur
l'écran de verrouillage Android.

Les widgets d'écran de verrouillage de Google sont bridés en taille, en
fréquence de rafraîchissement et en placement. Les applications de notes
persistantes n'ont pas de synchronisation — vous recopiez à la main. Les
applications de tâches qui ont, elles, une notification permanente ne se
synchronisent pas avec Google Tasks.

## L'intuition

Sur Android, la surface réellement « toujours visible » de l'écran de
verrouillage **n'est pas le widget**. C'est la notification, et
accessoirement le fond d'écran.

Une notification `ongoing` en `BigTextStyle` affiche six à huit lignes, accepte
des boutons d'action, échappe au throttling des widgets et survit au
redémarrage. Le fond d'écran de verrouillage, lui, est un `Bitmap` que
l'application peut redessiner à volonté — contrôle typographique total.

Ardoise exploite ces deux surfaces à partir d'une source unique.

<table>
  <tr>
    <td width="42%" align="center"><img src="art/screen-lockscreen.png" alt="Écran de verrouillage" width="300"></td>
    <td width="58%" valign="middle">
      <img src="art/screen-notification.png" alt="Notification dépliée" width="420">
      <br><br>
      <sub>À gauche, les deux surfaces ensemble sur l'écran verrouillé : la
      notification en haut, le rendu du fond d'écran en dessous. À droite, la
      notification dépliée — six lignes, les retards signalés, et les deux
      actions accessibles sans déverrouiller.</sub>
    </td>
  </tr>
</table>

<sub>Captures réelles, Pixel 9a sous Android 16 (API 37).</sub>

## Ce que fait Ardoise

- Affiche la liste Google Tasks de votre choix, en continu, sur l'écran de
  verrouillage.
- Marque les tâches en retard, en ocre.
- Permet de cocher la première tâche **depuis l'écran verrouillé**, sans
  déverrouiller le téléphone.
- Continue de fonctionner hors ligne et après un redémarrage, à partir d'un
  cache local.
- Ne stocke **aucun jeton d'accès** et ne contient **aucun secret client**.
  Rien ne quitte votre téléphone, sauf les appels à l'API Google.
- Parle **anglais et français**, en suivant la langue du système. L'anglais
  est la locale par défaut, donc le repli pour toute autre langue.

## Ce qu'Ardoise ne fait pas

Créer ou modifier des tâches, gérer les sous-tâches, les notes ou plusieurs
comptes. L'application Google Tasks fait déjà tout cela très bien. Ardoise est
une surface d'affichage, pas un gestionnaire de tâches.

## Architecture

```
  UI (Compose)          HomeScreen, LockPreview, HomeViewModel
        |
  Domain                TaskRepository, RenderSnapshot, SnapshotMapper
        |
  Data                  TasksApi (REST), AuthProvider, SettingsStore, SnapshotStore
```

Les deux moteurs de rendu consomment le même `RenderSnapshot` et ne connaissent
ni le réseau, ni l'authentification, ni le stockage.

| Choix | Pourquoi |
|---|---|
| Client REST maison | `google-api-services-tasks` est lourd et pénible sur Android. Trois appels suffisent. |
| Google Identity Services | Renvoie un jeton frais à chaque appel : rien à stocker, rien à rafraîchir, pas de serveur. |
| `WorkManager` en polling | L'API Google Tasks n'offre ni webhook ni push. Le polling n'est pas un raccourci, c'est la seule option. |
| Snapshot JSON, pas de base | Une cinquantaine de lignes de texte, toujours lues d'un bloc. Room serait de l'ingénierie excessive. |

## Deux pièges que seul l'appareil révèle

Les tests unitaires passaient, l'écran verrouillé donnait tort au code. Les
deux corrections qui suivent viennent d'une exécution réelle, pas d'une revue.

**1. `IMPORTANCE_LOW` détruit la fonctionnalité.**
C'est le choix qui semble évident pour une notification permanente et
silencieuse. Mais Android classe `LOW` parmi les notifications « silencieuses »
et les **réduit à une simple pastille** sur l'écran de verrouillage : plus une
seule ligne de texte ne survit. La bonne combinaison est `IMPORTANCE_DEFAULT`
avec le son coupé au niveau du canal — toujours aucun son, aucune vibration,
aucune bannière, mais la notification reste dépliable et prioritaire.

**2. Le fond d'écran est demandé en double largeur.**
Le système réclame ici un fond d'écran de 4848 × 2424 pour un écran de
1080 × 2424 : la largeur est doublée pour le parallaxe de l'écran d'accueil.
Un bitmap à la taille de l'écran s'y fait redimensionner et la composition
casse. Passer un `visibleCropHint` couvrant tout le bitmap le fixe à l'écran.

## L'application

<p align="center">
  <img src="art/screen-app.png" alt="Écran de réglages d'Ardoise" width="300">
</p>

Un seul écran. Il montre en permanence un aperçu de ce que donnera l'écran de
verrouillage — la seule façon honnête de régler une surface qu'on ne peut pas
voir pendant qu'on la configure.

## Installation

Ardoise n'est pas sur le Play Store : le scope `tasks` est un scope sensible,
dont la publication imposerait une vérification OAuth complète. Pour un usage
personnel, on reste en mode « test » et on s'auto-autorise — aucune friction.

**1. Créez un identifiant OAuth**

Sur [Google Cloud Console](https://console.cloud.google.com/) :

1. Créez un projet, puis activez l'**API Google Tasks**.
2. Écran de consentement OAuth : type **Externe**, statut **Test**, et
   ajoutez votre adresse Google dans *Utilisateurs de test*.
3. Ajoutez le scope `https://www.googleapis.com/auth/tasks`.
4. Créez un **ID client OAuth** de type **Android** :
   - nom du package : `fr.ardoise.tasks`
   - empreinte SHA-1 : celle de votre clé de signature

```bash
# empreinte SHA-1 de la clé de debug
keytool -list -v -keystore ~/.android/debug.keystore \
        -alias androiddebugkey -storepass android -keypass android
```

Aucun fichier n'est à ajouter au dépôt : l'identifiant Android est associé au
couple package + SHA-1 côté Google, pas embarqué dans l'application.

**2. Compilez et installez**

```bash
git clone <ce-dépôt> && cd ardoise
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:installDebug
```

**Si Ardoise refuse la connexion**

Juste après le choix du compte, Google rejette une application dont le couple
package + SHA-1 n'a pas d'identifiant OAuth. Play services le remonte comme un
générique `8 INTERNAL_ERROR` et ne nomme la vraie cause,
`UNREGISTERED_ON_API_CONSOLE`, que dans le message de statut — ce qui donne
l'impression d'un refus de votre part alors que vous venez d'accepter.

Ardoise reconnaît ce cas et affiche un encart **Configuration requise** avec le
nom de package et l'empreinte SHA-1 de la version installée, lus depuis le
`PackageManager` et prêts à copier. Ce sont exactement les deux valeurs à
enregistrer à l'étape 1.

**3. Réglez Android**

Ouvrez Ardoise, connectez votre compte, choisissez une liste. Puis, dans les
réglages système :

> **Paramètres → Notifications → Notifications sur l'écran de verrouillage →
> Afficher tout le contenu**

Sans cela, Android masque le texte de la notification sur l'écran verrouillé.

## Développement

```bash
./gradlew :app:testDebugUnitTest   # 30 tests unitaires
./gradlew :app:assembleDebug       # APK de debug
```

Les tests couvrent le calcul des retards, le mapping de l'API, la rédaction de
la notification, le client REST (via `MockWebServer`) et le rendu du fond
d'écran (via Robolectric en mode graphique natif).

Le reste a été vérifié sur un Pixel 9a émulé sous Android 16 : les deux
surfaces affichées simultanément sur un vrai écran verrouillé, la notification
dépliée avec ses actions, la carte d'avertissement qui disparaît au retour au
premier plan après l'octroi de la permission, et le passage en « hors ligne »
quand la synchronisation échoue.

**Le chemin d'authentification n'a pas pu être testé de bout en bout** : il
demande un identifiant OAuth Google réel et un compte connecté. Le reste de la
chaîne a été validé en injectant un instantané directement dans le `DataStore`
de l'application.

## Limites assumées

1. **Jusqu'à 30 minutes de latence** entre une modification faite ailleurs et
   son affichage. Inhérent au polling ; réglable sur 15 minutes.
2. **`FLAG_LOCK` n'est pas honoré par tous les constructeurs.** L'échec est
   détecté à l'exécution et la surface se désactive proprement.
3. **Le fond d'écran de verrouillage est remplacé** quand la surface est
   activée. Elle est désactivée par défaut, précisément pour cette raison.

## Nom

Une ardoise, c'est une surface sombre où l'on écrit en clair, qu'on essuie et
qu'on réécrit. C'est la description exacte d'un écran de verrouillage. La
charte graphique en découle : charbon `#1C1E21`, craie `#F2EFE9`, ocre
`#C9884A` pour ce qui a dépassé sa date.

## Licence

MIT — voir [LICENSE](LICENSE).
