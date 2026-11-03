package fr.minenorth.accueil.client.phone;

import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import fr.minenorth.accueil.network.ModNetwork;
import net.minecraft.client.Minecraft;

/**
 * Seul point d'entrée vers {@link PhoneDeskScreen} (qui hérite d'une classe du téléphone MikasRevs Phone 2). Ne JAMAIS toucher cette classe sans
 * avoir vérifié que le mod « newphone » est chargé. Côté client uniquement.
 */
public final class PhoneDesk {
    private PhoneDesk() {}

    /**
     * Onglet du bureau reçu du serveur : mis à jour s'il est déjà affiché dans le téléphone, ouvert dans le téléphone si le téléphone est à
     * l'écran (bouton Bureau de l'application Police). false = le grand écran habituel (tablette, bureau du commissariat).
     */
    public static boolean handle(ModNetwork.DeskPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneDeskScreen ps) {
            ps.update(p);
            return true;
        }
        if (mc.screen instanceof AbstractZPhoneScreen && mc.player != null) {
            PhoneNavigationState.open(mc, mc.screen, new PhoneDeskScreen(p));
            return true;
        }
        return false;
    }
}
