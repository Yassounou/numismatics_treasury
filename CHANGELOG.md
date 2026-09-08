# Changelog

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
