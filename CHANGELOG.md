# Changelog

## 1.2.0 — 2026-09-19

- Un shop joueur peut maintenant avoir jusqu'à 16 associés.
- Ajout d'une fenêtre dédiée et scrollable pour ajouter, modifier et retirer
  les associés ainsi que leurs pourcentages.
- La somme des parts associées est limitée à 100 % ; le solde et tous les
  arrondis restent versés au propriétaire.
- Les anciens shops à associé unique sont migrés automatiquement.
- Les associés conservent l'accès à la gestion du stock, tandis que seul le
  propriétaire ou un opérateur peut modifier la répartition des revenus.
- La saisie d'un associé prend désormais en charge la complétion des pseudos
  avec la touche Tab.
- Un joueur reçoit une notification dès qu'il est ajouté comme associé, y
  compris à sa prochaine connexion s'il était hors ligne.
- Un associé est également notifié lorsque son pourcentage de revenus change.

## 1.0.3 — 2026-09-18

- Affichage du tooltip complet des objets dans les shops joueur, y compris les
  enchantements, le lore personnalisé et les composants ajoutés par des mods.
- Ajout d'un joueur lié pouvant gérer le stock d'un shop joueur.
- Ajout d'une répartition configurable des revenus entre le propriétaire et le
  joueur lié. Les arrondis sont toujours versés au propriétaire.
- Versement direct des deux parts sur les comptes Numismatics et notifications
  individuelles après chaque vente.
- Compatibilité préservée avec les shops placés avant cette mise à jour.

## 1.0.2 — 2026-09-07

- Le shop serveur est désormais incassable et ne produit aucun objet lorsqu'il
  est supprimé par une commande ou un outil administratif.
- Les shops joueur et l'hôtel des ventes restent cassables à la main, avec une
  vitesse adaptée à la hache.
- Le guichet de virement est cassable à la main, plus rapide à miner avec une
  pioche et produit correctement son propre bloc.

## 1.0.1 — 2026-09-07

- Ajout des ventes par lots configurables dans les shops serveur et joueur.
- Ajout du choix du nombre de lots avec récapitulatif des objets et du prix
  total avant confirmation.
- Conservation automatique des anciens shops en lots d'un seul objet.
- Adaptation de « Tout vendre » pour ne vendre que les lots complets et laisser
  le reste dans l'inventaire.
- Affichage de la taille et du prix du lot sur les blocs et dans leur panneau
  d'interaction.
- Correction de la reconnaissance des shops placés sur une structure Sable.

## 1.0.0 — 2026-09-06

Première version publique de Numismatics Treasury.

- Ajout des virements bancaires par `/pay`, guichet et terminal portable.
- Ajout des shops serveur et des shops joueur à stock illimité.
- Compatibilité des shops joueur avec l'automatisation d'inventaire.
- Ajout d'un hôtel des ventes global avec ventes directes, enchères, séquestre,
  expirations, commissions et retraits sécurisés.
- Ajout de modules configurables et rechargeables côté serveur.
- Ajout des interfaces en anglais et de la traduction française complète.
- Ajout des modèles, textures, recettes et affichages en monde.
