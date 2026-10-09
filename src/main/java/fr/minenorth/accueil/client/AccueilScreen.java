package fr.minenorth.accueil.client;

import fr.minenorth.accueil.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Accueil du commissariat (PNJ) : porter plainte, prendre rendez-vous, voir ses véhicules en fourrière, déposer un objet trouvé. */
public class AccueilScreen extends Screen {
    private static final int W = 360, H = 240, ROWS = 4;
    private static final int HOME = 0, COMPLAINT = 1, APPOINTMENT = 2, CALL = 3;
    private static final String[] NAMES = {"POLICE", "MAIRIE", "POMPIERS"}, WHO = {"LA POLICE", "LE MAIRE", "LES POMPIERS"};
    private final List<ModNetwork.Person> players;
    /** Type d'accueil (ModNetwork.K_*) et service du joueur dans ce métier (ModNetwork.DUTY_*). */
    private final int kind, duty;
    private int left, top, page, mode = HOME;
    /** Personne visée par la plainte : null = pas encore choisie, NONE = inconnu (plainte contre X). */
    private UUID target;
    private String targetName = "", error = "", kReason = "", kWhen = "";
    private EditBox bReason, bWhen;
    /** true dès qu'une action a été envoyée : inutile de prévenir le serveur d'une simple fermeture. */
    private boolean done;

    public AccueilScreen(ModNetwork.AccueilPacket p) {
        super(Component.literal(kindTitle(p.kind())));
        this.players = p.players();
        this.kind = p.kind();
        this.duty = p.duty();
        // Caserne et mairie : pas de menu, on arrive directement sur l'appel.
        if (kind != ModNetwork.K_POLICE) mode = CALL;
    }

    private static String kindTitle(int kind) {
        return kind == ModNetwork.K_FIRE ? "Accueil Pompiers" : kind == ModNetwork.K_MAYOR ? "Accueil Mairie" : "Accueil Police";
    }

    private void send(int action, UUID t, String text) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(action, t == null ? ModNetwork.NONE : t, text == null ? "" : text, 0));
    }
    private void act(int action, UUID t, String text) { done = true; send(action, t, text); onClose(); }
    private void call(int service, String text) {
        done = true;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(ModNetwork.C_CALL, ModNetwork.NONE, text == null ? "" : text, service));
        onClose();
    }
    private MineNorthButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new MineNorthButton(x, y, w, h, Component.literal(label), color, r));
    }
    private EditBox box(int x, int y, int w, String hint, String value, int max) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.literal(hint));
        b.setMaxLength(max);
        b.setHint(Component.literal(hint));
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }
    private void go(Runnable change) {
        if (bReason != null) kReason = bReason.getValue();
        if (bWhen != null) kWhen = bWhen.getValue();
        error = "";
        change.run();
        clearWidgets();
        init();
    }

    /** Prise / fin de service, seulement pour un membre du métier de cet accueil (la mairie n'a pas de service). */
    private void dutyButton(int x, int y, int w) {
        if (duty == ModNetwork.DUTY_NONE) return;
        boolean on = duty == ModNetwork.DUTY_ON;
        btn(x, y, w, 22, on ? "FIN DE SERVICE" : "PRENDRE SON SERVICE", on ? MineNorthStyle.PINK : MineNorthStyle.GREEN, () -> act(ModNetwork.C_DUTY, null, ""));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        bReason = null;
        bWhen = null;
        int x = left + 14, w = W - 28;
        btn(left + W - 100, top + 10, 86, 16, mode == HOME || kind != ModNetwork.K_POLICE ? "Fermer" : "Retour", MineNorthButton.GHOST,
                () -> { if (mode == HOME || kind != ModNetwork.K_POLICE) onClose(); else go(() -> { mode = HOME; kReason = ""; }); });

        if (mode == HOME) {
            btn(x, top + 56, w, 22, "PORTER PLAINTE", MineNorthStyle.CYAN, () -> go(() -> mode = COMPLAINT));
            btn(x, top + 82, w, 22, "PRENDRE RENDEZ-VOUS AVEC UN POLICIER", MineNorthStyle.CYAN, () -> go(() -> mode = APPOINTMENT));
            btn(x, top + 108, w, 22, "APPELER LA POLICE", MineNorthStyle.PINK, () -> go(() -> { mode = CALL; kReason = ""; }));
            btn(x, top + 134, w, 22, "VOIR MES VÉHICULES EN FOURRIÈRE", MineNorthStyle.DARK, () -> act(ModNetwork.C_IMPOUND, null, ""));
            btn(x, top + 160, w, 22, "DÉPOSER UN OBJET TROUVÉ", MineNorthStyle.DARK, () -> act(ModNetwork.C_DEPOSIT, null, ""));
            dutyButton(x, top + 186, w);
            return;
        }

        if (mode == CALL) {
            bReason = box(x, top + 78, w, "Ex : agression devant l'entrée, incendie, rendez-vous urgent…", kReason, 90);
            btn(x, top + 108, w, 26, "APPELER " + WHO[kind], MineNorthStyle.GREEN, () -> call(kind, bReason.getValue()));
            if (kind != ModNetwork.K_POLICE) dutyButton(x, top + 150, w);
            return;
        }

        if (mode == APPOINTMENT) {
            bReason = box(x, top + 72, w, "Ex : témoignage, renseignement, récupérer un objet…", kReason, 90);
            bWhen = box(x, top + 116, w, "Ex : ce soir vers 21h, demain après-midi…", kWhen, 60);
            btn(x, top + 150, w, 22, "DEMANDER LE RENDEZ-VOUS", MineNorthStyle.GREEN, () -> {
                if (bReason.getValue().trim().length() < 5) { go(() -> error = "Écrivez le motif de votre rendez-vous."); return; }
                act(ModNetwork.C_APPOINTMENT, null, bReason.getValue().replace(ModNetwork.SEP, " ") + ModNetwork.SEP + bWhen.getValue().replace(ModNetwork.SEP, " "));
            });
            return;
        }

        int pages = Math.max(1, (players.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(pages - 1, page));
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= players.size()) break;
            ModNetwork.Person c = players.get(idx);
            btn(x, top + 62 + i * 20, w - 90, 18, c.name(), c.id().equals(target) ? MineNorthStyle.CYAN : MineNorthStyle.DARK,
                    () -> go(() -> { target = c.id(); targetName = c.name(); }));
        }
        btn(x + w - 84, top + 62, 84, 18, "INCONNU (X)", ModNetwork.NONE.equals(target) ? MineNorthStyle.CYAN : MineNorthStyle.DARK,
                () -> go(() -> { target = ModNetwork.NONE; targetName = "Inconnu (X)"; }));
        btn(x + w - 84, top + 84, 40, 18, "<", MineNorthStyle.DARK, () -> go(() -> page--)).enabled(page > 0);
        btn(x + w - 40, top + 84, 40, 18, ">", MineNorthStyle.DARK, () -> go(() -> page++)).enabled(page < pages - 1);
        bReason = box(x, top + 164, w, "Expliquez ce qui s'est passé", kReason, 96);
        btn(x, top + 190, w, 22, "DÉPOSER LA PLAINTE", MineNorthStyle.GREEN, () -> {
            if (target == null) { go(() -> error = "Choisissez la personne visée, ou « Inconnu (X) »."); return; }
            if (bReason.getValue().trim().length() < 5) { go(() -> error = "Écrivez la raison de votre plainte."); return; }
            act(ModNetwork.C_COMPLAINT, target, bReason.getValue());
        });
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        String title = mode == COMPLAINT ? "PORTER PLAINTE" : mode == APPOINTMENT ? "PRENDRE RENDEZ-VOUS" : mode == CALL ? "APPELER " + WHO[kind] : kindTitle(kind).toUpperCase(java.util.Locale.ROOT);
        MineNorthStyle.panel(g, left, top, W, H, title, kind == ModNetwork.K_FIRE ? "MINENORTH RP • CASERNE" : kind == ModNetwork.K_MAYOR ? "MINENORTH RP • MAIRIE" : "MINENORTH RP • COMMISSARIAT");
        int x = left + 14;
        if (mode == COMPLAINT) {
            g.drawString(font, players.isEmpty() ? "CONTRE QUI ? (aucun autre joueur connecté)" : "CONTRE QUI ? (joueurs connectés)", x, top + 50, MineNorthStyle.BLUE, false);
            g.drawString(font, target == null ? "RAISON" : "RAISON — plainte contre " + font.plainSubstrByWidth(targetName, 180), x, top + 152, MineNorthStyle.BLUE, false);
        } else if (mode == CALL) {
            g.drawString(font, "MOTIF DE L'APPEL (facultatif)", x, top + 64, MineNorthStyle.BLUE, false);
            g.drawString(font, "Seuls les agents disponibles reçoivent l'appel, avec votre position.", x, top + 140 + (kind == ModNetwork.K_POLICE ? 0 : 52), MineNorthStyle.MUTED, false);
        } else if (mode == APPOINTMENT) {
            g.drawString(font, "MOTIF DU RENDEZ-VOUS", x, top + 60, MineNorthStyle.BLUE, false);
            g.drawString(font, "VOS DISPONIBILITÉS (facultatif)", x, top + 104, MineNorthStyle.BLUE, false);
            g.drawString(font, "Un policier vous préviendra quand il prendra votre demande.", x, top + 182, MineNorthStyle.MUTED, false);
        } else {
            g.drawString(font, "Que souhaitez-vous faire ?", x, top + 44, MineNorthStyle.TEXT, false);
            g.drawString(font, "Objet trouvé : tenez-le en main avant de cliquer.", x, top + 214, MineNorthStyle.MUTED, false);
        }
        if (!error.isEmpty()) g.drawString(font, error, x, top + H - 14, MineNorthStyle.ALERT, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public void removed() {
        if (!done) send(ModNetwork.C_CLOSE, null, "");
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
