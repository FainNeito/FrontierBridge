package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

/** Registry-free play envelopes; component-bearing cases currently cover literal text. */
final class BasicPlayPackets {
    static void register(ProtocolSnapshot8To26_3 p) {
        fields(p,PLAY,CLIENTBOUND,"SET_HEALTH",Types.FLOAT,Types.VAR_INT,Types.FLOAT);
        fields(p,PLAY,CLIENTBOUND,"SET_EXPERIENCE",Types.FLOAT,Types.VAR_INT,Types.VAR_INT);
        fields(p,PLAY,CLIENTBOUND,"SET_HELD_SLOT",Types.VAR_INT);
        fields(p,PLAY,SERVERBOUND,"SET_CARRIED_ITEM",Types.SHORT);
        fields(p,PLAY,CLIENTBOUND,"CONTAINER_CLOSE",Types.VAR_INT);
        fields(p,PLAY,SERVERBOUND,"CONTAINER_CLOSE",Types.VAR_INT);
        fields(p,PLAY,CLIENTBOUND,"CONTAINER_SET_DATA",Types.VAR_INT,Types.SHORT,Types.SHORT);
        int systemChat=SessionPackets.type(PLAY,CLIENTBOUND,"SYSTEM_CHAT").getId();
        p.registerClientbound(PLAY,systemChat,systemChat,w->{var message=w.read(Types.TRUSTED_TAG);ScreenPackets.sanitizeDeathComponent(message);w.write(Types.TRUSTED_TAG,message);w.passthrough(Types.BOOLEAN);},false);
        for(String name:new String[]{"SET_ACTION_BAR_TEXT","SET_TITLE_TEXT","SET_SUBTITLE_TEXT"})fields(p,PLAY,CLIENTBOUND,name,Types.TRUSTED_TAG);
        fields(p,PLAY,CLIENTBOUND,"TAB_LIST",Types.TRUSTED_TAG,Types.TRUSTED_TAG);
        fields(p,PLAY,CLIENTBOUND,"SET_TITLES_ANIMATION",Types.INT,Types.INT,Types.INT);
        fields(p,PLAY,CLIENTBOUND,"CLEAR_TITLES",Types.BOOLEAN);
        fields(p,PLAY,CLIENTBOUND,"BLOCK_CHANGED_ACK",Types.VAR_INT);
        fields(p,PLAY,CLIENTBOUND,"BLOCK_DESTRUCTION",Types.VAR_INT,Types.LONG,Types.UNSIGNED_BYTE);
        fields(p,PLAY,CLIENTBOUND,"SET_BORDER_CENTER",Types.DOUBLE,Types.DOUBLE);
        fields(p,PLAY,CLIENTBOUND,"SET_BORDER_SIZE",Types.DOUBLE);
        fields(p,PLAY,CLIENTBOUND,"SET_BORDER_LERP_SIZE",Types.DOUBLE,Types.DOUBLE,Types.VAR_LONG);
        fields(p,PLAY,CLIENTBOUND,"SET_BORDER_WARNING_DELAY",Types.VAR_INT);
        fields(p,PLAY,CLIENTBOUND,"SET_BORDER_WARNING_DISTANCE",Types.VAR_INT);
    }
}
