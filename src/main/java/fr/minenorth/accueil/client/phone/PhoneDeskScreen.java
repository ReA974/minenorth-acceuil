package fr.minenorth.accueil.client.phone;

import fr.minenorth.accueil.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.UUID;

/**
 * Bureau de la police dessiné dans l'écran du téléphone (ouvert par le bouton Bureau de l'application Police) : plaintes, historique,
 * rendez-vous, objets trouvés, véhicules en fourrière. Mêmes actions que {@link fr.minenorth.accueil.client.DeskScreen} ; le serveur revérifie tout.
 */
public class PhoneDeskScreen extends PhoneAppBase {
    private static final int[] VIEWS = {ModNetwork.V_COMPLAINTS, ModNetwork.V_HISTORY, ModNetwork.V_APPOINTMENTS, ModNetwork.V_FOUND, ModNetwork.V_IMPOUND};

    private ModNetwork.DeskPacket v;
    /** Plainte dont on attend la confirmation de fermeture (-1 = aucune). */
    private int confirmId = -1;
    /** Objet trouvé que l'on est en train de rendre : on choisit le joueur (-1 = aucun). */
    private int returnId = -1;
    private String returnLabel = "";

    public PhoneDeskScreen(ModNetwork.DeskPacket v) {
        super("Bureau");
        this.v = v;
    }

    public void update(ModNetwork.DeskPacket n) {
        if (n.view() != v.view()) resetScroll();
        this.v = n;
        this.confirmId = -1;
        this.returnId = -1;
    }

    private void send(int action, UUID target, int n) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(action, target == null ? ModNetwork.NONE : target, "", n));
    }

    private static String[] fields(String row) { return row.split(ModNetwork.SEP, -1); }

    private static String field(String[] f, int i) { return i < f.length ? f[i] : ""; }

    private static int number(String s) { try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return -1; } }

    @Override protected String title() { return "Bureau"; }

    @Override protected String[] tabs() { return new String[]{"Plaintes", "Historique", "Rendez-vous", "Objets", "Fourrière"}; }

    @Override
    protected void tabChanged() {
        returnId = -1;
        send(ModNetwork.S_VIEW, null, VIEWS[Math.max(0, Math.min(VIEWS.length - 1, tab))]);
    }

    @Override protected String badge() { return "Commissariat"; }

    @Override protected int badgeColor() { return DARK; }

    @Override protected String message() { return returnId >= 0 ? "" : v.message(); }

    @Override protected boolean messageError() { return !v.ok(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        for (int i = 0; i < VIEWS.length; i++) if (VIEWS[i] == v.view()) tab = i;
        super.render(g, mx, my, pt);
    }

    private void card(GuiGraphics g, float t, float b, int accent) {
        box(g, L, t, R, b, 12, CARD);
        box(g, L + 3, t + 6, L + 6, b - 6, 1.5f, accent);
    }

    @Override
    protected float content(GuiGraphics g, int mx, int my, float y) {
        if (returnId >= 0) return giveBack(g, mx, my, y);
        List<String> list = v.rows();
        if (list.isEmpty() && v.ok()) return wrap(g, switch (v.view()) {
            case ModNetwork.V_FOUND -> "Aucun objet trouvé en dépôt.";
            case ModNetwork.V_IMPOUND -> "Aucun véhicule en fourrière.";
            case ModNetwork.V_HISTORY -> "Aucune plainte fermée pour le moment.";
            case ModNetwork.V_APPOINTMENTS -> "Aucune demande de rendez-vous.";
            default -> "Aucune plainte en cours.";
        }, L, y, 9.5f, R - L, MUTED);
        float mid = (L + R) / 2;
        for (String row : list) {
            String[] f = fields(row);
            switch (v.view()) {
                case ModNetwork.V_FOUND -> {
                    int id = number(field(f, 0));
                    String what = field(f, 1) + "x " + field(f, 2);
                    card(g, y, y + 62, CYAN);
                    text(g, what, L + 12, y + 7, 10, R - L - 24, WHITE);
                    text(g, "déposé par " + field(f, 3), L + 12, y + 21, 8.5f, R - L - 24, MUTED);
                    // Rendre = choisir le propriétaire parmi les joueurs connectés. Prendre = l'objet va dans son propre inventaire.
                    button(g, mx, my, L + 12, y + 34, mid - 3, y + 56, "Rendre", 9, GREEN, true, () -> { returnId = id; returnLabel = what; resetScroll(); });
                    button(g, mx, my, mid + 3, y + 34, R - 8, y + 56, "Prendre", 9, DARK, true, () -> send(ModNetwork.S_TAKE, null, id));
                    y += 67;
                }
                case ModNetwork.V_IMPOUND -> {
                    card(g, y, y + 36, WARN);
                    text(g, field(f, 0), L + 12, y + 6, 10, R - L - 24, WHITE);
                    text(g, "Propriétaire : " + field(f, 1), L + 12, y + 20, 8.5f, R - L - 24, TEXT);
                    y += 41;
                }
                case ModNetwork.V_HISTORY -> {
                    card(g, y, y + 50, DARK);
                    text(g, field(f, 0) + " contre " + field(f, 1), L + 12, y + 6, 10, R - L - 24, WHITE);
                    text(g, field(f, 2), L + 12, y + 20, 8.5f, R - L - 24, TEXT);
                    text(g, field(f, 4) + " · fermée par " + field(f, 3), L + 12, y + 33, 8, R - L - 24, MUTED);
                    y += 55;
                }
                case ModNetwork.V_APPOINTMENTS -> {
                    int id = number(field(f, 0));
                    boolean taken = !field(f, 4).isEmpty();
                    card(g, y, y + 62, taken ? OK : WARN);
                    text(g, field(f, 1), L + 12, y + 6, 10, R - L - 24, WHITE);
                    String when = field(f, 3).isEmpty() ? "" : " (" + field(f, 3) + ")";
                    text(g, field(f, 2) + when, L + 12, y + 20, 8.5f, R - L - 24, TEXT);
                    if (taken) {
                        text(g, "Pris par " + field(f, 4), L + 12, y + 39, 8.5f, mid - L - 16, OK);
                        button(g, mx, my, mid + 3, y + 34, R - 8, y + 56, "Terminer", 9, PINK, true, () -> send(ModNetwork.S_APPT_DONE, null, id));
                    } else {
                        button(g, mx, my, L + 12, y + 34, mid - 3, y + 56, "Prendre", 9, GREEN, true, () -> send(ModNetwork.S_APPT_TAKE, null, id));
                        button(g, mx, my, mid + 3, y + 34, R - 8, y + 56, "Refuser", 9, PINK, true, () -> send(ModNetwork.S_APPT_DONE, null, id));
                    }
                    y += 67;
                }
                default -> {
                    int id = number(field(f, 0));
                    boolean open = "1".equals(field(f, 1));
                    card(g, y, y + 62, open ? OK : WARN);
                    text(g, field(f, 2) + " contre " + field(f, 3), L + 12, y + 6, 10, R - L - 24, WHITE);
                    text(g, field(f, 4), L + 12, y + 20, 8.5f, R - L - 24, TEXT);
                    if (open) text(g, "OUVERTE", L + 12, y + 39, 9, mid - L - 16, OK);
                    else button(g, mx, my, L + 12, y + 34, mid - 3, y + 56, "Ouvrir", 9, GREEN, true, () -> send(ModNetwork.S_OPEN, null, id));
                    // Deux touches : la plainte quitte la liste et part dans l'historique.
                    button(g, mx, my, mid + 3, y + 34, R - 8, y + 56, confirmId == id ? "Sûr ?" : "Fermer", 9, PINK, true, () -> {
                        if (confirmId == id) send(ModNetwork.S_CLOSE_COMPLAINT, null, id); else confirmId = id;
                    });
                    y += 67;
                }
            }
        }
        return y;
    }

    /** Choix du joueur à qui rendre l'objet trouvé. */
    private float giveBack(GuiGraphics g, int mx, int my, float y) {
        button(g, mx, my, L, y, L + 70, y + 22, "Annuler", 9, HOVER, true, () -> { returnId = -1; resetScroll(); });
        text(g, "Rendre : " + returnLabel, L + 78, y + 6, 10, R - L - 78, WHITE);
        y += 30;
        if (v.players().isEmpty()) return wrap(g, "Aucun joueur connecté.", L, y, 9.5f, R - L, MUTED);
        for (ModNetwork.Person p : v.players()) {
            final int id = returnId;
            button(g, mx, my, L, y, R, y + 24, p.name(), 9.5f, DARK, true, () -> send(ModNetwork.S_RETURN, p.id(), id));
            y += 29;
        }
        return y;
    }

    @Override
    public void removed() {
        send(ModNetwork.S_CLOSE, null, 0);
        super.removed();
    }
}
