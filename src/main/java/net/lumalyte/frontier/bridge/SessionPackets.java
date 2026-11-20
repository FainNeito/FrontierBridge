package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import com.viaversion.viaversion.protocols.v1_21_7to1_21_9.packet.ServerboundConfigurationPackets1_21_9;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.*;

/** Explicit registry-free schemas audited with both native runtime codecs. */
final class SessionPackets {
    static PacketType type(State state,Direction direction,String name) {
        if(state==CONFIGURATION)return direction==CLIENTBOUND?ClientboundConfigurationPackets26_3.valueOf(name):ServerboundConfigurationPackets1_21_9.valueOf(name);
        return direction==CLIENTBOUND?ClientboundPackets26_3.valueOf(name):ServerboundPackets26_3.valueOf(name);
    }
    static void register(ProtocolSnapshot8To26_3 p) {
        for(State state:new State[]{CONFIGURATION,PLAY}) {
            fields(p,state,CLIENTBOUND,"KEEP_ALIVE",Types.LONG);
            fields(p,state,SERVERBOUND,"KEEP_ALIVE",Types.LONG);
            fields(p,state,CLIENTBOUND,"PING",Types.INT);
            fields(p,state,SERVERBOUND,"PONG",Types.INT);
            fields(p,state,SERVERBOUND,"CLIENT_INFORMATION",Types.STRING,Types.BYTE,Types.VAR_INT,Types.BOOLEAN,Types.UNSIGNED_BYTE,Types.VAR_INT,Types.BOOLEAN,Types.BOOLEAN,Types.VAR_INT);
        }
        int finish=ClientboundConfigurationPackets26_3.FINISH_CONFIGURATION.getId();
        p.registerClientbound(CONFIGURATION,finish,finish,w->w.user().getProtocolInfo().setServerState(PLAY),false);
        finish=ServerboundConfigurationPackets1_21_9.FINISH_CONFIGURATION.getId();
        p.registerServerbound(CONFIGURATION,finish,finish,w->w.user().getProtocolInfo().setClientState(PLAY),false);
        fields(p,CONFIGURATION,SERVERBOUND,"ACCEPT_CODE_OF_CONDUCT");
        fields(p,CONFIGURATION,CLIENTBOUND,"UPDATE_ENABLED_FEATURES",Types.STRING_ARRAY);
        int packs=ClientboundConfigurationPackets26_3.SELECT_KNOWN_PACKS.getId();
        p.registerClientbound(CONFIGURATION,packs,packs,w->{
            int count=w.passthrough(Types.VAR_INT);
            if(count<0||count>1024)throw new IllegalArgumentException("Invalid known-pack count");
            for(int i=0;i<count;i++){w.passthrough(Types.STRING);w.passthrough(Types.STRING);w.passthrough(Types.STRING);}
        },false);
        int acknowledged=ServerboundPackets26_3.CONFIGURATION_ACKNOWLEDGED.getId();
        p.registerServerbound(PLAY,acknowledged,acknowledged,w->w.user().getProtocolInfo().setClientState(CONFIGURATION),false);
        fields(p,PLAY,SERVERBOUND,"CLIENT_TICK_END");
        fields(p,PLAY,CLIENTBOUND,"CHUNK_BATCH_START");
        fields(p,PLAY,CLIENTBOUND,"CHUNK_BATCH_FINISHED",Types.VAR_INT);
        fields(p,PLAY,SERVERBOUND,"CHUNK_BATCH_RECEIVED",Types.FLOAT);
        fields(p,PLAY,CLIENTBOUND,"SET_CHUNK_CACHE_CENTER",Types.VAR_INT,Types.VAR_INT);
        fields(p,PLAY,CLIENTBOUND,"SET_CHUNK_CACHE_RADIUS",Types.VAR_INT);
        fields(p,PLAY,CLIENTBOUND,"SET_SIMULATION_DISTANCE",Types.VAR_INT);
        fields(p,PLAY,CLIENTBOUND,"PLAYER_ABILITIES",Types.BYTE,Types.FLOAT,Types.FLOAT);
        fields(p,PLAY,SERVERBOUND,"PLAYER_ABILITIES",Types.BYTE);
        fields(p,PLAY,SERVERBOUND,"MOVE_PLAYER_POS",Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.UNSIGNED_BYTE);
        fields(p,PLAY,SERVERBOUND,"MOVE_PLAYER_POS_ROT",Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.FLOAT,Types.FLOAT,Types.UNSIGNED_BYTE);
        fields(p,PLAY,SERVERBOUND,"MOVE_PLAYER_ROT",Types.FLOAT,Types.FLOAT,Types.UNSIGNED_BYTE);
        fields(p,PLAY,SERVERBOUND,"MOVE_PLAYER_STATUS_ONLY",Types.UNSIGNED_BYTE);
        fields(p,PLAY,SERVERBOUND,"ACCEPT_TELEPORTATION",Types.VAR_INT,Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.FLOAT,Types.FLOAT);
        fields(p,PLAY,CLIENTBOUND,"PLAYER_POSITION",Types.VAR_INT,Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.FLOAT,Types.FLOAT,Types.INT);
    }
    static void fields(ProtocolSnapshot8To26_3 p,State state,Direction direction,String name,Type<?>... fields) {
        int id=type(state,direction,name).getId();
        com.viaversion.viaversion.api.protocol.remapper.PacketHandler handler=w->{for(var field:fields)w.passthrough(field);};
        if(direction==CLIENTBOUND)p.registerClientbound(state,id,id,handler,false);else p.registerServerbound(state,id,id,handler,false);
    }
}
