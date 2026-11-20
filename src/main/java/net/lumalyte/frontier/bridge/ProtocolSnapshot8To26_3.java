package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.exception.CancelException;
import com.viaversion.viaversion.exception.InformativeException;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import com.viaversion.viaversion.util.ProtocolLogger;
import java.util.logging.Logger;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundConfigurationPackets26_3;
import com.viaversion.viaversion.protocols.v1_21_7to1_21_9.packet.ServerboundConfigurationPackets1_21_9;
import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.buffer.Unpooled;

/** Experimental translator kernel. Not registered as a live Via protocol yet.
 * Unimplemented packets are rejected rather than implicitly passed through.
 */
public final class ProtocolSnapshot8To26_3 extends AbstractProtocol<ClientboundPacketType, ClientboundPacketType, ServerboundPacketType, ServerboundPacketType> {
    private final BridgeMappings mappings;
    private final CodecContext source=new CodecContext(true),target=new CodecContext(false);
    private final java.util.function.Consumer<UserConnection> inventoryResync;
    private final com.viaversion.viaversion.api.type.types.version.VersionedTypesHolder clientTypes;
    public ProtocolSnapshot8To26_3(BridgeMappings mappings) {
        this(mappings,user->{throw new IllegalStateException("Inventory resync callback not installed");});
    }
    public ProtocolSnapshot8To26_3(BridgeMappings mappings,java.util.function.Consumer<UserConnection> inventoryResync) {
        this(mappings,inventoryResync,null);
    }
    public ProtocolSnapshot8To26_3(BridgeMappings mappings,java.util.function.Consumer<UserConnection> inventoryResync,com.viaversion.viaversion.api.type.types.version.VersionedTypesHolder clientTypes) {
        super(null, null, null, null);
        this.mappings = mappings;
        this.inventoryResync=java.util.Objects.requireNonNull(inventoryResync);
        this.clientTypes=clientTypes==null?target.types:clientTypes;
    }
    static int nativeClientboundId(State state,int oldId) {
        if(state!=State.PLAY)return oldId;
        String key=ClientboundPackets26_3.values()[oldId].name().toLowerCase(java.util.Locale.ROOT);
        if(key.equals("tracked_waypoint"))key="waypoint";
        if(key.startsWith("debug_")&&!key.equals("debug_sample"))key="debug/"+key.substring(6);
        for(var row:ItemMappingData.resource("snapshot-packets.json").getAsJsonObject("play").getAsJsonArray("CLIENTBOUND")) {
            var packet=row.getAsJsonObject();
            if(packet.get("key").getAsString().equals("minecraft:"+key))return packet.get("id").getAsInt();
        }
        throw new IllegalArgumentException("No snapshot-8 packet for "+key);
    }
    @Override public void registerClientbound(State state,int unmapped,int mapped,com.viaversion.viaversion.api.protocol.remapper.PacketHandler handler,boolean override) {
        super.registerClientbound(state,nativeClientboundId(state,unmapped),mapped,handler,override);
    }
    void registerNativeClientbound(State state,int nativeId,int mapped,com.viaversion.viaversion.api.protocol.remapper.PacketHandler handler) {
        super.registerClientbound(state,nativeId,mapped,handler,false);
    }
    @Override protected ProtocolLogger createLogger() { return new ProtocolLogger(Logger.getLogger("FrontierBridge"), getClass()); }
    // No shared handlers are appropriate before the complete versioned protocol is registered.
    @Override protected void applySharedRegistrations() { }
    @Override public void init(UserConnection user) {user.put(new BridgeConnectionState(mappings));if(Boolean.getBoolean("frontierbridge.network"))LegacyLoginTransport.install(user);}
    private BridgeConnectionState connection(PacketWrapper wrapper) {
        var state=wrapper.user().get(BridgeConnectionState.class);
        if(state==null)throw new IllegalStateException("Bridge connection not initialized");return state;
    }

    @Override protected void registerPackets() {
        LoginPackets.register(this);
        SessionPackets.register(this);
        CommonPackets.register(this);
        BasicPlayPackets.register(this);
        InteractionPackets.register(this);
        ScreenPackets.register(this);
        MovementPackets.register(this);
        ImpulsePackets.register(this,clientTypes);
        StatePackets.register(this);
        UtilityPackets.register(this);
        CombatPackets.register(this);
        RemainingControlPackets.register(this);
        ChatPackets.register(this);
        RemainingWorldPackets.register(this);
        DebugPackets.register(this);
        new RemainingItemPackets(source,target,clientTypes).register(this);
        WorldEntryPackets.register(this);
        new RecipePackets(source,target,clientTypes).register(this);
        register(ClientboundPackets26_3.LEVEL_PARTICLES,w->{
            var state=connection(w);var particle=state.entities.particle(w.read(source.types.particle),state.items);
            var bytes=Unpooled.buffer();try{target.types.particle.write(bytes,particle);w.write(clientTypes.particle(),clientTypes.particle().read(bytes));}finally{bytes.release();}
            for(int i=0;i<2;i++)w.passthrough(Types.BOOLEAN);
            for(int i=0;i<3;i++)w.passthrough(Types.DOUBLE);
            for(int i=0;i<6;i++)w.passthrough(Types.FLOAT);
            w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);
        });
        int loaded=com.viaversion.viaversion.protocols.v26_2to26_3.packet.ServerboundPackets26_3.PLAYER_LOADED.getId();
        registerServerbound(State.PLAY,loaded,loaded,w->{},false); // Native snapshot/26.3 codec is an empty unit packet.
        int tags=ClientboundConfigurationPackets26_3.UPDATE_TAGS.getId();
        registerClientbound(State.CONFIGURATION,tags,tags,this::rewriteTags,false);
        register(ClientboundPackets26_3.UPDATE_TAGS,this::rewriteTags);
        int click=com.viaversion.viaversion.protocols.v26_2to26_3.packet.ServerboundPackets26_3.CONTAINER_CLICK.getId();
        registerServerbound(State.PLAY,click,click,wrapper->{
            var in=Unpooled.buffer();var out=Unpooled.buffer();
            try {
                copyClickFromWrapper(wrapper,in);
                if(!connection(wrapper).hashes().rewriteClick(in,out)){inventoryResync.accept(wrapper.user());wrapper.cancel();return;}
                writeClickToWrapper(out,wrapper);
            }finally{in.release();out.release();}
        },false);
        int creative=com.viaversion.viaversion.protocols.v26_2to26_3.packet.ServerboundPackets26_3.SET_CREATIVE_MODE_SLOT.getId();
        registerServerbound(State.PLAY,creative,creative,wrapper->{
            wrapper.passthrough(Types.SHORT);
            var clientItem=wrapper.read(target.types.lengthPrefixedItem);
            com.viaversion.viaversion.api.minecraft.item.Item restored;
            try { restored=connection(wrapper).items.toServer(clientItem); }
            catch (IllegalArgumentException rejected) {
                // A stale/modified creative echo is rejected, not a fatal connection error.
                wrapper.cancel();inventoryResync.accept(wrapper.user());return;
            }
            wrapper.write(source.types.lengthPrefixedItem,restored);
        },false);
        register(ClientboundPackets26_3.CONTAINER_SET_CONTENT,wrapper->{
            wrapper.passthrough(Types.VAR_INT);wrapper.passthrough(Types.VAR_INT);
            var items=wrapper.read(source.types.itemArray);
            for(int i=0;i<items.length;i++)items[i]=canonicalItem(connection(wrapper).inventoryItem(items[i]));
            wrapper.write(clientTypes.itemArray(),items);itemToClient(wrapper);
        });
        register(ClientboundPackets26_3.CONTAINER_SET_SLOT,wrapper->{
            wrapper.passthrough(Types.VAR_INT);wrapper.passthrough(Types.VAR_INT);wrapper.passthrough(Types.SHORT);itemToClient(wrapper);
        });
        register(ClientboundPackets26_3.SET_PLAYER_INVENTORY,wrapper->{wrapper.passthrough(Types.VAR_INT);itemToClient(wrapper);});
        register(ClientboundPackets26_3.SET_CURSOR_ITEM,this::itemToClient);
        register(ClientboundPackets26_3.SET_EQUIPMENT,wrapper->{
            wrapper.passthrough(Types.VAR_INT);byte slot;int count=0;
            do {if(++count>8)throw new IllegalArgumentException("Too many equipment slots");slot=wrapper.passthrough(Types.BYTE);itemToClient(wrapper);}while(slot<0);
        });
        register(ClientboundPackets26_3.SET_ENTITY_DATA,wrapper->{
            int id=wrapper.passthrough(Types.VAR_INT);var state=connection(wrapper);
            var metadata=state.entities.rewrite(id,wrapper.read(source.types.entityDataList),state.items);
            var bytes=Unpooled.buffer();try{target.types.entityDataList.write(bytes,metadata);wrapper.write(clientTypes.entityDataList(),clientTypes.entityDataList().read(bytes));}finally{bytes.release();}
        });
        register(ClientboundPackets26_3.REMOVE_ENTITIES,wrapper->{for(int id:wrapper.passthrough(Types.VAR_INT_ARRAY_PRIMITIVE)){connection(wrapper).entities.remove(id);connection(wrapper).vehicles.remove(id);}});
        int registryPacket=ClientboundConfigurationPackets26_3.REGISTRY_DATA.getId();
        registerClientbound(State.CONFIGURATION,registryPacket,registryPacket,wrapper->{
            var input=Unpooled.wrappedBuffer(wrapper.read(Types.REMAINING_BYTES));var output=Unpooled.buffer();
            try {
                if(!connection(wrapper).registries.rewrite(input,output)){wrapper.cancel();return;}
                wrapper.write(Types.STRING,Types.STRING.read(output));wrapper.write(Types.REGISTRY_ENTRY_ARRAY,Types.REGISTRY_ENTRY_ARRAY.read(output));
            }finally{input.release();output.release();}
        },false);
        int packs=ServerboundConfigurationPackets1_21_9.SELECT_KNOWN_PACKS.getId();
        registerServerbound(State.CONFIGURATION,packs,packs,wrapper->{wrapper.read(Types.REMAINING_BYTES);wrapper.write(Types.VAR_INT,0);},false);
        register(ClientboundPackets26_3.START_CONFIGURATION,wrapper->{connection(wrapper).reset();wrapper.user().getProtocolInfo().setServerState(State.CONFIGURATION);});
        register(ClientboundPackets26_3.LOGIN,wrapper->{
            connection(wrapper).player(wrapper.passthrough(Types.INT));wrapper.passthrough(Types.BOOLEAN);wrapper.passthrough(Types.STRING_ARRAY);
            for(int i=0;i<3;i++)wrapper.passthrough(Types.VAR_INT);
            for(int i=0;i<3;i++)wrapper.passthrough(Types.BOOLEAN);
            spawnContext(wrapper);
            WorldEntryPackets.sendPendingCommands(wrapper);
        });
        register(ClientboundPackets26_3.RESPAWN,this::spawnContext);
        register(ClientboundPackets26_3.LIGHT_UPDATE,wrapper->{
            wrapper.passthrough(Types.VAR_INT);wrapper.passthrough(Types.VAR_INT); // Chunk X/Z
            for(int i=0;i<4;i++)wrapper.passthrough(Types.BIT_SET);
            for(int layer=0;layer<2;layer++) {
                int count=wrapper.passthrough(Types.VAR_INT);
                if(count<0||count>connection(wrapper).sections()+2)throw new IllegalArgumentException("Invalid light array count");
                for(int i=0;i<count;i++)wrapper.passthrough(Types.BYTE_ARRAY_PRIMITIVE);
            }
        });
        register(ClientboundPackets26_3.LEVEL_CHUNK_WITH_LIGHT,wrapper->{
            var state=connection(wrapper);var input=Unpooled.wrappedBuffer(wrapper.read(Types.REMAINING_BYTES));var output=Unpooled.buffer();
            try {
                new ChunkTranslator(mappings).rewrite(input,output,state.sections(),state.registries.biomeCount());
                var type=new com.viaversion.viaversion.api.type.types.chunk.ChunkType26_1(state.sections(),ChunkTranslator.bits(mappings.oldStateCount()),ChunkTranslator.bits(state.registries.biomeCount()));
                wrapper.write(type,type.read(output));
                // Subsequent Via handlers consume typed masks, not an opaque byte tail.
                for(int i=0;i<4;i++)wrapper.write(Types.BIT_SET,Types.BIT_SET.read(output));
                for(int layer=0;layer<2;layer++) {
                    int count=Types.VAR_INT.read(output);
                    if(count<0||count>state.sections()+2)throw new IllegalArgumentException("Invalid light array count");
                    wrapper.write(Types.VAR_INT,count);
                    for(int i=0;i<count;i++)wrapper.write(Types.BYTE_ARRAY_PRIMITIVE,Types.BYTE_ARRAY_PRIMITIVE.read(output));
                }
                if(output.isReadable())throw new IllegalArgumentException("Trailing chunk light bytes");
            }finally{input.release();output.release();}
        });
        register(ClientboundPackets26_3.ADD_ENTITY, wrapper -> {
            int entityId=wrapper.passthrough(Types.VAR_INT);wrapper.passthrough(Types.UUID);
            int type=wrapper.read(Types.VAR_INT);wrapper.write(Types.VAR_INT,mappings.entity(type));
            if(wrapper.user()!=null){connection(wrapper).entities.track(entityId,type);connection(wrapper).vehicles.spawn(entityId);}
            wrapper.passthrough(Types.DOUBLE);wrapper.passthrough(Types.DOUBLE);wrapper.passthrough(Types.DOUBLE);
            wrapper.passthrough(Types.LOW_PRECISION_VECTOR);
            wrapper.passthrough(Types.BYTE);wrapper.passthrough(Types.BYTE);wrapper.passthrough(Types.BYTE);
            int data=wrapper.read(Types.VAR_INT);wrapper.write(Types.VAR_INT,mappings.fallingBlock(type)?mappings.state(data):data);
        });
        register(ClientboundPackets26_3.BLOCK_UPDATE, wrapper -> {
            wrapper.passthrough(Types.BLOCK_POSITION1_14);
            wrapper.write(Types.VAR_INT, mappings.state(wrapper.read(Types.VAR_INT)));
        });
        register(ClientboundPackets26_3.SECTION_BLOCKS_UPDATE, wrapper -> {
            wrapper.passthrough(Types.LONG); // Packed SectionPos
            var records=wrapper.passthrough(Types.VAR_LONG_BLOCK_CHANGE_ARRAY);
            if(records.length>4096)throw new IllegalArgumentException("Invalid section change count");
            for(var record:records)record.setBlockId(mappings.state(record.getBlockId()));
        });
        register(ClientboundPackets26_3.BLOCK_EVENT, wrapper -> {
            wrapper.passthrough(Types.BLOCK_POSITION1_14);
            wrapper.passthrough(Types.UNSIGNED_BYTE);
            wrapper.passthrough(Types.UNSIGNED_BYTE);
            wrapper.write(Types.VAR_INT, mappings.block(wrapper.read(Types.VAR_INT)));
        });
        register(ClientboundPackets26_3.LEVEL_EVENT, wrapper -> {
            int event = wrapper.read(Types.INT);
            // New sound-only events use the closest existing 26.3 event.
            event=switch(event){case 1055->1045;case 1056->1040;case 1057,1058->1041;default->event;};
            wrapper.write(Types.INT,event);
            wrapper.passthrough(Types.BLOCK_POSITION1_14);
            int data = wrapper.read(Types.INT);
            // Other events use colours, directions or stable dynamic registry IDs.
            wrapper.write(Types.INT, event==2001||event==2014||event==3008 ? mappings.state(data) : data);
            wrapper.passthrough(Types.BOOLEAN);
        });
        register(ClientboundPackets26_3.SOUND, this::rewriteSound);
        register(ClientboundPackets26_3.SOUND_ENTITY, this::rewriteSound);
        register(ClientboundPackets26_3.STOP_SOUND, wrapper -> {
            byte flags = wrapper.passthrough(Types.BYTE);
            if ((flags & 1) != 0) wrapper.passthrough(Types.VAR_INT);
            if ((flags & 2) != 0) wrapper.write(Types.STRING,mappings.soundName(wrapper.read(Types.STRING)));
        });
    }

    private void itemToClient(PacketWrapper wrapper)throws InformativeException {
        wrapper.write(clientTypes.item(),canonicalItem(connection(wrapper).inventoryItem(wrapper.read(source.types.item))));
    }
    private com.viaversion.viaversion.api.minecraft.item.Item canonicalItem(com.viaversion.viaversion.api.minecraft.item.Item item) {
        var bytes=Unpooled.buffer();try{target.types.item.write(bytes,item);return clientTypes.item().read(bytes);}finally{bytes.release();}
    }
    private void rewriteTags(PacketWrapper wrapper)throws InformativeException {
        var input=Unpooled.wrappedBuffer(wrapper.read(Types.REMAINING_BYTES));var output=Unpooled.buffer();
        try {
            new TagTranslator().rewrite(input,output,connection(wrapper).registries);
            int registries=Types.VAR_INT.readPrimitive(output);wrapper.write(Types.VAR_INT,registries);
            for(int r=0;r<registries;r++){wrapper.write(Types.STRING,Types.STRING.read(output));int tags=Types.VAR_INT.readPrimitive(output);wrapper.write(Types.VAR_INT,tags);for(int t=0;t<tags;t++){wrapper.write(Types.STRING,Types.STRING.read(output));wrapper.write(Types.VAR_INT_ARRAY_PRIMITIVE,Types.VAR_INT_ARRAY_PRIMITIVE.read(output));}}
        }
        finally{input.release();output.release();}
    }
    private void copyClickFromWrapper(PacketWrapper w,io.netty.buffer.ByteBuf b) {
        Types.VAR_INT.writePrimitive(b,w.read(Types.VAR_INT));Types.VAR_INT.writePrimitive(b,w.read(Types.VAR_INT));b.writeShort(w.read(Types.SHORT)).writeByte(w.read(Types.BYTE));Types.VAR_INT.writePrimitive(b,w.read(Types.VAR_INT));
        int n=w.read(Types.VAR_INT);if(n<0||n>128)throw new IllegalArgumentException("Invalid changed-slot count");Types.VAR_INT.writePrimitive(b,n);
        for(int i=0;i<n;i++){b.writeShort(w.read(Types.SHORT));Types.HASHED_ITEM.write(b,w.read(Types.HASHED_ITEM));}Types.HASHED_ITEM.write(b,w.read(Types.HASHED_ITEM));
    }
    private void writeClickToWrapper(io.netty.buffer.ByteBuf b,PacketWrapper w) {
        w.write(Types.VAR_INT,Types.VAR_INT.readPrimitive(b));w.write(Types.VAR_INT,Types.VAR_INT.readPrimitive(b));w.write(Types.SHORT,b.readShort());w.write(Types.BYTE,b.readByte());w.write(Types.VAR_INT,Types.VAR_INT.readPrimitive(b));int n=Types.VAR_INT.readPrimitive(b);w.write(Types.VAR_INT,n);
        for(int i=0;i<n;i++){w.write(Types.SHORT,b.readShort());w.write(Types.HASHED_ITEM,Types.HASHED_ITEM.read(b));}w.write(Types.HASHED_ITEM,Types.HASHED_ITEM.read(b));
    }

    private void spawnContext(PacketWrapper wrapper) throws InformativeException {
        int dimension=wrapper.passthrough(Types.VAR_INT);wrapper.passthrough(Types.STRING);
        connection(wrapper).enter(dimension);
        wrapper.write(Types.LONG,0L); // 26.4 removed the legacy biome seed; 26.3 still requires this field.
        wrapper.passthrough(Types.VAR_INT);wrapper.passthrough(Types.OPTIONAL_VAR_INT); // Typed fields must survive subsequent Via handlers.
        wrapper.write(Types.BOOLEAN,false); // Snapshot 8 removed the debug-world flag.
    }

    private void rewriteSound(PacketWrapper wrapper) throws InformativeException {
        var holder=wrapper.read(Types.SOUND_EVENT).updateId(mappings::sound)
            .updateValue(sound->new com.viaversion.viaversion.api.minecraft.SoundEvent(mappings.soundName(sound.identifier()),sound.fixedRange()));
        wrapper.write(Types.SOUND_EVENT,holder);
    }

    private void register(ClientboundPackets26_3 packet, com.viaversion.viaversion.api.protocol.remapper.PacketHandler handler) {
        registerClientbound(State.PLAY, packet.getId(), packet.getId(), handler, false);
    }

    @Override public void transform(Direction direction, State state, PacketWrapper wrapper) throws InformativeException, CancelException {
        if (!(direction==Direction.CLIENTBOUND ? hasRegisteredClientbound(state,wrapper.getId()) : hasRegisteredServerbound(state,wrapper.getId())))
            throw new IllegalArgumentException("Unimplemented bridge packet " + direction + "/" + state + "/" + wrapper.getId());
        super.transform(direction, state, wrapper);
    }
}
