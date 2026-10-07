package fr.minenorth.accueil.client;

import fr.minenorth.accueil.network.ModNetwork;
import net.minecraft.client.Minecraft;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    public static void accueil(ModNetwork.AccueilPacket p) {
        Minecraft.getInstance().setScreen(new AccueilScreen(p));
    }

    /** Met à jour le bureau déjà ouvert (on garde la page), sinon l'ouvre. */
    public static void desk(ModNetwork.DeskPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DeskScreen s) s.update(p);
        else mc.setScreen(new DeskScreen(p));
    }
}
