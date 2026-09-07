# Numismatics Treasury 1.0.1

Addon NeoForge 1.21.1 pour Numismatics, développé sous le namespace
`numismatics_treasury` et le package `dev.yassou.numismaticstreasury`.

## Fonctionnalités

- `/pay <pseudo> <montant>` : transfert direct entre deux comptes bancaires
  Numismatics, y compris vers un joueur hors ligne déjà connu du serveur.
- Guichet de virement : même transfert dans une interface dédiée.
- Terminal de transfert portable : ouvre cette interface directement depuis
  la main, sans bloc à proximité.
- Shop serveur : vente ou rachat d'un objet par unité ou par lot, stock et
  argent serveur infinis, paiement exclusivement depuis le compte bancaire du
  joueur.
- Shop joueur : un seul type d'objet, stock à capacité illimitée et versement
  direct des ventes sur le compte Numismatics du propriétaire. Le propriétaire
  configure le nombre d'objets et le prix de chaque lot. Les entonnoirs vanilla
  et les systèmes d'insertion de Create peuvent l'alimenter, mais uniquement
  avec l'objet configuré.
- Hôtel des ventes global : ventes directes, enchères avec argent bloqué,
  expirations, commission configurable et récupération sécurisée des objets.
- Interfaces sombres sans flou vanilla, boutons et cadres adaptés dans le
  namespace propre au mod.

## Utilisation

- Clic droit sur le shop : ouvrir le commerce.
- Maj + clic droit par un opérateur sur le shop : choisir l'objet tenu en main,
  le sens achat/vente, le nombre d'objets par lot et le prix du lot.
- Maj + clic droit sur son shop joueur : choisir l'objet, la taille et le prix
  du lot, puis transférer les piles vers le véritable slot d'entrée avec la
  combinaison Maj + clic. Seul
  son propriétaire (ou un opérateur) peut le modifier et il faut vider tout
  son stock avant de le casser.
- Clic droit sur n'importe quel hôtel des ventes : ouvrir le marché global.
- Pour publier une annonce, sélectionner une pile dans l'inventaire affiché,
  puis définir son prix, son type et sa durée.
- Clic droit sur le guichet de virement : saisir le pseudo et le montant.
- Dans le guichet, la touche Tab complète les pseudonymes connus, y compris
  ceux des joueurs hors ligne possédant déjà un compte Numismatics.
- Dans la configuration du shop et la création d'annonce, cliquer sur une pile
  de l'inventaire affiché pour la placer dans la case d'objet.

Toutes les actions importantes utilisent une confirmation en deux clics.

## Configuration serveur

Le fichier `config/numismatics_treasury.json` est créé au premier lancement.
Il permet notamment de désactiver indépendamment :

- la commande `/pay` ;
- le guichet de virement ;
- le terminal de transfert portable ;
- le shop serveur ;
- le shop joueur ;
- l'hôtel des ventes.

Il configure aussi les limites de paiement, la commission, le nombre maximal
d'annonces, les prix et les durées autorisées. Après modification :

```text
/numismatics_treasury reload
```

Quand un module de bloc est désactivé, sa recette disparaît au rechargement et
le bloc éventuellement déjà placé devient inactif sans être supprimé du monde.
Le shop serveur n'a volontairement aucune recette. Les recettes du shop joueur,
du guichet de virement et de l'hôtel des ventes utilisent toutes du laiton.

## Dépendances

- Minecraft 1.21.1
- NeoForge 21.1.242 ou ultérieur pour Minecraft 1.21.1
- Numismatics 1.0.20 ou ultérieur

Numismatics est la seule dépendance de mod déclarée par cet addon.

## Installation

Placez `numismatics_treasury-1.0.1.jar` dans le dossier `mods` du serveur et
des clients, aux côtés de Numismatics. Le fichier JSON de configuration est
exclusivement géré par le serveur puis synchronisé vers les clients : il ne
doit donc être modifié qu'une seule fois, côté serveur.

## Construction

Numismatics est téléchargé automatiquement depuis son dépôt Maven officiel :

```text
./gradlew build
```

Le JAR est généré dans `build/libs/`.

Les interfaces sont écrites en anglais et disposent d'une traduction française
complète. Les notifications d'achat, de nouvelle enchère, de surenchère et de
fin d'annonce sont conservées pour les joueurs hors ligne.
