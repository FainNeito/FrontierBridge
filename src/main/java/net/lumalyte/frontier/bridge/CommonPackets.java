package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.type.Types;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.*;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

/** Common configuration/play envelopes. Plugin payload contents belong to their channel. */
final class CommonPackets {
    static void register(ProtocolSnapshot8To26_3 p) {
        for(State state:new State[]{CONFIGURATION,PLAY}) {
            fields(p,state,CLIENTBOUND,"COOKIE_REQUEST",Types.STRING);
            register(p,state,SERVERBOUND,"COOKIE_RESPONSE",w->{w.passthrough(Types.STRING);byte[] data=w.passthrough(Types.OPTIONAL_BYTE_ARRAY_PRIMITIVE);if(data!=null&&data.length>5120)throw new IllegalArgumentException("Cookie too large");});
            register(p,state,CLIENTBOUND,"STORE_COOKIE",w->{w.passthrough(Types.STRING);if(w.passthrough(Types.BYTE_ARRAY_PRIMITIVE).length>5120)throw new IllegalArgumentException("Cookie too large");});
            fields(p,state,CLIENTBOUND,"DISCONNECT",Types.TRUSTED_TAG);
            fields(p,state,CLIENTBOUND,"RESOURCE_PACK_POP",Types.OPTIONAL_UUID);
            register(p,state,CLIENTBOUND,"RESOURCE_PACK_PUSH",w->{
                w.passthrough(Types.UUID);w.passthrough(Types.STRING);w.passthrough(Types.STRING);w.passthrough(Types.BOOLEAN);
                if(w.passthrough(Types.BOOLEAN))w.passthrough(Types.TRUSTED_TAG);
            });
            fields(p,state,SERVERBOUND,"RESOURCE_PACK",Types.UUID,Types.VAR_INT);
            fields(p,state,SERVERBOUND,"CUSTOM_CLICK_ACTION",Types.STRING,Types.CUSTOM_CLICK_ACTION_TAG);
            fields(p,state,CLIENTBOUND,"CLEAR_DIALOG");
            fields(p,state,CLIENTBOUND,"POST_EFFECTS",Types.STRING_ARRAY);
            register(p,state,CLIENTBOUND,"TRANSFER",w->{
                w.passthrough(Types.STRING);w.passthrough(Types.VAR_INT);
                // Snapshot-only transfer properties have no representation in 26.3.
                int count=w.read(Types.VAR_INT);if(count<0||count>1024)throw new IllegalArgumentException("Invalid transfer properties");
                for(int i=0;i<count;i++){w.read(Types.STRING);w.read(Types.STRING);}
            });
            register(p,state,CLIENTBOUND,"CUSTOM_REPORT_DETAILS",w->{
                int count=w.passthrough(Types.VAR_INT);if(count<0||count>32)throw new IllegalArgumentException("Invalid report detail count");
                for(int i=0;i<count;i++){w.passthrough(Types.STRING);w.passthrough(Types.STRING);}
            });
            register(p,state,CLIENTBOUND,"SERVER_LINKS",w->{
                int count=w.passthrough(Types.VAR_INT);if(count<0||count>1024)throw new IllegalArgumentException("Invalid server link count");
                for(int i=0;i<count;i++){if(w.passthrough(Types.BOOLEAN))w.passthrough(Types.VAR_INT);else w.passthrough(Types.TRUSTED_TAG);w.passthrough(Types.STRING);}
            });
            for(Direction direction:Direction.values())register(p,state,direction,"CUSTOM_PAYLOAD",w->{
                w.passthrough(Types.STRING);byte[] payload=w.read(Types.REMAINING_BYTES);
                if(payload.length>(direction==CLIENTBOUND?1048576:32767))throw new IllegalArgumentException("Custom payload too large");
                w.write(Types.REMAINING_BYTES,payload);
            });
        }
        fields(p,CONFIGURATION,CLIENTBOUND,"RESET_CHAT");
        fields(p,CONFIGURATION,CLIENTBOUND,"CODE_OF_CONDUCT",Types.STRING);
        fields(p,CONFIGURATION,CLIENTBOUND,"SHOW_DIALOG",Types.TRUSTED_COMPOUND_TAG);
    }
    private static void register(ProtocolSnapshot8To26_3 p,State state,Direction direction,String name,PacketHandler handler) {
        int id=SessionPackets.type(state,direction,name).getId();
        if(direction==CLIENTBOUND)p.registerClientbound(state,id,id,handler,false);else p.registerServerbound(state,id,id,handler,false);
    }
}
