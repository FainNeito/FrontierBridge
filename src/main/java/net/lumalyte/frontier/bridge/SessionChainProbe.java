package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.exception.CancelException;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;

/** Payload-chain audit; configuration state transitions remain a separate login milestone. */
final class SessionChainProbe {
    static Map<String,Object> run(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,Path file)throws Exception {
        NativeLiveSessionProbe.run(file);
        int count=0,effectsOmitted=0,conductDialogs=0,statusEffectsOmitted=0,legacyActionsOmitted=0;var up=new ArrayList<Protocol>(down);Collections.reverse(up);var failures=new ArrayList<String>();
        for(var value:JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
            var row=value.getAsJsonObject();String name=row.get("name").getAsString();
            boolean before1219=user.getProtocolInfo().protocolVersion().olderThan(com.viaversion.viaversion.api.protocol.version.ProtocolVersion.v1_21_9);
            if(before1219&&name.equals("DEBUG_SUBSCRIPTION_REQUEST")&&row.has("debugSubscription")&&row.get("debugSubscription").getAsInt()!=0)continue; // These clients expose only tick-time subscriptions.
            if(row.has("nativeOnly")&&row.get("nativeOnly").getAsBoolean())continue;
            // Unit state transitions and version-specific code-of-conduct acceptance are native-codec tested separately.
            if(Set.of("FINISH_CONFIGURATION","CONFIGURATION_ACKNOWLEDGED","ACCEPT_CODE_OF_CONDUCT","ACCEPT_TELEPORTATION").contains(name))continue;
            State state=State.valueOf(row.get("state").getAsString());Direction direction=Direction.valueOf(row.get("direction").getAsString());
            var provider=oldest.getPacketTypesProvider();var nativeType=SessionPackets.type(state,direction,name);
            var clientType=direction==Direction.CLIENTBOUND?provider.mappedClientboundType(state,row.has("legacyPacket")?row.get("legacyPacket").getAsString():name):provider.unmappedServerboundType(state,row.has("clientPacket")?row.get("clientPacket").getAsString():name);
            if(clientType==null&&direction==Direction.CLIENTBOUND)clientType=provider.mappedClientboundType(state,name);
            byte[] payload=HexFormat.of().parseHex(row.get("hex").getAsString());
            byte[] expectedPayload=row.has("targetHex")?HexFormat.of().parseHex(row.get("targetHex").getAsString()):payload;
            if(row.has("legacyHex")&&(!row.has("legacyBefore1219")||before1219))expectedPayload=HexFormat.of().parseHex(row.get("legacyHex").getAsString());
            boolean targetCancelled=row.has("targetCancelled")&&row.get("targetCancelled").getAsBoolean();
            boolean legacyCancelled=row.has("legacyCancelled")&&row.get("legacyCancelled").getAsBoolean();targetCancelled|=legacyCancelled;
            if(before1219&&(name.startsWith("DEBUG_")&&!name.equals("DEBUG_SAMPLE")&&!name.equals("DEBUG_SUBSCRIPTION_REQUEST")||row.has("cancelBefore1219"))){legacyCancelled=true;targetCancelled=true;}
            if(before1219&&name.equals("DEBUG_SUBSCRIPTION_REQUEST"))payload=new byte[]{0};
            if(name.equals("AWARD_STATS"))expectedPayload=statistics(expectedPayload,down);
            if(name.equals("BLOCK_ENTITY_DATA")) {
                var input=Unpooled.wrappedBuffer(expectedPayload);var output=Unpooled.buffer();try{
                    output.writeLong(input.readLong());int id=Types.VAR_INT.readPrimitive(input);
                    for(var protocol:down){if(protocol instanceof ProtocolSnapshot8To26_3||id<0)continue;var data=protocol.getMappingData();if(data!=null&&data.getBlockEntityMappings()!=null)id=data.getBlockEntityMappings().getNewId(id);}
                    if(id<0){legacyCancelled=true;targetCancelled=true;}
                    Types.VAR_INT.writePrimitive(output,id);output.writeBytes(input);expectedPayload=ByteBufUtil.getBytes(output);
                }finally{input.release();output.release();}
            }
            if(name.equals("MERCHANT_OFFERS")) {
                var input=Unpooled.wrappedBuffer(expectedPayload);var output=Unpooled.buffer();try{
                    Types.VAR_INT.writePrimitive(output,Types.VAR_INT.readPrimitive(input));Types.VAR_INT.writePrimitive(output,Types.VAR_INT.readPrimitive(input));int id=Types.VAR_INT.readPrimitive(input);
                    for(var protocol:down){if(protocol instanceof ProtocolSnapshot8To26_3)continue;var data=protocol.getMappingData();if(data!=null&&data.getItemMappings()!=null)id=data.getNewItemId(id);}
                    Types.VAR_INT.writePrimitive(output,id);output.writeBytes(input);expectedPayload=ByteBufUtil.getBytes(output);
                }finally{input.release();output.release();}
            }
            if(name.equals("EXPLODE")) {
                // 26.2 has no sound-presence flag; before 1.21.9 radius/block-particle fields did not exist either.
                expectedPayload=Arrays.copyOf(expectedPayload,expectedPayload.length-1);
                if(before1219)expectedPayload=HexFormat.of().parseHex(row.get("explosionBefore1219Hex").getAsString());
                var input=Unpooled.wrappedBuffer(expectedPayload);var output=Unpooled.buffer();try {
                    output.writeBytes(input,24);if(!before1219)output.writeBytes(input,8);boolean knockback=input.readBoolean();output.writeBoolean(knockback);if(knockback)output.writeBytes(input,24);
                    int particle=Types.VAR_INT.readPrimitive(input);for(var protocol:down){if(protocol instanceof ProtocolSnapshot8To26_3)continue;var data=protocol.getMappingData();if(data!=null&&data.getParticleMappings()!=null)particle=data.getParticleMappings().getNewId(particle);}
                    Types.VAR_INT.writePrimitive(output,particle);output.writeBytes(input);expectedPayload=ByteBufUtil.getBytes(output);
                }finally{input.release();output.release();}
            }
            if((name.equals("UPDATE_MOB_EFFECT")||name.equals("REMOVE_MOB_EFFECT"))&&user.getProtocolInfo().protocolVersion().olderThan(com.viaversion.viaversion.api.protocol.version.ProtocolVersion.v1_21_11)) {
                var effect=Unpooled.wrappedBuffer(payload);try{Types.VAR_INT.readPrimitive(effect);if(Types.VAR_INT.readPrimitive(effect)==39)targetCancelled=true;}finally{effect.release();}
            }
            if(name.equals("UPDATE_ATTRIBUTES"))expectedPayload=attributes(expectedPayload,down);
            if((name.equals("SET_BORDER_LERP_SIZE")||name.equals("INITIALIZE_BORDER"))&&user.getProtocolInfo().protocolVersion().olderThan(com.viaversion.viaversion.api.protocol.version.ProtocolVersion.v1_21_11)) {
                // Before 1.21.11 border durations were milliseconds rather than ticks.
                var ticks=Unpooled.wrappedBuffer(expectedPayload);var millis=Unpooled.buffer();
                try{for(int i=0;i<(name.equals("INITIALIZE_BORDER")?4:2);i++)millis.writeDouble(ticks.readDouble());Types.VAR_LONG.writePrimitive(millis,Math.multiplyExact(Types.VAR_LONG.readPrimitive(ticks),50));millis.writeBytes(ticks);expectedPayload=ByteBufUtil.getBytes(millis);}
                finally{ticks.release();millis.release();}
            }
            if(name.equals("ANIMATE")) {
                expectedPayload=expectedPayload.clone();int action=Byte.toUnsignedInt(expectedPayload[expectedPayload.length-1]);
                expectedPayload[expectedPayload.length-1]=(byte)new int[]{2,4,5}[action];
            }
            if(row.has("clientHex"))payload=HexFormat.of().parseHex(row.get("clientHex").getAsString());
            if(name.equals("OPEN_SCREEN")) {
                var input=Unpooled.wrappedBuffer(expectedPayload);var mapped=Unpooled.buffer();
                try {
                    Types.VAR_INT.writePrimitive(mapped,Types.VAR_INT.readPrimitive(input));int menu=Types.VAR_INT.readPrimitive(input);
                    for(var protocol:down){var data=protocol.getMappingData();if(data!=null&&data.getMenuMappings()!=null)menu=data.getMenuMappings().getNewId(menu);if(menu<0)throw new IllegalStateException("Unsupported fixture menu");}
                    Types.VAR_INT.writePrimitive(mapped,menu);mapped.writeBytes(input);expectedPayload=ByteBufUtil.getBytes(mapped);
                }finally{input.release();mapped.release();}
            }
            try {
                if(row.has("spectator"))for(var protocol:up)if(protocol.getClass().getSimpleName().equals("Protocol26_1To1_21_11")){Object storage=user.storables(protocol);storage.getClass().getMethod("setGameMode",int.class).invoke(storage,3);}
                var actual=transform(user,direction==Direction.CLIENTBOUND?down:up,direction,state,direction==Direction.CLIENTBOUND?nativeType.getId():clientType.getId(),payload);
                try{
                    if(targetCancelled)throw new IllegalStateException("Unsupported status effect was forwarded");
                    if(name.equals("COMMANDS")) {
                        if(Types.VAR_INT.readPrimitive(actual)!=clientType.getId())throw new IllegalStateException("Wrong command packet ID");
                        verifyCommandTree(actual,user,down);
                    } else if(name.equals("CODE_OF_CONDUCT")&&user.getProtocolInfo().protocolVersion().olderThan(com.viaversion.viaversion.api.protocol.version.ProtocolVersion.v1_21_9)) {
                        if(Types.VAR_INT.readPrimitive(actual)!=provider.mappedClientboundType(state,"SHOW_DIALOG").getId())throw new IllegalStateException("Missing conduct dialog");
                        var dialog=Types.TRUSTED_COMPOUND_TAG.read(actual);var original=Unpooled.wrappedBuffer(payload);
                        try{if(!dialog.getCompoundTag("body").getStringTag("contents").getValue().equals(Types.STRING.read(original))||actual.isReadable())throw new IllegalStateException("Conduct text changed");}finally{original.release();}
                        conductDialogs++;
                    } else {
                        int expectedId=direction==Direction.CLIENTBOUND?clientType.getId():nativeType.getId();if(Types.VAR_INT.readPrimitive(actual)!=expectedId||!Arrays.equals(expectedPayload,ByteBufUtil.getBytes(actual)))throw new IllegalStateException("Session chain payload mismatch expected="+HexFormat.of().formatHex(expectedPayload)+" actual="+ByteBufUtil.hexDump(actual));
                    }
                }
                finally{actual.release();}
            }catch(CancelException e){if(legacyCancelled)legacyActionsOmitted++;else if(targetCancelled)statusEffectsOmitted++;else if(name.equals("POST_EFFECTS"))effectsOmitted++;else if(failures.size()<100)failures.add(name+": unexpected cancellation");}
            catch(Exception e){String key=state+" "+direction+" "+name+":";if(failures.stream().filter(f->f.startsWith(key)).count()<2)failures.add(key+" "+e);}
            finally{if(row.has("spectator"))for(var protocol:up)if(protocol.getClass().getSimpleName().equals("Protocol26_1To1_21_11")){Object storage=user.storables(protocol);storage.getClass().getMethod("setGameMode",int.class).invoke(storage,0);}}
            count++;
        }
        if(!failures.isEmpty())throw new IllegalStateException("Session failures: "+new Gson().toJson(failures));
        for(var tracker:user.getEntityTrackers())tracker.setInstaBuild(false);
        // Older clients send teleport ID first, then position/rotation. Via combines the pair for 26.3+.
        var acknowledgement=Unpooled.buffer();var followup=Unpooled.buffer();var expected=Unpooled.buffer();
        try {
            Types.VAR_INT.writePrimitive(acknowledgement,13);byte[] ack=new byte[acknowledgement.readableBytes()];acknowledgement.readBytes(ack);
            int ackId=oldest.getPacketTypesProvider().unmappedServerboundType(State.PLAY,"ACCEPT_TELEPORTATION").getId();
            boolean cancelled=false;try{var unexpected=transform(user,up,Direction.SERVERBOUND,State.PLAY,ackId,ack);unexpected.release();}catch(CancelException normal){cancelled=true;}
            if(!cancelled)throw new IllegalStateException("Teleport acknowledgement was not deferred");
            followup.writeDouble(-12.5).writeDouble(70).writeDouble(45.25).writeFloat(120).writeFloat(-30).writeByte(1);
            Types.VAR_INT.writePrimitive(expected,13);expected.writeBytes(followup,0,followup.readableBytes()-1);
            byte[] move=new byte[followup.readableBytes()];followup.readBytes(move);
            int moveId=oldest.getPacketTypesProvider().unmappedServerboundType(State.PLAY,"MOVE_PLAYER_POS_ROT").getId();
            var actual=transform(user,up,Direction.SERVERBOUND,State.PLAY,moveId,move);
            try{if(Types.VAR_INT.readPrimitive(actual)!=SessionPackets.type(State.PLAY,Direction.SERVERBOUND,"ACCEPT_TELEPORTATION").getId()||!ByteBufUtil.equals(actual,expected))throw new IllegalStateException("Teleport acknowledgement position/rotation mismatch");}
            finally{actual.release();}
            return Map.of("payloads",count,"teleportAcknowledgementPair",true,"nativeStateTransitionPayloadsOnly",true,"unsupportedPostEffectsOmitted",effectsOmitted,"codeOfConductDialogs",conductDialogs,"unsupportedStatusEffectsOmitted",statusEffectsOmitted,"legacyCombatActionsOmitted",legacyActionsOmitted);
        }finally{acknowledgement.release();followup.release();expected.release();}
    }
    private static byte[] statistics(byte[] payload,List<Protocol> down)throws Exception {
        var in=Unpooled.wrappedBuffer(payload);var out=Unpooled.buffer();try {
            if(Types.VAR_INT.readPrimitive(in)!=1)throw new IllegalStateException("Expected single statistic fixture");int category=Types.VAR_INT.readPrimitive(in),id=Types.VAR_INT.readPrimitive(in),amount=Types.VAR_INT.readPrimitive(in);
            for(var protocol:down){if(protocol instanceof ProtocolSnapshot8To26_3)continue;var data=protocol.getMappingData();if(data==null||id<0)continue;switch(category){
                case 0->{if(data.getBlockMappings()!=null)id=data.getNewBlockId(id);}
                case 1,2,3,4,5->{if(data.getItemMappings()!=null)id=data.getNewItemId(id);}
                case 6,7->{if(protocol.getEntityRewriter()!=null)id=protocol.getEntityRewriter().newEntityId(id);}
                case 8->{if(data.getStatisticsMappings()!=null)id=data.getStatisticsMappings().getNewId(id);}
            }}
            Types.VAR_INT.writePrimitive(out,id<0?0:1);if(id>=0){Types.VAR_INT.writePrimitive(out,category);Types.VAR_INT.writePrimitive(out,id);Types.VAR_INT.writePrimitive(out,amount);}return ByteBufUtil.getBytes(out);
        }finally{in.release();out.release();}
    }
    private static byte[] attributes(byte[] payload,List<Protocol> down)throws Exception {
        var input=Unpooled.wrappedBuffer(payload);var output=Unpooled.buffer();
        try {
            int entity=Types.VAR_INT.readPrimitive(input),count=Types.VAR_INT.readPrimitive(input);var entries=new ArrayList<byte[]>();
            for(int i=0;i<count;i++) {
                int attribute=Types.VAR_INT.readPrimitive(input),start=input.readerIndex();input.readDouble();int modifiers=Types.VAR_INT.readPrimitive(input);
                for(int j=0;j<modifiers;j++){Types.STRING.read(input);input.readDouble();input.readByte();}
                byte[] tail=ByteBufUtil.getBytes(input,start,input.readerIndex()-start);
                for(var protocol:down){var data=protocol.getMappingData();if(attribute>=0&&data!=null&&data.getAttributeMappings()!=null)attribute=data.getAttributeMappings().getNewId(attribute);}
                if(attribute>=0){var row=Unpooled.buffer();try{Types.VAR_INT.writePrimitive(row,attribute);row.writeBytes(tail);entries.add(ByteBufUtil.getBytes(row));}finally{row.release();}}
            }
            if(input.isReadable())throw new IllegalStateException("Trailing attribute fixture bytes");Types.VAR_INT.writePrimitive(output,entity);Types.VAR_INT.writePrimitive(output,entries.size());for(byte[] row:entries)output.writeBytes(row);return ByteBufUtil.getBytes(output);
        }finally{input.release();output.release();}
    }
    private static ByteBuf transform(UserConnectionImpl user,List<Protocol> protocols,Direction direction,State state,int id,byte[] payload)throws Exception {
        var input=Unpooled.wrappedBuffer(payload);var output=Unpooled.buffer();
        try{var wrapper=new PacketWrapperImpl(id,input,user);wrapper.apply(direction,state,protocols);wrapper.writeToBuffer(output);return output;}
        catch(Exception|Error e){output.release();throw e;}finally{input.release();}
    }
    private static void verifyCommandTree(ByteBuf input,UserConnectionImpl user,List<Protocol> down)throws Exception {
        com.viaversion.viaversion.api.data.FullMappings arguments=null;
        for(int i=down.size()-1;i>=0;i--){var mappings=down.get(i).getMappingData();if(mappings!=null&&mappings.getArgumentTypeMappings()!=null){arguments=mappings.getArgumentTypeMappings();break;}}
        if(arguments==null)throw new IllegalStateException("Missing client command parser table");
        int count=Types.VAR_INT.readPrimitive(input);if(count!=2&&count!=3)throw new IllegalStateException("Command nodes lost");
        for(int i=0;i<count;i++) {
            int flags=input.readUnsignedByte(),kind=flags&3;int[] children=Types.VAR_INT_ARRAY_PRIMITIVE.read(input);
            for(int child:children)if(child<0||child>=count)throw new IllegalStateException("Invalid command child");
            if((flags&8)!=0&&Types.VAR_INT.readPrimitive(input)!=0)throw new IllegalStateException("Redirect root changed");
            if(kind!=0){String name=Types.STRING.read(input);if(!Set.of("probe","value","redirect").contains(name))throw new IllegalStateException("Command name changed");}
            if(kind==2) {
                String parser=arguments.mappedIdentifier(Types.VAR_INT.readPrimitive(input));if(parser==null)throw new IllegalStateException("Unknown client argument type");
                WorldEntryPackets.properties(new PacketWrapperImpl(-1,input,user),parser);
            }
            if((flags&16)!=0&&!Types.STRING.read(input).equals("minecraft:ask_server"))throw new IllegalStateException("Suggestion provider changed");
        }
        if(Types.VAR_INT.readPrimitive(input)!=0||input.isReadable())throw new IllegalStateException("Command root/trailing bytes changed");
    }
}
