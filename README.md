# MineNorth Accueil Police

Remplace le script Skript « Accueil Police ». Forge 1.20.1, mod indépendant (aucune dépendance obligatoire).
Optionnels, détectés automatiquement : Véhicules (fourrière), Identité (noms RP), Police.

## Citoyens
`/policeaccueil <joueur>` (console / OP — à donner au PNJ) : porter plainte, prendre rendez-vous avec un policier, voir ses véhicules en fourrière, déposer un objet trouvé.
Le paiement des amendes du script n'a pas été repris.

## Police
- `/policestaff` : ouvre le bureau (plaintes, historique, rendez-vous, objets trouvés, fourrière).
- `/policestaff <joueur>` (console / OP) : ouvre le bureau chez ce joueur, pour un PNJ.
- `/policestaff set` / `/policestaff remove` (OP) : le bloc regardé ouvre, ou n'ouvre plus, le bureau au clic droit.

Est policier : un OP, un joueur avec le tag `police.check` (`/tag <joueur> add police.check`), ou un policier du mod MineNorth Police.

## Bureau
- **Plaintes** : ouvrir la procédure, puis la fermer. Une plainte fermée n'est pas supprimée, elle passe dans **Historique**.
- **Rendez-vous** : prendre une demande (le citoyen est prévenu), puis la terminer ; ou la refuser.
- **Objets** : rendre l'objet à un joueur connecté, ou le prendre soi-même.
Les réponses de la police arrivées pendant l'absence d'un joueur lui sont remises à sa prochaine connexion.
