package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.connection.StorableObject;
public final class BridgeConnectionState implements StorableObject {
    public volatile int chatSessionsReceived, signedChatsReceived, unsignedChatsReceived;
    public final RegistryTranslator registries;
    public final ItemTranslationSession items=new ItemTranslationSession();
    public final EntityMetadataTranslator entities;
    final VehicleMotionState vehicles=new VehicleMotionState();
    boolean vehicleImpulseTesting=Boolean.getBoolean("frontierbridge.vehicleImpulses");
    private InventoryHashTranslator hashes;
    public final java.util.concurrent.atomic.AtomicBoolean resyncPending=new java.util.concurrent.atomic.AtomicBoolean();
    public InventoryHashTranslator hashes(){if(hashes==null)hashes=new InventoryHashTranslator(registries.hashContext(false),registries.hashContext(true));return hashes;}
    public com.viaversion.viaversion.api.minecraft.item.Item inventoryItem(com.viaversion.viaversion.api.minecraft.item.Item original) {
        var translated=items.toClient(original);hashes().track(original,translated);return translated;
    }
    private int dimension=-1;
    private Integer playerId;
    private byte[] pendingCommands;
    public void commands(byte[] bytes){pendingCommands=bytes.clone();}
    public byte[] takeCommands(){byte[] bytes=pendingCommands;pendingCommands=null;return bytes;}
    private static final int PLAYER_TYPE=new ItemMappingData().getEntityMappings().id("player");
    public BridgeConnectionState(BridgeMappings mappings) {registries=new RegistryTranslator(mappings);entities=new EntityMetadataTranslator(mappings);}
    public void player(int id){playerId=id;}
    public boolean isPlayer(int id){return playerId!=null&&playerId==id;}
    public void enter(int id) {registries.dimension(id);dimension=id;entities.clear();vehicles.clear();if(playerId!=null)entities.track(playerId,PLAYER_TYPE);}
    public int sections() {if(dimension<0)throw new IllegalStateException("Login dimension not received");return registries.dimension(dimension).sections();}
    public void reset() {dimension=-1;playerId=null;pendingCommands=null;registries.reset();items.clear();entities.clear();vehicles.clear();if(hashes!=null)hashes.clear();hashes=null;}
}
