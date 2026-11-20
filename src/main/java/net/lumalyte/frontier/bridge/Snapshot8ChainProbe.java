package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import com.viaversion.viaversion.exception.CancelException;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import com.viaversion.viaversion.api.minecraft.item.*;

/** Exercises actual installed ViaBackwards handlers in memory; never registers a public protocol edge. */
final class Snapshot8ChainProbe {
    static boolean run(Path fixturesPath,Path reportPath)throws Exception {
        var fixtures=JsonParser.parseString(Files.readString(fixturesPath)).getAsJsonObject();var rows=new ArrayList<Object>();boolean passed=true;
        var chunks=JsonParser.parseString(Files.readString(fixturesPath.resolveSibling("chain-chunks.json"))).getAsJsonArray();
        var tags=HexFormat.of().parseHex(JsonParser.parseString(Files.readString(fixturesPath.resolveSibling("chain-tags.json"))).getAsJsonObject().get("hex").getAsString());
        var particles=JsonParser.parseString(Files.readString(fixturesPath.resolveSibling("chain-particles.json"))).getAsJsonArray();
        for(var version:ProtocolVersion.getProtocols()) {
            if(!version.getName().startsWith("1.21")||version.olderThan(ProtocolVersion.v1_21_6))continue;
            String stage="initialize";int registries=0,cancelled=0,items=0,components=0;
            var channel=new io.netty.channel.embedded.EmbeddedChannel();
            channel.pipeline().addLast(Via.getManager().getInjector().getEncoderName(),new io.netty.channel.ChannelDuplexHandler());
            channel.pipeline().addLast(Via.getManager().getInjector().getDecoderName(),new io.netty.channel.ChannelDuplexHandler());
            try {
                var user=new UserConnectionImpl(channel);var info=user.getProtocolInfo();info.setProtocolVersion(version);info.setServerProtocolVersion(ProtocolVersion.getProtocol(BridgeMappings.SOURCE_PROTOCOL));info.setState(State.CONFIGURATION);
                var path=Via.getManager().getProtocolManager().getProtocolPath(version,ProtocolVersion.v26_3);
                var pipeline=new ProtocolPipelineImpl(user);List<Protocol> protocols=new ArrayList<>();
                for(var entry:path){pipeline.add(entry.protocol());protocols.add(entry.protocol());}
                var resyncRequests=new java.util.concurrent.atomic.AtomicInteger();
                var bridge=new ProtocolSnapshot8To26_3(new BridgeMappings(),u->resyncRequests.incrementAndGet(),VersionedTypes.V26_3);bridge.initialize();pipeline.add(bridge);protocols.add(bridge);Collections.reverse(protocols);
                for(var value:fixtures.getAsJsonArray("registries")) {
                    var row=value.getAsJsonObject();stage="registry "+row.get("key").getAsString();
                    if(apply(user,protocols,State.CONFIGURATION,ClientboundConfigurationPackets26_3.REGISTRY_DATA.getId(),HexFormat.of().parseHex(row.get("hex").getAsString())))registries++;else cancelled++;
                }
                stage="configuration tags";if(!apply(user,protocols,State.CONFIGURATION,ClientboundConfigurationPackets26_3.UPDATE_TAGS.getId(),tags))throw new IllegalStateException("Tags cancelled");
                stage="configuration commands";
                byte[] commands=HexFormat.of().parseHex(JsonParser.parseString(Files.readString(fixturesPath.resolveSibling("chain-commands.json"))).getAsJsonObject().get("hex").getAsString());
                if(apply(user,protocols,State.CONFIGURATION,21,commands))throw new IllegalStateException("Configuration command tree was not deferred");
                drainOutbound(channel);info.setState(State.PLAY);stage="login";
                var login=fixtures.getAsJsonArray("logins").get(0).getAsJsonObject();
                apply(user,protocols,State.PLAY,ClientboundPackets26_3.LOGIN.getId(),HexFormat.of().parseHex(login.get("login").getAsString()));
                channel.runPendingTasks();channel.checkException();
                int commandId=path.getFirst().protocol().getPacketTypesProvider().mappedClientboundType(State.PLAY,"COMMANDS").getId();
                boolean foundCommands=false;io.netty.buffer.ByteBuf queued;
                while((queued=channel.readOutbound())!=null){try{if(Types.VAR_INT.readPrimitive(queued)==commandId)foundCommands=true;}finally{queued.release();}}
                if(!foundCommands)throw new IllegalStateException("Deferred commands did not reach the older client pipeline");
                stage="snapshot-8 explosion effects";int explosionCases=explosions(user,protocols,path.getFirst().protocol(),fixturesPath.resolveSibling("chain-explosions.json"));
                stage="vehicle impulse candidate";int vehicleCases=vehicleCandidate(user,protocols,path.getFirst().protocol(),fixturesPath.resolveSibling("chain-vehicle-candidate.json"));
                stage="registered vehicle lifecycle";int lifecycleCases=VehicleLifecycleChainProbe.run(user,protocols,path.getFirst().protocol(),fixtures);
                var session=Map.of("legacySessionFixturesNotRun",true,"nativeExplosionCases",explosionCases,"vehicleImpulseCandidateCases",vehicleCases,"registeredVehicleLifecycleChecks",lifecycleCases,"vehicleLiveHandlerEnabled",false);
                stage="movement overflow splitting";int movementSplits=MovementChainProbe.run(user,protocols,path.getFirst().protocol(),channel);
                stage="snapshot-8 movement and head rotation";int movementCases=movement(user,protocols,path.getFirst().protocol(),channel,fixturesPath.resolveSibling("chain-movement.json"));
                stage="snapshot-8 player impulses";int impulseCases=impulses(user,protocols,path.getFirst().protocol(),fixturesPath.resolveSibling("chain-impulses.json"));
                int recipePackets=0;
                for(var value:JsonParser.parseString(Files.readString(fixturesPath.resolveSibling("chain-recipes.json"))).getAsJsonArray()) {
                    var row=value.getAsJsonObject();stage="recipe "+row.get("name").getAsString();
                    if(!apply(user,protocols,State.PLAY,ClientboundPackets26_3.valueOf(row.get("name").getAsString()).getId(),HexFormat.of().parseHex(row.get("hex").getAsString())))throw new IllegalStateException("Recipe cancelled");recipePackets++;
                }
                int terrain=0,metadata=0,particleCount=0;
                for(var dimension:fixtures.getAsJsonArray("logins")) {
                    var spawn=dimension.getAsJsonObject();stage="respawn "+spawn.get("world").getAsString();
                    if(!apply(user,protocols,State.PLAY,ClientboundPackets26_3.RESPAWN.getId(),HexFormat.of().parseHex(spawn.get("respawn").getAsString())))throw new IllegalStateException("Respawn cancelled");
                    for(var value:chunks){var chunk=value.getAsJsonObject();if(!chunk.get("world").equals(spawn.get("world")))continue;stage="chunk "+spawn.get("world").getAsString();if(!apply(user,protocols,State.PLAY,ClientboundPackets26_3.LEVEL_CHUNK_WITH_LIGHT.getId(),HexFormat.of().parseHex(chunk.get("hex").getAsString())))throw new IllegalStateException("Chunk cancelled");terrain++;stage="standalone light "+spawn.get("world").getAsString();lightRoundtrip(user,protocols,path.getFirst().protocol(),chunk);}
                }
                var entities=new HashMap<Integer,String>();for(var value:fixtures.getAsJsonArray("entities")){var row=value.getAsJsonObject();entities.put(row.get("type").getAsInt(),row.get("hex").getAsString());}
                for(var value:fixtures.getAsJsonArray("metadata")) {
                    var row=value.getAsJsonObject();if(row.has("schemaOnly"))continue;stage="entity "+row.get("key").getAsString();
                    boolean cushion=row.get("key").getAsString().equals("minecraft:cushion");
                    drainOutbound(channel);
                    boolean spawned=apply(user,protocols,State.PLAY,ClientboundPackets26_3.ADD_ENTITY.getId(),HexFormat.of().parseHex(entities.get(row.get("type").getAsInt())));
                    if(!spawned) {
                        if(!cushion)throw new IllegalStateException("Spawn cancelled");
                        // Via replaces cushions with falling-block visuals and no-gravity metadata.
                        var provider=path.getFirst().protocol().getPacketTypesProvider();
                        String spawnKind="ADD_ENTITY";
                        var allowed=new HashSet<Integer>();for(String kind:new String[]{spawnKind,"SET_ENTITY_DATA","SET_ENTITY_MOTION"})allowed.add(provider.mappedClientboundType(State.PLAY,kind).getId());
                        var received=new HashSet<Integer>();io.netty.buffer.ByteBuf generated;
                        while((generated=channel.readOutbound())!=null) {
                            try{int id=Types.VAR_INT.readPrimitive(generated);boolean first=received.add(id);if(!allowed.contains(id)||(!first&&id!=provider.mappedClientboundType(State.PLAY,"SET_ENTITY_MOTION").getId())||Types.VAR_INT.readPrimitive(generated)!=9123||!generated.isReadable())throw new IllegalStateException("Invalid generated spawn packet "+id);}
                            finally{generated.release();}
                        }
                        if(!received.contains(provider.mappedClientboundType(State.PLAY,spawnKind).getId())||!received.contains(provider.mappedClientboundType(State.PLAY,cushion?"SET_ENTITY_DATA":"SET_ENTITY_MOTION").getId()))throw new IllegalStateException("Missing generated spawn packets");
                    }
                    if(!apply(user,protocols,State.PLAY,ClientboundPackets26_3.SET_ENTITY_DATA.getId(),HexFormat.of().parseHex(row.get("hex").getAsString())))throw new IllegalStateException("Metadata cancelled");metadata++;
                    // Fixtures reuse one entity ID; retire it before the next type is spawned.
                    var removal=Unpooled.buffer();
                    try{Types.VAR_INT_ARRAY_PRIMITIVE.write(removal,new int[]{9123});byte[] bytes=new byte[removal.readableBytes()];removal.readBytes(bytes);if(!apply(user,protocols,State.PLAY,ClientboundPackets26_3.REMOVE_ENTITIES.getId(),bytes))throw new IllegalStateException("Entity removal cancelled");}
                    finally{removal.release();}
                }
                if(terrain!=3||metadata!=162)throw new IllegalStateException("Incomplete world fixture coverage");
                for(var value:particles){var row=value.getAsJsonObject();stage="particle "+row.get("key").getAsString();if(!apply(user,protocols,State.PLAY,ClientboundPackets26_3.LEVEL_PARTICLES.getId(),HexFormat.of().parseHex(row.get("hex").getAsString())))throw new IllegalStateException("Particle cancelled");particleCount++;}
                for(var value:fixtures.getAsJsonArray("items")) {
                    var row=value.getAsJsonObject();stage="cursor item "+row.get("id").getAsInt();
                    if(!apply(user,protocols,State.PLAY,ClientboundPackets26_3.SET_CURSOR_ITEM.getId(),HexFormat.of().parseHex(row.get("hex").getAsString())))throw new IllegalStateException("Inventory update cancelled");items++;
                }
                var codec=new CodecContext(true);var sample=new StructuredItem(new ItemMappingData().getFullItemMappings().id("stone"),1);
                for(var value:fixtures.getAsJsonArray("components")) {
                    var row=value.getAsJsonObject();stage="component "+row.get("key").getAsString();var b=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.get("hex").getAsString()));var out=Unpooled.buffer();
                    try{var data=codec.types.structuredData.read(b);var item=sample.copy();item.dataContainer().data().put(data.key(),data);codec.types.item.write(out,item);byte[] bytes=new byte[out.readableBytes()];out.readBytes(bytes);if(!apply(user,protocols,State.PLAY,ClientboundPackets26_3.SET_CURSOR_ITEM.getId(),bytes))throw new IllegalStateException("Component item cancelled");components++;}
                    finally{b.release();out.release();}
                }
                stage="nested creative roundtrip";
                var nested=sample.copy();nested.dataContainer().setIdLookup(codec,true);
                nested.dataContainer().set(codec.types.structuredDataKeys().container,new Item[]{new StructuredItem(new ItemMappingData().getFullItemMappings().id("ice_ball"),3)});
                roundtrip(user,protocols,path.getFirst().protocol(),codec,nested);
                stage="new-item inventory click";
                roundtrip(user,protocols,path.getFirst().protocol(),codec,new StructuredItem(new ItemMappingData().getFullItemMappings().id("ice_ball"),7));
                if(resyncRequests.get()!=2)throw new IllegalStateException("Expected exactly two expired-click resync callbacks");
                rows.add(Map.of("client",version.getName(),"registries",registries,"cancelledRegistries",cancelled,"inventoryPackets",items,"componentPackets",components,"nestedRoundtrip",true,"session",session,"inventoryClicks",Map.of("valid",4,"expiredCancelled",2,"resyncCallbacks",resyncRequests.get(),"survival",true),"worldPackets",Map.of("chunks",terrain,"entitySpawnsAndMetadata",metadata,"tagPackets",1,"particles",particleCount,"lightUpdates",terrain,"recipePackets",recipePackets,"overflowMovementCases",movementSplits,"movementAndHeadCases",movementCases,"playerImpulses",impulseCases,"deferredCommandsDelivered",true),"passed",true));
            }catch(Exception|LinkageError e){passed=false;Throwable cause=e;while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();rows.add(Map.of("client",version.getName(),"stage",stage,"registries",registries,"inventoryPackets",items,"passed",false,"failure",e.toString(),"rootCause",cause.toString()));}
            finally{try{channel.finishAndReleaseAll();}catch(Exception e){passed=false;rows.add(Map.of("client",version.getName(),"stage","generated packets","passed",false,"failure",e.toString()));}}
        }
        Files.writeString(reportPath,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",passed,"networkLoginValidated",false,"snapshotEdgeRegistered",false,"routes",rows)));return passed;
    }
    private static int movement(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,io.netty.channel.embedded.EmbeddedChannel channel,Path file)throws Exception {
        int cases=0;drainOutbound(channel);
        for(var value:JsonParser.parseString(Files.readString(file)).getAsJsonArray()){
            var row=value.getAsJsonObject();String name=row.get("name").getAsString();
            if(!apply(user,down,State.PLAY,ClientboundPackets26_3.valueOf(name).getId(),HexFormat.of().parseHex(row.get("hex").getAsString())))throw new IllegalStateException("Movement cancelled");
            if(name.equals("ENTITY_POSITION_SYNC")){
                io.netty.buffer.ByteBuf head=channel.readOutbound();if(head==null)throw new IllegalStateException("Missing head update");
                try{if(Types.VAR_INT.readPrimitive(head)!=oldest.getPacketTypesProvider().mappedClientboundType(State.PLAY,"ROTATE_HEAD").getId()||Types.VAR_INT.readPrimitive(head)!=123||head.readByte()!=-64||head.isReadable())throw new IllegalStateException("Head update corrupted");}finally{head.release();}
            }
            io.netty.buffer.ByteBuf unexpected=channel.readOutbound();if(unexpected!=null){unexpected.release();throw new IllegalStateException("Unexpected movement output");}cases++;
        }
        return cases;
    }
    private static int vehicleCandidate(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,Path file)throws Exception {
        int cases=0;
        for(var value:JsonParser.parseString(Files.readString(file)).getAsJsonArray()){
            var row=value.getAsJsonObject();var input=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.get("hex").getAsString()));var output=Unpooled.buffer();
            try{
                // Candidate already has 26.3 semantics; exercise every installed older-client stage.
                var packet=new PacketWrapperImpl(ClientboundPackets26_3.TELEPORT_ENTITY,input,user);
                packet.apply(Direction.CLIENTBOUND,State.PLAY,down.subList(1,down.size()));packet.writeToBuffer(output);
                if(Types.VAR_INT.readPrimitive(output)!=oldest.getPacketTypesProvider().mappedClientboundType(State.PLAY,"TELEPORT_ENTITY").getId()||Types.VAR_INT.readPrimitive(output)!=987)throw new IllegalStateException("Vehicle target changed");
                for(int i=0;i<3;i++)if(output.readDouble()!=0)throw new IllegalStateException("Vehicle position shifted");
                for(String axis:new String[]{"x","y","z"})if(output.readDouble()!=row.get(axis).getAsDouble())throw new IllegalStateException("Vehicle impulse changed");
                if(output.readFloat()!=0||output.readFloat()!=0||output.readInt()!=255||output.readBoolean()!=row.get("onGround").getAsBoolean()||output.isReadable())throw new IllegalStateException("Vehicle relative-motion flags changed");
                cases++;
            }finally{input.release();output.release();}
        }
        return cases;
    }
    private static int explosions(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,Path file)throws Exception {
        int cases=0;boolean legacy=user.getProtocolInfo().protocolVersion().olderThan(ProtocolVersion.v1_21_9);
        for(var value:JsonParser.parseString(Files.readString(file)).getAsJsonArray()){
            var row=value.getAsJsonObject();int mask=row.get("mask").getAsInt();
            var input=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.get("hex").getAsString()));var output=Unpooled.buffer();
            try{
                var packet=new PacketWrapperImpl(ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,ClientboundPackets26_3.EXPLODE.getId()),input,user);
                packet.apply(Direction.CLIENTBOUND,State.PLAY,down);packet.writeToBuffer(output);
                if(Types.VAR_INT.readPrimitive(output)!=oldest.getPacketTypesProvider().mappedClientboundType(State.PLAY,"EXPLODE").getId())throw new IllegalStateException("Wrong explosion packet");
                if(output.readDouble()!=12.25||output.readDouble()!=64||output.readDouble()!=-18.5)throw new IllegalStateException("Explosion center changed");
                if(!legacy&&(output.readFloat()!=4F||output.readInt()!=7))throw new IllegalStateException("Explosion radius/block count changed");
                if(output.readBoolean())throw new IllegalStateException("Explosion duplicated separate knockback");
                var particle=oldest.mappedTypes().particle().read(output);
                if((mask&1)==0&&(particle.getArguments().size()!=1||!Integer.valueOf(0).equals(particle.getArgument(0).getValue())))throw new IllegalStateException("Missing particle fallback is visible");
                var sound=Types.SOUND_EVENT.read(output);
                if(sound.hasId()||!sound.value().identifier().equals((mask&2)!=0?"minecraft:entity.generic.explode":"minecraft:intentionally_empty"))throw new IllegalStateException("Explosion sound changed");
                if(!legacy){int count=Types.VAR_INT.readPrimitive(output);if(count!=((mask&4)!=0?1:0))throw new IllegalStateException("Block particles changed");
                    for(int i=0;i<count;i++){oldest.mappedTypes().particle().read(output);if(output.readFloat()!=0.5F||output.readFloat()!=1.25F||Types.VAR_INT.readPrimitive(output)!=3)throw new IllegalStateException("Block particle parameters changed");}}
                if(output.isReadable())throw new IllegalStateException("Trailing explosion bytes");cases++;
            }finally{input.release();output.release();}
        }
        return cases;
    }
    private static int impulses(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,Path file)throws Exception {
        int cases=0;boolean legacy=user.getProtocolInfo().protocolVersion().olderThan(ProtocolVersion.v1_21_9);
        for(var value:JsonParser.parseString(Files.readString(file)).getAsJsonArray()){
            var row=value.getAsJsonObject();byte[] bytes=HexFormat.of().parseHex(row.get("hex").getAsString());
            var input=Unpooled.wrappedBuffer(bytes);var expected=Unpooled.wrappedBuffer(bytes);var output=Unpooled.buffer();
            try{
                Types.VAR_INT.readPrimitive(expected);var vector=Types.LOW_PRECISION_VECTOR.read(expected);
                var packet=new PacketWrapperImpl(2,input,user);packet.apply(Direction.CLIENTBOUND,State.PLAY,down);packet.writeToBuffer(output);
                if(Types.VAR_INT.readPrimitive(output)!=oldest.getPacketTypesProvider().mappedClientboundType(State.PLAY,"EXPLODE").getId())throw new IllegalStateException("Wrong impulse packet");
                for(int i=0;i<3;i++)if(output.readDouble()!=0)throw new IllegalStateException("Impulse center changed");
                if(!legacy&&(output.readFloat()!=0||output.readInt()!=0))throw new IllegalStateException("Impulse terrain effects");
                if(!output.readBoolean()||output.readDouble()!=vector.x()||output.readDouble()!=vector.y()||output.readDouble()!=vector.z())throw new IllegalStateException("Impulse lost its additive vector");
                oldest.mappedTypes().particle().read(output);var sound=Types.SOUND_EVENT.read(output);
                if(sound.hasId()||!sound.value().identifier().equals("minecraft:intentionally_empty"))throw new IllegalStateException("Impulse audible");
                if(!legacy&&Types.VAR_INT.readPrimitive(output)!=0)throw new IllegalStateException("Impulse block particles");
                if(output.isReadable())throw new IllegalStateException("Trailing impulse bytes");cases++;
            }finally{input.release();expected.release();output.release();}
        }
        return cases;
    }
    private static void roundtrip(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,CodecContext source,Item original)throws Exception {
        var input=Unpooled.buffer();var encoded=Unpooled.buffer();
        try {
            source.types.item.write(input,original);var w=new PacketWrapperImpl(ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,ClientboundPackets26_3.SET_CURSOR_ITEM.getId()),input,user);w.apply(Direction.CLIENTBOUND,State.PLAY,down);String name=w.getPacketType().getName();w.writeToBuffer(encoded);Types.VAR_INT.readPrimitive(encoded);
            if(name.equals("CONTAINER_SET_SLOT")){encoded.readByte();Types.VAR_INT.readPrimitive(encoded);encoded.readShort();}
            else if(!name.equals("SET_CURSOR_ITEM"))throw new IllegalStateException("Unexpected cursor replacement "+name);
            var client=oldest.mappedTypes().item().read(encoded);if(encoded.isReadable())throw new IllegalStateException("Trailing old-client item bytes");
            if(original.identifier()==new ItemMappingData().getFullItemMappings().id("ice_ball")) {
                var itemName=client.dataContainer().get(com.viaversion.viaversion.api.minecraft.data.StructuredDataKey.ITEM_NAME);
                if(!(itemName instanceof com.viaversion.nbt.tag.CompoundTag text)||!"Ice Ball".equals(text.getString("fallback")))throw new IllegalStateException("Older client lost readable item-name fallback");
            }
            clickRoundtrip(user,down,oldest,client,original);
            input.clear();encoded.clear();input.writeShort(7);
            oldest.mappedTypes().lengthPrefixedItem().write(input,client);
            for(var tracker:user.getEntityTrackers())tracker.setInstaBuild(true); // In-memory test only; no real player permissions change.
            var up=new ArrayList<Protocol>(down);Collections.reverse(up);
            int creative=oldest.getPacketTypesProvider().unmappedServerboundType(State.PLAY,"SET_CREATIVE_MODE_SLOT").getId();
            var returned=new PacketWrapperImpl(creative,input,user);returned.apply(Direction.SERVERBOUND,State.PLAY,up);returned.writeToBuffer(encoded);
            if(Types.VAR_INT.readPrimitive(encoded)!=ServerboundPackets26_3.SET_CREATIVE_MODE_SLOT.getId()||encoded.readShort()!=7)throw new IllegalStateException("Creative return packet mismatch");
            var restored=source.types.lengthPrefixedItem.read(encoded);
            if(restored.identifier()!=original.identifier()||restored.amount()!=original.amount()||encoded.isReadable())throw new IllegalStateException("Creative item lost through Via chain");
            var content=restored.dataContainer().data().values().stream().filter(d->d.key().identifier().equals("container")).findFirst().orElse(null);
            if(content==null)return;
            Item[] nested=(Item[])content.value();
            if(restored.identifier()!=original.identifier()||nested.length!=1||nested[0].identifier()!=new ItemMappingData().getFullItemMappings().id("ice_ball")||nested[0].amount()!=3||encoded.isReadable())throw new IllegalStateException("Nested item lost through Via chain");
        }finally{for(var tracker:user.getEntityTrackers())tracker.setInstaBuild(false);input.release();encoded.release();}
    }
    private static void clickRoundtrip(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,Item client,Item original)throws Exception {
        for(var tracker:user.getEntityTrackers())if(tracker.canInstaBuild())throw new IllegalStateException("Ordinary click test requires survival permissions");
        var hasher=new com.viaversion.viaversion.data.item.ItemHasherBase(oldest,user);
        var prediction=hasher.toHashedItem(client,true);
        var expected=user.get(BridgeConnectionState.class).hashes().hash(original,false);
        var up=new ArrayList<Protocol>(down);Collections.reverse(up);
        int id=oldest.getPacketTypesProvider().unmappedServerboundType(State.PLAY,"CONTAINER_CLICK").getId();
        // Both unchanged and split stack predictions, with changed slots and an empty slot.
        for(int amount:new int[]{original.amount(),1}) {
            var predicted=prediction.copy();predicted.setAmount(amount);var restoredHash=expected.copy();restoredHash.setAmount(amount);
            var input=Unpooled.buffer();var output=Unpooled.buffer();
            try {
                Types.VAR_INT.writePrimitive(input,0);Types.VAR_INT.writePrimitive(input,17);input.writeShort(7).writeByte(0);Types.VAR_INT.writePrimitive(input,0);Types.VAR_INT.writePrimitive(input,2);
                input.writeShort(7);Types.HASHED_ITEM.write(input,predicted);input.writeShort(8);Types.HASHED_ITEM.write(input,HashedStructuredItem.empty());Types.HASHED_ITEM.write(input,predicted);
                var packet=new PacketWrapperImpl(id,input,user);packet.apply(Direction.SERVERBOUND,State.PLAY,up);packet.writeToBuffer(output);
                if(Types.VAR_INT.readPrimitive(output)!=ServerboundPackets26_3.CONTAINER_CLICK.getId()||Types.VAR_INT.readPrimitive(output)!=0||Types.VAR_INT.readPrimitive(output)!=17||output.readShort()!=7||output.readByte()!=0||Types.VAR_INT.readPrimitive(output)!=0||Types.VAR_INT.readPrimitive(output)!=2||output.readShort()!=7||!Types.HASHED_ITEM.read(output).equals(restoredHash)||output.readShort()!=8||!Types.HASHED_ITEM.read(output).equals(HashedStructuredItem.empty())||!Types.HASHED_ITEM.read(output).equals(restoredHash)||output.isReadable())throw new IllegalStateException("Full-chain click prediction restoration failed");
            }finally{input.release();output.release();}
        }
        // The client's prediction was issued, but is no longer in the bridge cache.
        user.get(BridgeConnectionState.class).hashes().clear();
        var expired=Unpooled.buffer();
        try {
            Types.VAR_INT.writePrimitive(expired,0);Types.VAR_INT.writePrimitive(expired,18);expired.writeShort(7).writeByte(0);Types.VAR_INT.writePrimitive(expired,0);Types.VAR_INT.writePrimitive(expired,0);Types.HASHED_ITEM.write(expired,prediction);
            var packet=new PacketWrapperImpl(id,expired,user);boolean cancelled=false;
            try{packet.apply(Direction.SERVERBOUND,State.PLAY,up);}catch(CancelException expectedCancel){cancelled=true;}
            if(!cancelled)throw new IllegalStateException("Expired prediction was forwarded");
        }finally{expired.release();}
    }
    private static boolean apply(UserConnectionImpl user,List<Protocol> protocols,State state,int id,byte[] bytes)throws Exception {
        var input=Unpooled.wrappedBuffer(bytes);var output=Unpooled.buffer();
        try{var w=new PacketWrapperImpl(ProtocolSnapshot8To26_3.nativeClientboundId(state,id),input,user);w.apply(Direction.CLIENTBOUND,state,protocols);w.writeToBuffer(output);if(!output.isReadable())throw new IllegalStateException("Empty transformed packet");return true;}
        catch(CancelException expected){return false;}
        finally{input.release();output.release();}
    }
    private static void lightRoundtrip(UserConnectionImpl user,List<Protocol> protocols,Protocol<?,?,?,?> oldest,JsonObject fixture)throws Exception {
        var nativeChunk=Unpooled.wrappedBuffer(HexFormat.of().parseHex(fixture.get("hex").getAsString()));
        var input=Unpooled.buffer();var actual=Unpooled.buffer();var expected=Unpooled.buffer();
        try {
            var state=user.get(BridgeConnectionState.class);
            var codec=new SnapshotChunkType(state.sections(),ChunkTranslator.bits(new BridgeMappings().stateCount()),state.registries.biomeCount());
            var chunk=codec.read(nativeChunk);
            Types.VAR_INT.writePrimitive(input,chunk.getX());Types.VAR_INT.writePrimitive(input,chunk.getZ());input.writeBytes(nativeChunk,nativeChunk.readerIndex(),nativeChunk.readableBytes());
            Types.VAR_INT.writePrimitive(expected,chunk.getX());Types.VAR_INT.writePrimitive(expected,chunk.getZ());
            for(int i=0;i<4;i++)Types.LONG_ARRAY_PRIMITIVE.write(expected,Types.BIT_SET.read(nativeChunk).toLongArray());
            expected.writeBytes(nativeChunk);
            var packet=new PacketWrapperImpl(ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,ClientboundPackets26_3.LIGHT_UPDATE.getId()),input,user);packet.apply(Direction.CLIENTBOUND,State.PLAY,protocols);packet.writeToBuffer(actual);
            if(Types.VAR_INT.readPrimitive(actual)!=oldest.getPacketTypesProvider().mappedClientboundType(State.PLAY,"LIGHT_UPDATE").getId()||!io.netty.buffer.ByteBufUtil.equals(actual,expected))throw new IllegalStateException("Standalone light coordinates/masks/arrays changed");
        }finally{nativeChunk.release();input.release();actual.release();expected.release();}
    }
    private static void drainOutbound(io.netty.channel.embedded.EmbeddedChannel channel) {
        Object packet;while((packet=channel.readOutbound())!=null)io.netty.util.ReferenceCountUtil.release(packet);
    }
}
