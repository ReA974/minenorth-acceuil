# MineNorth Accueil Police

Remplace le script Skript « Accueil Police ». Forge 1.20.1, mod indépendant (aucune dépendance obligatoire).
Optionnels, détectés automatiquement : Véhicules (fourrière), Identité (noms RP), Police.

## Citoyens
`/policeaccueil <joueur>` (console / OP — à donner au PNJ) : porter plainte, prendre rendez-vous avec un policier, voir ses véhicules en fourrière, déposer un objet trouvé.
Le paiement des amendes du script n'a pas été repris.

### Trois accueils (un PNJ chacun, commandes réservées à la console / OP)
| Commande | Accueil | Contenu |
|---|---|---|
| `/policeaccueil <joueur>` | Commissariat | Menu ci-dessus + **APPELER LA POLICE** + prise / fin de service des policiers. |
| `/pompieraccueil <joueur>` | Caserne | **APPELER LES POMPIERS** + prise / fin de service des pompiers / SAMU. |
| `/mairieaccueil <joueur>` | Mairie | **APPELER LE MAIRE** (le maire n'a pas de notion de service). |

L'appel (avec le nom et la position du citoyen, et un motif facultatif) n'arrive qu'aux policiers / pompiers **en service**, ou au maire s'il est connecté (mod État). Si personne ne peut répondre, le citoyen est prévenu. Un appel toutes les 30 s par joueur. On ne peut appeler que le service de l'accueil où l'on se trouve.
Le bouton de service n'apparaît que pour les membres du métier (équivalent de la tablette). Nécessite la MineNorth API à jour (`setDuty`) et les mods Police / Secours à jour.

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
