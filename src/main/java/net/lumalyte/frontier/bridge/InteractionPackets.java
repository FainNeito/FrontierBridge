package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

/** Shared snapshot/26.3 schemas. Earlier-version semantic conversions remain in ViaBackwards. */
final class InteractionPackets {
 static void register(ProtocolSnapshot8To26_3 p) {
  fields(p,PLAY,CLIENTBOUND,"ANIMATE",Types.VAR_INT,Types.UNSIGNED_BYTE);
  fields(p,PLAY,CLIENTBOUND,"ENTITY_EVENT",Types.INT,Types.BYTE);
  fields(p,PLAY,CLIENTBOUND,"HURT_ANIMATION",Types.VAR_INT,Types.FLOAT);
  fields(p,PLAY,CLIENTBOUND,"ROTATE_HEAD",Types.VAR_INT,Types.BYTE);
  fields(p,PLAY,CLIENTBOUND,"SET_ENTITY_LINK",Types.INT,Types.INT);
  int passengers=SessionPackets.type(PLAY,CLIENTBOUND,"SET_PASSENGERS").getId();
  p.registerClientbound(PLAY,passengers,passengers,w->{
   int entity=w.passthrough(Types.VAR_INT);int[] ids=w.passthrough(Types.VAR_INT_ARRAY_PRIMITIVE);
   var state=w.user().get(BridgeConnectionState.class);state.vehicles.passengers(entity,ids.length>0&&state.isPlayer(ids[0]));
  },false);
  fields(p,PLAY,CLIENTBOUND,"TAKE_ITEM_ENTITY",Types.VAR_INT,Types.VAR_INT,Types.VAR_INT);
  fields(p,PLAY,CLIENTBOUND,"PLAYER_COMBAT_END",Types.VAR_INT);
  fields(p,PLAY,CLIENTBOUND,"PLAYER_COMBAT_ENTER");
  fields(p,PLAY,CLIENTBOUND,"SET_CAMERA",Types.VAR_INT);
  fields(p,PLAY,CLIENTBOUND,"CHANGE_DIFFICULTY",Types.VAR_INT,Types.BOOLEAN);
  fields(p,PLAY,CLIENTBOUND,"COOLDOWN",Types.STRING,Types.VAR_INT);
  fields(p,PLAY,CLIENTBOUND,"GAME_EVENT",Types.UNSIGNED_BYTE,Types.FLOAT);
  fields(p,PLAY,CLIENTBOUND,"FORGET_LEVEL_CHUNK",Types.LONG);
  fields(p,PLAY,CLIENTBOUND,"TICKING_STATE",Types.FLOAT,Types.BOOLEAN);
  fields(p,PLAY,CLIENTBOUND,"TICKING_STEP",Types.VAR_INT);
  fields(p,PLAY,CLIENTBOUND,"INITIALIZE_BORDER",Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.VAR_LONG,Types.VAR_INT,Types.VAR_INT,Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"CLIENT_COMMAND",Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"CONTAINER_BUTTON_CLICK",Types.VAR_INT,Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"CONTAINER_SLOT_STATE_CHANGED",Types.VAR_INT,Types.VAR_INT,Types.BOOLEAN);
  fields(p,PLAY,SERVERBOUND,"PLAYER_INPUT",Types.BYTE);
  fields(p,PLAY,SERVERBOUND,"PADDLE_BOAT",Types.BOOLEAN,Types.BOOLEAN);
  fields(p,PLAY,SERVERBOUND,"RENAME_ITEM",Types.STRING);
  fields(p,PLAY,SERVERBOUND,"SELECT_TRADE",Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"PLAYER_ACTION",Types.VAR_INT,Types.LONG,Types.UNSIGNED_BYTE,Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"USE_ITEM",Types.VAR_INT,Types.VAR_INT,Types.FLOAT,Types.FLOAT);
  fields(p,PLAY,SERVERBOUND,"PLAYER_COMMAND",Types.VAR_INT,Types.VAR_INT,Types.VAR_INT);
 }
}
