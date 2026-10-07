package fr.minenorth.accueil;

import fr.minenorth.api.PlayerWipeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Suppression d'un joueur depuis le panneau admin : ses plaintes, rendez-vous et messages en attente sont effacés. */
@Mod.EventBusSubscriber(modid = MineNorthAccueil.MOD_ID)
public final class AccueilWipe {
    private AccueilWipe() {}

    @SubscribeEvent
    public static void onWipe(PlayerWipeEvent e) {
        AccueilData d = AccueilData.get(e.server());
        boolean any = d.complaints.removeIf(c -> e.player().equals(c.plaintiff));
        any |= d.appointments.removeIf(a -> e.player().equals(a.citizen));
        any |= d.notices.remove(e.player()) != null;
        if (any) {
            d.setDirty();
            e.cleaned("accueil police");
        }
    }
}
