# MineNorth Accueil Police

Remplace le script Skript « Accueil Police ». Forge 1.20.1, mod indépendant (aucune dépendance obligatoire).
Optionnels, détectés automatiquement : Véhicules (fourrière), Identité (noms RP), Police.

## Citoyens
`/policeaccueil <joueur>` (console / OP — à donner au PNJ) : porter plainte, prendre rendez-vous avec un policier, voir ses véhicules en fourrière, déposer un objet trouvé.
Le paiement des amendes du script n'a pas été repris.

### Appeler un service
Bouton **APPELER LA POLICE / LE MAIRE / LES POMPIERS** : choisir le service et, si on veut, un motif. L'appel (avec le nom et la position du citoyen) n'arrive qu'aux policiers et pompiers **en service**, ou au maire s'il est connecté (mod État). Si personne ne peut répondre, le citoyen est prévenu. Un appel toutes les 30 s par joueur.

### Prendre son service
Les policiers (mod Police) et les pompiers / SAMU (mod Secours) voient en plus un bouton **PRENDRE SON SERVICE / FIN DE SERVICE** dans le même menu, équivalent à celui de leur tablette. Le maire n'a pas de notion de service.
Nécessite la MineNorth API à jour (`setDuty`) et les mods Police / Secours à jour.

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

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).
