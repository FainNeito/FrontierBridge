package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.*;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

/** World-entry command and player-list payloads, pinned to the audited snapshot. */
final class WorldEntryPackets {
    static void register(ProtocolSnapshot8To26_3 p) {
        int configurationCommands=-1;
        for(var row:ItemMappingData.resource("snapshot-packets.json").getAsJsonObject("configuration").getAsJsonArray("CLIENTBOUND")) {
            var packet=row.getAsJsonObject();
            if(packet.get("key").getAsString().equals("minecraft:commands"))configurationCommands=packet.get("id").getAsInt();
        }
        if(configurationCommands<0)throw new IllegalStateException("Missing native configuration commands ID");
        p.registerClientbound(State.CONFIGURATION,configurationCommands,-1,w->{
            var input=io.netty.buffer.Unpooled.wrappedBuffer(w.read(Types.REMAINING_BYTES));
            var output=io.netty.buffer.Unpooled.buffer();
            try {
                CommandTreeTranslator.rewrite(input,output);
                byte[] bytes=new byte[output.readableBytes()];output.readBytes(bytes);
                w.user().get(BridgeConnectionState.class).commands(bytes);w.cancel();
            }finally{input.release();output.release();}
        },false);
        register(p,ClientboundPackets26_3.COMMANDS,w->{
            var input=io.netty.buffer.Unpooled.wrappedBuffer(w.read(Types.REMAINING_BYTES));
            var output=io.netty.buffer.Unpooled.buffer();
            try {
                CommandTreeTranslator.rewrite(input,output);
                CommandTreeTranslator.writeTyped(output,w);
            }finally{input.release();output.release();}
        });
        register(p,ClientboundPackets26_3.PLAYER_INFO_UPDATE,w->{
            var actions=w.passthrough(Types.PROFILE_ACTIONS_ENUM1_21_4);int count=w.passthrough(Types.VAR_INT);
            if(count<0||count>8192)throw new IllegalArgumentException("Invalid player-info count");
            for(int i=0;i<count;i++) {
                w.passthrough(Types.UUID);
                if(actions.get(0)){w.passthrough(Types.STRING);w.passthrough(Types.PROFILE_PROPERTY_ARRAY);}
                if(actions.get(1)&&w.passthrough(Types.BOOLEAN)){w.passthrough(Types.UUID);w.passthrough(Types.PROFILE_KEY);}
                if(actions.get(2))w.passthrough(Types.VAR_INT);
                if(actions.get(3))w.passthrough(Types.BOOLEAN);
                if(actions.get(4))w.passthrough(Types.VAR_INT);
                if(actions.get(5))w.passthrough(Types.TRUSTED_OPTIONAL_TAG);
                if(actions.get(6))w.passthrough(Types.VAR_INT);
                if(actions.get(7))w.passthrough(Types.BOOLEAN);
            }
        });
        fields(p,PLAY,CLIENTBOUND,"PLAYER_INFO_REMOVE",Types.UUID_ARRAY);
        fields(p,PLAY,SERVERBOUND,"COMMAND_SUGGESTION",Types.VAR_INT,Types.STRING);
        fields(p,PLAY,SERVERBOUND,"CHAT_COMMAND",Types.STRING);
        register(p,ClientboundPackets26_3.COMMAND_SUGGESTIONS,w->{
            w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);
            int count=w.passthrough(Types.VAR_INT);if(count<0||count>65536)throw new IllegalArgumentException("Invalid suggestion count");
            for(int i=0;i<count;i++){w.passthrough(Types.STRING);w.passthrough(Types.TRUSTED_OPTIONAL_TAG);}
        });
    }
    static void sendPendingCommands(PacketWrapper login) throws com.viaversion.viaversion.exception.InformativeException {
        byte[] bytes=login.user().get(BridgeConnectionState.class).takeCommands();
        if(bytes==null)return;
        var packet=login.create(ClientboundPackets26_3.COMMANDS);
        var input=io.netty.buffer.Unpooled.wrappedBuffer(bytes);
        try{CommandTreeTranslator.writeTyped(input,packet);}finally{input.release();}
        packet.scheduleSend(ProtocolSnapshot8To26_3.class);
    }
    static void properties(PacketWrapper w,String name) throws com.viaversion.viaversion.exception.InformativeException {
        switch(name) {
            case "brigadier:float" -> numeric(w,Types.FLOAT);
            case "brigadier:double" -> numeric(w,Types.DOUBLE);
            case "brigadier:integer" -> numeric(w,Types.INT);
            case "brigadier:long" -> numeric(w,Types.LONG);
            case "brigadier:string" -> w.passthrough(Types.VAR_INT);
            case "minecraft:entity","minecraft:score_holder" -> w.passthrough(Types.BYTE);
            case "minecraft:time" -> w.passthrough(Types.INT);
            case "minecraft:resource","minecraft:resource_key","minecraft:resource_or_tag","minecraft:resource_or_tag_key","minecraft:resource_selector" -> w.passthrough(Types.STRING);
            default -> { }
        }
    }
    private static void numeric(PacketWrapper w,Type<?> type)throws com.viaversion.viaversion.exception.InformativeException {byte flags=w.passthrough(Types.BYTE);if((flags&1)!=0)w.passthrough(type);if((flags&2)!=0)w.passthrough(type);}
    private static void index(int index,int count){if(index<0||index>=count)throw new IllegalArgumentException("Invalid command index");}
    private static void register(ProtocolSnapshot8To26_3 p,ClientboundPackets26_3 type,com.viaversion.viaversion.api.protocol.remapper.PacketHandler h){p.registerClientbound(PLAY,type.getId(),type.getId(),h,false);}
}
