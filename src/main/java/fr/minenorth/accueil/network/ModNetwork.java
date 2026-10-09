package fr.minenorth.accueil.network;

import fr.minenorth.accueil.AccueilService;
import fr.minenorth.accueil.MineNorthAccueil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModNetwork {
    private ModNetwork() {}

    /** Onglets du bureau (staff police). */
    public static final int V_COMPLAINTS = 0, V_FOUND = 1, V_IMPOUND = 2, V_HISTORY = 3, V_APPOINTMENTS = 4;
    /** Actions des citoyens (accueil). */
    public static final int C_COMPLAINT = 1, C_IMPOUND = 2, C_DEPOSIT = 3, C_CLOSE = 4, C_APPOINTMENT = 5, C_DUTY_POLICE = 6, C_DUTY_SECOURS = 7, C_CALL = 8;
    /** Services appelables (n de C_CALL). */
    public static final int CALL_POLICE = 0, CALL_MAYOR = 1, CALL_FIRE = 2;
    /** Service du joueur dans l'accueil : pas membre / hors service / en service. */
    public static final int DUTY_NONE = -1, DUTY_OFF = 0, DUTY_ON = 1;
    /** Actions du bureau. */
    public static final int S_VIEW = 10, S_OPEN = 11, S_CLOSE_COMPLAINT = 12, S_TAKE = 13, S_CLOSE = 14, S_RETURN = 15, S_APPT_TAKE = 16, S_APPT_DONE = 17;
    public static final UUID NONE = new UUID(0, 0);
    /** Séparateur des champs dans les lignes du bureau. */
    public static final String SEP = "\u001F";

    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthAccueil.MOD_ID, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, AccueilPacket.class, AccueilPacket::encode, AccueilPacket::decode, AccueilPacket::handle);
        CHANNEL.registerMessage(id++, DeskPacket.class, DeskPacket::encode, DeskPacket::decode, DeskPacket::handle);
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
    }

    public static void send(ServerPlayer p, Object packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet); }

    public record Person(UUID id, String name) {
        static void encode(FriendlyByteBuf b, Person p) { b.writeUUID(p.id); b.writeUtf(p.name); }
        static Person decode(FriendlyByteBuf b) { return new Person(b.readUUID(), b.readUtf()); }
    }

    /** Ouvre l'accueil chez un citoyen. players = joueurs connectés contre qui il peut porter plainte ; police / secours = son service (DUTY_*). */
    public record AccueilPacket(List<Person> players, int police, int secours) {
        static void encode(AccueilPacket p, FriendlyByteBuf b) { b.writeCollection(p.players, Person::encode); b.writeVarInt(p.police + 1); b.writeVarInt(p.secours + 1); }
        static AccueilPacket decode(FriendlyByteBuf b) { return new AccueilPacket(b.readList(Person::decode), b.readVarInt() - 1, b.readVarInt() - 1); }
        static void handle(AccueilPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.accueil.client.ClientNetworkHandler.accueil(p)));
            c.get().setPacketHandled(true);
        }
    }

    /** Un onglet du bureau : lignes de texte dont les champs sont séparés par SEP. players = joueurs connectés (pour rendre un objet). */
    public record DeskPacket(int view, String message, boolean ok, List<String> rows, List<Person> players) {
        static void encode(DeskPacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.view); b.writeUtf(p.message); b.writeBoolean(p.ok); b.writeCollection(p.rows, (x, v) -> x.writeUtf(v));
            b.writeCollection(p.players, Person::encode);
        }
        static DeskPacket decode(FriendlyByteBuf b) {
            return new DeskPacket(b.readVarInt(), b.readUtf(), b.readBoolean(), b.readList(FriendlyByteBuf::readUtf), b.readList(Person::decode));
        }
        static void handle(DeskPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.accueil.client.ClientNetworkHandler.desk(p)));
            c.get().setPacketHandled(true);
        }
    }

    public record ActionPacket(int action, UUID target, String text, int n) {
        static void encode(ActionPacket p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeUUID(p.target); b.writeUtf(p.text, 200); b.writeInt(p.n); }
        static ActionPacket decode(FriendlyByteBuf b) { return new ActionPacket(b.readVarInt(), b.readUUID(), b.readUtf(200), b.readInt()); }
        static void handle(ActionPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) AccueilService.handle(sp, p); });
            c.get().setPacketHandled(true);
        }
    }
}
