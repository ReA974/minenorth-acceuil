package fr.minenorth.accueil;

import fr.minenorth.accueil.network.ModNetwork;
import net.minecraftforge.fml.common.Mod;

/** Accueil du commissariat : remplace le script Skript « Accueil Police ». Mod indépendant, sans dépendance obligatoire. */
@Mod(MineNorthAccueil.MOD_ID)
public class MineNorthAccueil {
    public static final String MOD_ID = "minenorthaccueil";

    public MineNorthAccueil() {
        ModNetwork.register();
    }
}
