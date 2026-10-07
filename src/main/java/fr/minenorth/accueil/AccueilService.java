package fr.minenorth.accueil;

import fr.minenorth.accueil.network.ModNetwork;
import fr.minenorth.accueil.network.ModNetwork.ActionPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accueil du commissariat.
 *
 * Citoyens  : /policeaccueil <joueur>  (commande du PNJ) -> porter plainte, voir ses véhicules en fourrière, déposer un objet trouvé.
 * Police    : /policestaff [joueur]    -> bureau : plaintes, objets trouvés, véhicules en fourrière.
 *             /policestaff set|remove  (OP) -> le bloc regardé ouvre (ou n'ouvre plus) le bureau au clic droit.
 *
 * Est considéré comme policier : un OP, un joueur portant le tag « police.check »
 * (/tag <joueur> add police.check, le même que pour la fourrière), ou un policier du mod MineNorth Police s'il est installé.
 */
@Mod.EventBusSubscriber
public final class AccueilService {
    private AccueilService() {}

    private static final int MAX = 200;

    /** Citoyens dont le PNJ a ouvert l'accueil, et policiers dont le bureau est ouvert. */
    private static final Set<UUID> ACCUEIL = new HashSet<>(), DESK = new HashSet<>();

    // ------------------------------------------------------------------ outils
    private static void tell(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal(text)); }

    /** Policier (effectifs du mod Police, via MineNorth API) ou OP. */
    public static boolean isPolice(ServerPlayer p) {
        return p.hasPermissions(2) || fr.minenorth.api.MineNorth.police().isPolice(p);
    }

    private static void tellPolice(MinecraftServer s, String text) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (isPolice(p)) tell(p, text);
    }

    /** « NOM Prénom » de la carte d'identité (format des fichiers de police), sinon le pseudo. MineNorth API. */
    private static String display(ServerPlayer p) {
        return ownerName(p.server, p.getUUID());
    }

    private static String ownerName(MinecraftServer s, UUID id) {
        return fr.minenorth.api.MineNorth.identity().get(s, id).map(fr.minenorth.api.Identity::officialName)
                .orElseGet(() -> fr.minenorth.api.MineNorth.pseudo(s, id));
    }

    /** Prévient un joueur ; s'il est hors ligne, le message lui sera remis à sa prochaine connexion. */
    private static void notify(MinecraftServer s, UUID id, String text) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p != null) { tell(p, text); return; }
        AccueilData d = AccueilData.get(s);
        List<String> list = d.notices.computeIfAbsent(id, k -> new ArrayList<>());
        if (list.size() < 20) list.add(text);
        d.setDirty();
    }

    /** Texte saisi par un joueur : sans codes couleur (§) ni caractères de contrôle, espaces normalisés. */
    private static String clean(String v) {
        return v == null ? "" : v.replaceAll("[\\p{Cntrl}§]", " ").trim().replaceAll("\\s+", " ");
    }

    private static String date(long ms) { return new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(ms)); }

    private static String row(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (Object f : fields) sb.append(sb.length() > 0 ? ModNetwork.SEP : "").append(String.valueOf(f).replace(ModNetwork.SEP, " "));
        return sb.toString();
    }

    // ------------------------------------------------------------------ accueil (citoyens)
    public static void openAccueil(ServerPlayer p) {
        List<ModNetwork.Person> players = new ArrayList<>();
        for (ServerPlayer q : p.server.getPlayerList().getPlayers()) if (q != p) players.add(new ModNetwork.Person(q.getUUID(), display(q)));
        players.sort(Comparator.comparing(x -> x.name().toLowerCase(Locale.ROOT)));
        ACCUEIL.add(p.getUUID());
        ModNetwork.send(p, new ModNetwork.AccueilPacket(players));
    }

    private static void citizen(ServerPlayer p, ActionPacket k) {
        if (k.action() == ModNetwork.C_CLOSE) { ACCUEIL.remove(p.getUUID()); return; }
        // Une seule action par passage au guichet, et seulement si le PNJ a ouvert le menu.
        if (!ACCUEIL.remove(p.getUUID())) return;
        MinecraftServer s = p.server;
        AccueilData d = AccueilData.get(s);
        switch (k.action()) {
            case ModNetwork.C_COMPLAINT -> {
                String reason = clean(k.text());
                if (reason.length() < 5) { tell(p, "§cÉcrivez la raison de votre plainte."); return; }
                String targetName = "Inconnu (X)";
                if (!ModNetwork.NONE.equals(k.target())) {
                    ServerPlayer target = s.getPlayerList().getPlayer(k.target());
                    if (target == null || target == p) { tell(p, "§cCette personne n'est plus connectée : recommencez."); return; }
                    targetName = display(target);
                }
                AccueilData.Complaint c = new AccueilData.Complaint();
                c.id = d.nextId(); c.plaintiff = p.getUUID(); c.plaintiffName = display(p); c.targetName = targetName;
                c.reason = reason; c.time = System.currentTimeMillis();
                d.complaints.add(c);
                // On garde au plus 400 plaintes : les plus anciennes fermées partent en premier.
                while (d.complaints.size() > 2 * MAX) {
                    AccueilData.Complaint oldest = null;
                    for (AccueilData.Complaint x : d.complaints) if (x.status == AccueilData.CLOSED) { oldest = x; break; }
                    d.complaints.remove(oldest == null ? d.complaints.get(0) : oldest);
                }
                d.setDirty();
                tell(p, "§aVotre plainte a été transmise à la police.");
                tellPolice(s, "§e[Accueil] Nouvelle plainte de " + c.plaintiffName + " contre " + targetName + ".");
            }
            case ModNetwork.C_APPOINTMENT -> {
                String[] parts = k.text().split(ModNetwork.SEP, 2);
                String reason = clean(parts[0]);
                String when = parts.length > 1 ? clean(parts[1]) : "";
                if (reason.length() < 5) { tell(p, "§cÉcrivez le motif de votre rendez-vous."); return; }
                for (AccueilData.Appointment a : d.appointments) {
                    if (a.citizen.equals(p.getUUID())) { tell(p, "§cVous avez déjà une demande de rendez-vous en cours."); return; }
                }
                if (d.appointments.size() >= MAX) { tell(p, "§cTrop de demandes en attente : réessayez plus tard."); return; }
                AccueilData.Appointment a = new AccueilData.Appointment();
                a.id = d.nextId(); a.citizen = p.getUUID(); a.citizenName = display(p); a.reason = reason; a.when = when; a.time = System.currentTimeMillis();
                d.appointments.add(a);
                d.setDirty();
                tell(p, "§aVotre demande de rendez-vous est enregistrée. Un policier vous préviendra quand il la prendra.");
                tellPolice(s, "§e[Accueil] " + a.citizenName + " demande un rendez-vous : " + reason + (when.isEmpty() ? "" : " (" + when + ")"));
            }
            case ModNetwork.C_IMPOUND -> {
                if (!ModList.get().isLoaded("minenorth_rp_vehicles")) { tell(p, "§cLa fourrière n'est pas disponible."); return; }
                // Même menu que le PNJ de la fourrière du mod Véhicules.
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), "fourrieremenu " + p.getGameProfile().getName());
            }
            case ModNetwork.C_DEPOSIT -> {
                ItemStack held = p.getMainHandItem();
                if (held.isEmpty()) { tell(p, "§cTenez l'objet à déposer dans votre main avant de cliquer."); return; }
                if (d.found.size() >= MAX) { tell(p, "§cLe bureau des objets trouvés est plein."); return; }
                AccueilData.Found f = new AccueilData.Found();
                f.id = d.nextId(); f.item = held.save(new CompoundTag()); f.depositor = display(p); f.time = System.currentTimeMillis();
                String label = held.getCount() + "x " + held.getHoverName().getString();
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                d.found.add(f);
                d.setDirty();
                tell(p, "§aObjet déposé au bureau des objets trouvés : " + label + ".");
                tellPolice(s, "§b[Accueil] Objet trouvé déposé par " + f.depositor + " : " + label + ".");
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ bureau (police)
    public static void openDesk(ServerPlayer p) {
        if (!isPolice(p)) { p.displayClientMessage(Component.literal("§cVous n'avez pas accès à ce bureau."), true); return; }
        DESK.add(p.getUUID());
        sendDesk(p, ModNetwork.V_COMPLAINTS, "", true);
    }

    private static void sendDesk(ServerPlayer p, int view, String msg, boolean ok) {
        MinecraftServer s = p.server;
        AccueilData d = AccueilData.get(s);
        List<String> rows = new ArrayList<>();
        List<ModNetwork.Person> players = new ArrayList<>();
        if (view == ModNetwork.V_FOUND) {
            // Joueurs connectés à qui un objet peut être rendu.
            for (ServerPlayer q : s.getPlayerList().getPlayers()) players.add(new ModNetwork.Person(q.getUUID(), display(q)));
            players.sort(Comparator.comparing(x -> x.name().toLowerCase(Locale.ROOT)));
            // Champs : id, quantité, nom de l'objet, déposé par.
            for (int i = d.found.size() - 1; i >= 0; i--) {
                AccueilData.Found f = d.found.get(i);
                ItemStack st = ItemStack.of(f.item);
                rows.add(row(f.id, st.getCount(), st.isEmpty() ? "Objet inconnu" : st.getHoverName().getString(), f.depositor));
            }
        } else if (view == ModNetwork.V_IMPOUND) {
            // Champs : modèle, propriétaire.
            boolean loaded = ModList.get().isLoaded("minenorth_rp_vehicles");
            if (loaded) {
                try {
                    Class<?> c = Class.forName("com.minenorth.vehicles.fourriere.ImpoundData");
                    Object data = c.getMethod("get", MinecraftServer.class).invoke(null, s);
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) c.getField("vehicles").get(data)).entrySet()) {
                        if (!(e.getKey() instanceof UUID owner) || !(e.getValue() instanceof List<?> list)) continue;
                        String name = ownerName(s, owner);
                        for (Object v : list) rows.add(row(String.valueOf(v.getClass().getField("label").get(v)), name));
                    }
                } catch (Throwable ignored) {}
                rows.sort(Comparator.comparing(r -> r.toLowerCase(Locale.ROOT)));
            } else if (msg.isEmpty()) { msg = "Le mod Véhicules n'est pas installé."; ok = false; }
        } else if (view == ModNetwork.V_APPOINTMENTS) {
            // Champs : id, citoyen, motif, disponibilités, policier ("" = pas encore pris), date. Les plus anciennes d'abord.
            for (AccueilData.Appointment a : d.appointments) rows.add(row(a.id, a.citizenName, a.reason, a.when, a.officer, date(a.time)));
        } else if (view == ModNetwork.V_HISTORY) {
            // Plaintes fermées, les plus récentes d'abord. Champs : plaignant, visé, raison, fermée par, date de fermeture.
            List<AccueilData.Complaint> closed = new ArrayList<>();
            for (AccueilData.Complaint c : d.complaints) if (c.status == AccueilData.CLOSED) closed.add(c);
            closed.sort(Comparator.comparingLong((AccueilData.Complaint c) -> c.closedTime).reversed());
            for (AccueilData.Complaint c : closed) rows.add(row(c.plaintiffName, c.targetName, c.reason, c.closedBy, date(c.closedTime)));
        } else {
            // Plaintes en cours. Champs : id, statut, plaignant, visé, raison, policier.
            for (int i = d.complaints.size() - 1; i >= 0; i--) {
                AccueilData.Complaint c = d.complaints.get(i);
                if (c.status != AccueilData.CLOSED) rows.add(row(c.id, c.status, c.plaintiffName, c.targetName, c.reason, c.officer));
            }
        }
        ModNetwork.send(p, new ModNetwork.DeskPacket(view, msg, ok, rows, players));
    }

    private static void staff(ServerPlayer p, ActionPacket k) {
        if (k.action() == ModNetwork.S_CLOSE) { DESK.remove(p.getUUID()); return; }
        if (!DESK.contains(p.getUUID()) || !isPolice(p)) return;
        MinecraftServer s = p.server;
        AccueilData d = AccueilData.get(s);
        switch (k.action()) {
            case ModNetwork.S_VIEW -> sendDesk(p, Math.max(0, Math.min(4, k.n())), "", true);
            case ModNetwork.S_OPEN, ModNetwork.S_CLOSE_COMPLAINT -> {
                AccueilData.Complaint c = null;
                for (AccueilData.Complaint x : d.complaints) if (x.id == k.n() && x.status != AccueilData.CLOSED) c = x;
                if (c == null) { sendDesk(p, ModNetwork.V_COMPLAINTS, "Cette plainte est déjà fermée.", false); return; }
                if (k.action() == ModNetwork.S_OPEN) {
                    if (c.status != 0) { sendDesk(p, ModNetwork.V_COMPLAINTS, "La procédure est déjà ouverte.", false); return; }
                    c.status = 1; c.officer = display(p);
                    d.setDirty();
                    notify(s, c.plaintiff, "§aLa police a ouvert une procédure pour votre plainte contre " + c.targetName + ".");
                    sendDesk(p, ModNetwork.V_COMPLAINTS, "Procédure ouverte pour la plainte de " + c.plaintiffName + ".", true);
                } else {
                    // La plainte n'est plus supprimée : elle passe dans l'historique.
                    c.status = AccueilData.CLOSED; c.closedBy = display(p); c.closedTime = System.currentTimeMillis();
                    d.setDirty();
                    notify(s, c.plaintiff, "§eVotre plainte contre " + c.targetName + " a été fermée par la police.");
                    sendDesk(p, ModNetwork.V_COMPLAINTS, "Procédure fermée : la plainte est dans l'historique.", true);
                }
            }
            case ModNetwork.S_RETURN -> {
                AccueilData.Found f = null;
                for (AccueilData.Found x : d.found) if (x.id == k.n()) f = x;
                if (f == null) { sendDesk(p, ModNetwork.V_FOUND, "Cet objet n'est plus au bureau.", false); return; }
                ServerPlayer owner = s.getPlayerList().getPlayer(k.target());
                if (owner == null) { sendDesk(p, ModNetwork.V_FOUND, "Ce joueur n'est plus connecté.", false); return; }
                d.found.remove(f);
                d.setDirty();
                ItemStack st = ItemStack.of(f.item);
                String label = st.getCount() + "x " + st.getHoverName().getString();
                if (!st.isEmpty() && !owner.getInventory().add(st)) owner.drop(st, false);
                tell(owner, "§aLa police vous a rendu un objet trouvé : " + label + ".");
                sendDesk(p, ModNetwork.V_FOUND, "Objet rendu à " + display(owner) + " : " + label + ".", true);
            }
            case ModNetwork.S_APPT_TAKE, ModNetwork.S_APPT_DONE -> {
                AccueilData.Appointment a = null;
                for (AccueilData.Appointment x : d.appointments) if (x.id == k.n()) a = x;
                if (a == null) { sendDesk(p, ModNetwork.V_APPOINTMENTS, "Ce rendez-vous n'existe plus.", false); return; }
                if (k.action() == ModNetwork.S_APPT_TAKE) {
                    if (!a.officer.isEmpty()) { sendDesk(p, ModNetwork.V_APPOINTMENTS, "Ce rendez-vous est déjà pris par " + a.officer + ".", false); return; }
                    a.officer = display(p);
                    d.setDirty();
                    notify(s, a.citizen, "§aVotre rendez-vous est pris en charge par " + a.officer + ". Présentez-vous au commissariat.");
                    sendDesk(p, ModNetwork.V_APPOINTMENTS, "Vous prenez le rendez-vous de " + a.citizenName + ". Il est prévenu.", true);
                } else {
                    boolean refused = a.officer.isEmpty();
                    d.appointments.remove(a);
                    d.setDirty();
                    // Personne ne l'avait pris : c'est un refus, le citoyen est prévenu et peut refaire une demande.
                    if (refused) notify(s, a.citizen, "§eVotre demande de rendez-vous a été refusée par la police.");
                    sendDesk(p, ModNetwork.V_APPOINTMENTS, refused ? "Demande refusée : le citoyen est prévenu." : "Rendez-vous terminé et retiré de la liste.", true);
                }
            }
            case ModNetwork.S_TAKE -> {
                AccueilData.Found f = null;
                for (AccueilData.Found x : d.found) if (x.id == k.n()) f = x;
                if (f == null) { sendDesk(p, ModNetwork.V_FOUND, "Cet objet a déjà été récupéré.", false); return; }
                d.found.remove(f);
                d.setDirty();
                ItemStack st = ItemStack.of(f.item);
                String label = st.getCount() + "x " + st.getHoverName().getString();
                if (!st.isEmpty() && !p.getInventory().add(st)) p.drop(st, false);
                // Trace : les autres policiers sont prévenus et la console garde l'historique.
                tellPolice(s, "§7[Accueil] " + display(p) + " a retiré des objets trouvés : " + label + " (déposé par " + f.depositor + ").");
                com.mojang.logging.LogUtils.getLogger().info("[Accueil] {} ({}) a retiré l'objet trouvé #{} : {} (déposé par {})",
                        display(p), p.getGameProfile().getName(), f.id, label, f.depositor);
                sendDesk(p, ModNetwork.V_FOUND, "Objet récupéré : il est dans votre inventaire.", true);
            }
            default -> {}
        }
    }

    public static void handle(ServerPlayer p, ActionPacket k) {
        if (k.action() >= ModNetwork.S_VIEW) staff(p, k); else citizen(p, k);
    }

    // ------------------------------------------------------------------ bloc « bureau »
    private static String deskKey(Level level, BlockPos pos) { return level.dimension().location() + "|" + pos.asLong(); }

    @SubscribeEvent
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) return;
        AccueilData d = AccueilData.get(p.server);
        if (d.desks.isEmpty() || !d.desks.contains(deskKey(e.getLevel(), e.getPos()))) return;
        openDesk(p);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static int desk(CommandSourceStack source, boolean add) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        HitResult hit = p.pick(6.0, 0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Regardez un bloc (à moins de 6 blocs) avant de faire cette commande."));
            return 0;
        }
        AccueilData d = AccueilData.get(p.server);
        String key = deskKey(p.level(), block.getBlockPos());
        if (add) d.desks.add(key); else d.desks.remove(key);
        d.setDirty();
        source.sendSystemMessage(Component.literal(add ? "§aCe bloc ouvre maintenant le bureau de la police au clic droit."
                : "§aCe bloc n'ouvre plus le bureau de la police."));
        return 1;
    }

    // ------------------------------------------------------------------ commandes
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        // PNJ d'accueil : réservé à la console / aux OP, les joueurs passent par le PNJ.
        d.register(Commands.literal("policeaccueil").requires(s -> s.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    openAccueil(EntityArgument.getPlayer(c, "joueur"));
                    return 1;
                })));
        d.register(Commands.literal("policestaff")
                // Sans argument : le policier ouvre son propre bureau (refusé s'il n'est pas policier).
                .executes(c -> { openDesk(c.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("set").requires(s -> s.hasPermission(2)).executes(c -> desk(c.getSource(), true)))
                .then(Commands.literal("remove").requires(s -> s.hasPermission(2)).executes(c -> desk(c.getSource(), false)))
                // PNJ du bureau : ouvre le bureau chez ce joueur (qui doit quand même être policier).
                .then(Commands.argument("joueur", EntityArgument.player()).requires(s -> s.hasPermission(2)).executes(c -> {
                    openDesk(EntityArgument.getPlayer(c, "joueur"));
                    return 1;
                })));
    }

    /** Remet au joueur les réponses de la police arrivées pendant son absence. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        AccueilData d = AccueilData.get(p.server);
        List<String> lines = d.notices.remove(p.getUUID());
        if (lines == null || lines.isEmpty()) return;
        for (String line : lines) tell(p, line);
        d.setDirty();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        ACCUEIL.remove(e.getEntity().getUUID());
        DESK.remove(e.getEntity().getUUID());
    }
}
