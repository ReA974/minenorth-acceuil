package fr.minenorth.accueil.client;

import fr.minenorth.accueil.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bureau de la police : plaintes, historique, rendez-vous, objets trouvés, véhicules en fourrière. */
public class DeskScreen extends Screen {
    private static final int W = 420, H = 240, ROWS = 6;
    private ModNetwork.DeskPacket v;
    private int left, top, page;
    /** Plainte dont on attend la confirmation de fermeture (-1 = aucune). */
    private int confirmId = -1;
    /** Objet trouvé que l'on est en train de rendre : on choisit le joueur (-1 = aucun). */
    private int returnId = -1;
    private String returnLabel = "";

    private record Label(String text, int x, int y, int color) {}
    private record Card(int x, int y, int w, int h, int accent) {}
    private final List<Label> labels = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();

    public DeskScreen(ModNetwork.DeskPacket v) {
        super(Component.literal("Bureau Police"));
        this.v = v;
    }

    public void update(ModNetwork.DeskPacket n) {
        if (n.view() != v.view()) page = 0;
        this.v = n;
        this.confirmId = -1;
        this.returnId = -1;
        clearWidgets();
        init();
    }

    private void send(int action, UUID target, int n) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(action, target == null ? ModNetwork.NONE : target, "", n));
    }
    private MineNorthButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new MineNorthButton(x, y, w, h, Component.literal(label), color, r));
    }
    private void label(String text, int x, int y, int color) { labels.add(new Label(text, x, y, color)); }
    private void label(String text, int x, int y, int color, int maxWidth) { label(font.plainSubstrByWidth(text, maxWidth), x, y, color); }
    private void card(int x, int y, int w, int h, int accent) { cards.add(new Card(x, y, w, h, accent)); }
    private void rebuild(Runnable change) { change.run(); clearWidgets(); init(); }

    private static String[] fields(String row) { return row.split(ModNetwork.SEP, -1); }
    private static String field(String[] f, int i) { return i < f.length ? f[i] : ""; }
    private static int number(String s) { try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return -1; } }

    private void pager(int pages, int right) {
        if (pages <= 1) return;
        int yb = top + H - 36;
        btn(right - 78, yb, 20, 18, "<", MineNorthStyle.DARK, () -> rebuild(() -> page--)).enabled(page > 0);
        String p = (page + 1) + " / " + pages;
        label(p, right - 39 - font.width(p) / 2, yb + 5, MineNorthStyle.TEXT);
        btn(right - 20, yb, 20, 18, ">", MineNorthStyle.DARK, () -> rebuild(() -> page++)).enabled(page < pages - 1);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        labels.clear();
        cards.clear();
        int x = left + 14, w = W - 28;
        btn(left + W - 100, top + 10, 86, 16, returnId >= 0 ? "Annuler" : "Fermer", MineNorthButton.GHOST,
                () -> { if (returnId >= 0) rebuild(() -> { returnId = -1; page = 0; }); else onClose(); });

        if (returnId >= 0) { buildReturn(x, w); return; }

        // Onglets, dans l'ordre d'affichage (le numéro est celui de la vue côté serveur).
        String[] tabs = {"PLAINTES", "HISTORIQUE", "RENDEZ-VOUS", "OBJETS", "FOURRIÈRE"};
        int[] views = {ModNetwork.V_COMPLAINTS, ModNetwork.V_HISTORY, ModNetwork.V_APPOINTMENTS, ModNetwork.V_FOUND, ModNetwork.V_IMPOUND};
        int[] widths = {64, 74, 84, 60, 72};
        int tx = x;
        for (int i = 0; i < tabs.length; i++) {
            final int view = views[i];
            btn(tx, top + 46, widths[i], 18, tabs[i], v.view() == view ? MineNorthStyle.CYAN : MineNorthStyle.DARK, () -> send(ModNetwork.S_VIEW, null, view));
            tx += widths[i] + 4;
        }

        List<String> list = v.rows();
        int y0 = top + 72;
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(pages - 1, page));
        if (list.isEmpty() && v.ok()) {
            label(switch (v.view()) {
                case ModNetwork.V_FOUND -> "Aucun objet trouvé en dépôt.";
                case ModNetwork.V_IMPOUND -> "Aucun véhicule en fourrière.";
                case ModNetwork.V_HISTORY -> "Aucune plainte fermée pour le moment.";
                case ModNetwork.V_APPOINTMENTS -> "Aucune demande de rendez-vous.";
                default -> "Aucune plainte en cours.";
            }, x, y0 + 6, MineNorthStyle.MUTED);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= list.size()) break;
            String[] f = fields(list.get(idx));
            int y = y0 + i * 20;
            switch (v.view()) {
                case ModNetwork.V_FOUND -> {
                    int id = number(field(f, 0));
                    String what = field(f, 1) + "x " + field(f, 2);
                    card(x, y, w, 18, MineNorthStyle.CYAN);
                    label(what, x + 8, y + 5, MineNorthStyle.WHITE, 150);
                    label("par " + field(f, 3), x + 164, y + 5, MineNorthStyle.MUTED, w - 164 - 150);
                    // Rendre = choisir le propriétaire parmi les joueurs connectés. Prendre = l'objet va dans son propre inventaire.
                    btn(x + w - 144, y + 2, 66, 14, "RENDRE", MineNorthStyle.GREEN, () -> rebuild(() -> { returnId = id; returnLabel = what; page = 0; }));
                    btn(x + w - 74, y + 2, 72, 14, "PRENDRE", MineNorthStyle.DARK, () -> send(ModNetwork.S_TAKE, null, id));
                }
                case ModNetwork.V_IMPOUND -> {
                    card(x, y, w, 18, MineNorthStyle.WARN);
                    label(field(f, 0), x + 8, y + 5, MineNorthStyle.WHITE, 190);
                    label("Propriétaire : " + field(f, 1), x + 206, y + 5, MineNorthStyle.TEXT, w - 214);
                }
                case ModNetwork.V_HISTORY -> {
                    card(x, y, w, 18, MineNorthStyle.DARK);
                    label(field(f, 4), x + 8, y + 5, MineNorthStyle.MUTED);
                    label(field(f, 0) + " contre " + field(f, 1), x + 76, y + 5, MineNorthStyle.WHITE, 130);
                    label(field(f, 2), x + 212, y + 5, MineNorthStyle.TEXT, 96);
                    label("par " + field(f, 3), x + 314, y + 5, MineNorthStyle.MUTED, w - 320);
                }
                case ModNetwork.V_APPOINTMENTS -> {
                    int id = number(field(f, 0));
                    boolean taken = !field(f, 4).isEmpty();
                    card(x, y, w, 18, taken ? MineNorthStyle.OK : MineNorthStyle.WARN);
                    label(field(f, 1), x + 8, y + 5, MineNorthStyle.WHITE, 100);
                    String when = field(f, 3).isEmpty() ? "" : " (" + field(f, 3) + ")";
                    label(field(f, 2) + when, x + 114, y + 5, MineNorthStyle.TEXT, w - 114 - 150);
                    if (taken) {
                        label(field(f, 4), x + w - 144, y + 5, MineNorthStyle.OK, 70);
                        btn(x + w - 72, y + 2, 70, 14, "TERMINER", MineNorthStyle.PINK, () -> send(ModNetwork.S_APPT_DONE, null, id));
                    } else {
                        btn(x + w - 144, y + 2, 68, 14, "PRENDRE", MineNorthStyle.GREEN, () -> send(ModNetwork.S_APPT_TAKE, null, id));
                        btn(x + w - 72, y + 2, 70, 14, "REFUSER", MineNorthStyle.PINK, () -> send(ModNetwork.S_APPT_DONE, null, id));
                    }
                }
                default -> {
                    int id = number(field(f, 0));
                    boolean open = "1".equals(field(f, 1));
                    card(x, y, w, 18, open ? MineNorthStyle.OK : MineNorthStyle.WARN);
                    label(field(f, 2) + " contre " + field(f, 3), x + 8, y + 5, MineNorthStyle.WHITE, 160);
                    label(field(f, 4), x + 174, y + 5, MineNorthStyle.TEXT, w - 174 - 124);
                    if (open) label("OUVERTE", x + w - 116, y + 5, MineNorthStyle.OK);
                    else btn(x + w - 120, y + 2, 54, 14, "OUVRIR", MineNorthStyle.GREEN, () -> send(ModNetwork.S_OPEN, null, id));
                    // Deux clics : la plainte quitte la liste et part dans l'historique.
                    btn(x + w - 62, y + 2, 60, 14, confirmId == id ? "SÛR ?" : "FERMER", MineNorthStyle.PINK, () -> {
                        if (confirmId == id) send(ModNetwork.S_CLOSE_COMPLAINT, null, id); else rebuild(() -> confirmId = id);
                    });
                }
            }
        }
        pager(pages, x + w);
    }

    /** Choix du joueur à qui rendre l'objet trouvé. */
    private void buildReturn(int x, int w) {
        List<ModNetwork.Person> players = v.players();
        label("À qui rendre : " + returnLabel + " ?", x, top + 50, MineNorthStyle.BLUE, w);
        int y0 = top + 66, rows = ROWS;
        int pages = Math.max(1, (players.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(pages - 1, page));
        if (players.isEmpty()) label("Aucun joueur connecté.", x, y0 + 6, MineNorthStyle.MUTED);
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= players.size()) break;
            ModNetwork.Person p = players.get(idx);
            final int id = returnId;
            btn(x, y0 + i * 20, w, 18, p.name(), MineNorthStyle.DARK, () -> send(ModNetwork.S_RETURN, p.id(), id));
        }
        pager(pages, x + w);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, returnId >= 0 ? "RENDRE UN OBJET" : "BUREAU POLICE", "MINENORTH RP • COMMISSARIAT");
        for (Card c : cards) MineNorthStyle.card(g, c.x(), c.y(), c.w(), c.h(), false, c.accent());
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), l.color(), false);
        if (!v.message().isEmpty() && returnId < 0) {
            g.drawString(font, font.plainSubstrByWidth(v.message(), W - 28), left + 14, top + H - 13, v.ok() ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void removed() {
        send(ModNetwork.S_CLOSE, null, 0);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
