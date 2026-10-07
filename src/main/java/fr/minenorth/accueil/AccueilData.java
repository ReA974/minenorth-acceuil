package fr.minenorth.accueil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Plaintes, objets trouvés et blocs « bureau ». Sauvegardé avec le monde. */
public class AccueilData extends SavedData {
    private static final String NAME = "minenorth_accueil_police";

    public static final int WAITING = 0, OPEN = 1, CLOSED = 2;

    /** status : WAITING = en attente, OPEN = procédure ouverte, CLOSED = fermée (gardée dans l'historique). */
    public static final class Complaint {
        public int id, status; public UUID plaintiff; public String plaintiffName = "", targetName = "", reason = "", officer = "", closedBy = "";
        public long time, closedTime;
    }
    /** Demande de rendez-vous avec un policier. officer vide = personne ne l'a encore prise. */
    public static final class Appointment {
        public int id; public UUID citizen; public String citizenName = "", reason = "", when = "", officer = ""; public long time;
    }
    /** L'objet complet (enchantements, nom, contenu) est gardé en NBT. */
    public static final class Found {
        public int id; public CompoundTag item = new CompoundTag(); public String depositor = ""; public long time;
    }

    public final List<Complaint> complaints = new ArrayList<>();
    public final List<Found> found = new ArrayList<>();
    public final List<Appointment> appointments = new ArrayList<>();
    /** Messages à remettre à un joueur à sa prochaine connexion (il était hors ligne quand la police a répondu). */
    public final java.util.Map<UUID, List<String>> notices = new java.util.HashMap<>();
    /** Blocs qui ouvrent le bureau de la police au clic droit : "dimension|position". */
    public final Set<String> desks = new HashSet<>();
    private int nextId = 1;

    public static AccueilData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AccueilData::load, AccueilData::new, NAME);
    }

    public int nextId() { setDirty(); return nextId++; }

    public static AccueilData load(CompoundTag tag) {
        AccueilData d = new AccueilData();
        d.nextId = Math.max(1, tag.getInt("nextId"));
        ListTag cl = tag.getList("complaints", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag t = cl.getCompound(i);
            Complaint c = new Complaint();
            c.id = t.getInt("id"); c.status = t.getInt("status"); c.plaintiff = t.getUUID("plaintiff");
            c.plaintiffName = t.getString("plaintiffName"); c.targetName = t.getString("targetName");
            c.reason = t.getString("reason"); c.officer = t.getString("officer"); c.time = t.getLong("time");
            c.closedBy = t.getString("closedBy"); c.closedTime = t.getLong("closedTime");
            d.complaints.add(c);
        }
        ListTag al = tag.getList("appointments", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) {
            CompoundTag t = al.getCompound(i);
            Appointment a = new Appointment();
            a.id = t.getInt("id"); a.citizen = t.getUUID("citizen"); a.citizenName = t.getString("citizenName");
            a.reason = t.getString("reason"); a.when = t.getString("when"); a.officer = t.getString("officer"); a.time = t.getLong("time");
            d.appointments.add(a);
        }
        ListTag nl = tag.getList("notices", Tag.TAG_COMPOUND);
        for (int i = 0; i < nl.size(); i++) {
            CompoundTag t = nl.getCompound(i);
            List<String> lines = new ArrayList<>();
            ListTag ll = t.getList("lines", Tag.TAG_STRING);
            for (int k = 0; k < ll.size(); k++) lines.add(ll.getString(k));
            d.notices.put(t.getUUID("id"), lines);
        }
        ListTag fl = tag.getList("found", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            CompoundTag t = fl.getCompound(i);
            Found f = new Found();
            f.id = t.getInt("id"); f.item = t.getCompound("item"); f.depositor = t.getString("depositor"); f.time = t.getLong("time");
            d.found.add(f);
        }
        ListTag dl = tag.getList("desks", Tag.TAG_STRING);
        for (int i = 0; i < dl.size(); i++) d.desks.add(dl.getString(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextId", nextId);
        ListTag cl = new ListTag();
        for (Complaint c : complaints) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id); t.putInt("status", c.status); t.putUUID("plaintiff", c.plaintiff);
            t.putString("plaintiffName", c.plaintiffName); t.putString("targetName", c.targetName);
            t.putString("reason", c.reason); t.putString("officer", c.officer); t.putLong("time", c.time);
            t.putString("closedBy", c.closedBy); t.putLong("closedTime", c.closedTime);
            cl.add(t);
        }
        tag.put("complaints", cl);
        ListTag al = new ListTag();
        for (Appointment a : appointments) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", a.id); t.putUUID("citizen", a.citizen); t.putString("citizenName", a.citizenName);
            t.putString("reason", a.reason); t.putString("when", a.when); t.putString("officer", a.officer); t.putLong("time", a.time);
            al.add(t);
        }
        tag.put("appointments", al);
        ListTag nl = new ListTag();
        notices.forEach((id, lines) -> {
            if (lines.isEmpty()) return;
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            ListTag ll = new ListTag();
            for (String line : lines) ll.add(StringTag.valueOf(line));
            t.put("lines", ll);
            nl.add(t);
        });
        tag.put("notices", nl);
        ListTag fl = new ListTag();
        for (Found f : found) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", f.id); t.put("item", f.item); t.putString("depositor", f.depositor); t.putLong("time", f.time);
            fl.add(t);
        }
        tag.put("found", fl);
        ListTag dl = new ListTag();
        for (String desk : desks) dl.add(StringTag.valueOf(desk));
        tag.put("desks", dl);
        return tag;
    }
}
