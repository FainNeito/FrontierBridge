package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

/** Explicit control/UI schemas; registry-bearing payloads live in separate translators. */
final class RemainingControlPackets {
 static void handler(ProtocolSnapshot8To26_3 p,Direction d,String name,PacketHandler h) {
  int id=SessionPackets.type(PLAY,d,name).getId();
  if(d==CLIENTBOUND)p.registerClientbound(PLAY,id,id,h,false);else p.registerServerbound(PLAY,id,id,h,false);
 }
 static int count(PacketWrapper w,int max) {int n=w.passthrough(Types.VAR_INT);if(n<0||n>max)throw new IllegalArgumentException("Invalid collection length "+n);return n;}
 static void rules(PacketWrapper w) {int n=count(w,128);for(int i=0;i<n;i++){w.passthrough(Types.STRING);w.passthrough(Types.STRING);}}
 static void format(PacketWrapper w) {
  if(!w.passthrough(Types.BOOLEAN))return;
  int kind=w.passthrough(Types.VAR_INT);
  switch(kind){case 0->{} case 1->w.passthrough(Types.TRUSTED_TAG);case 2->w.passthrough(Types.TRUSTED_TAG);default->throw new IllegalArgumentException("Unknown number format");}
 }
 static void register(ProtocolSnapshot8To26_3 p) {
  fields(p,PLAY,SERVERBOUND,"CHANGE_GAME_MODE",Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"CHAT_ACK",Types.VAR_INT);
  handler(p,SERVERBOUND,"CHAT_SESSION_UPDATE",w->{w.passthrough(Types.UUID);w.passthrough(Types.PROFILE_KEY);w.user().get(BridgeConnectionState.class).chatSessionsReceived++;});
  fields(p,PLAY,SERVERBOUND,"JIGSAW_GENERATE",Types.BLOCK_POSITION1_14,Types.VAR_INT,Types.BOOLEAN);
  fields(p,PLAY,SERVERBOUND,"SET_COMMAND_BLOCK",Types.BLOCK_POSITION1_14,Types.STRING,Types.VAR_INT,Types.BYTE);
  fields(p,PLAY,SERVERBOUND,"SET_COMMAND_MINECART",Types.VAR_INT,Types.STRING,Types.BOOLEAN);
  handler(p,SERVERBOUND,"SET_GAME_RULE",RemainingControlPackets::rules);
  handler(p,CLIENTBOUND,"GAME_RULE_VALUES",RemainingControlPackets::rules);
  fields(p,PLAY,SERVERBOUND,"SET_JIGSAW_BLOCK",Types.BLOCK_POSITION1_14,Types.STRING,Types.STRING,Types.STRING,Types.STRING,Types.STRING,Types.VAR_INT,Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"SET_STRUCTURE_BLOCK",Types.BLOCK_POSITION1_14,Types.VAR_INT,Types.VAR_INT,Types.STRING,Types.BYTE,Types.BYTE,Types.BYTE,Types.BYTE,Types.BYTE,Types.BYTE,Types.VAR_INT,Types.VAR_INT,Types.STRING,Types.FLOAT,Types.VAR_LONG,Types.BYTE);
  fields(p,PLAY,SERVERBOUND,"SET_TEST_BLOCK",Types.BLOCK_POSITION1_14,Types.VAR_INT,Types.STRING);
  fields(p,PLAY,SERVERBOUND,"SPECTATOR_ACTION",Types.OPTIONAL_VAR_INT);
  fields(p,PLAY,SERVERBOUND,"TEST_INSTANCE_BLOCK_ACTION",Types.BLOCK_POSITION1_14,Types.VAR_INT,Types.OPTIONAL_STRING,Types.VAR_INT,Types.VAR_INT,Types.VAR_INT,Types.VAR_INT,Types.BOOLEAN,Types.VAR_INT,Types.OPTIONAL_TAG);
  fields(p,PLAY,CLIENTBOUND,"GAME_TEST_HIGHLIGHT_POS",Types.BLOCK_POSITION1_14,Types.BLOCK_POSITION1_14);
  fields(p,PLAY,CLIENTBOUND,"MOUNT_SCREEN_OPEN",Types.VAR_INT,Types.VAR_INT,Types.INT);
  fields(p,PLAY,CLIENTBOUND,"LOW_DISK_SPACE_WARNING");
  fields(p,PLAY,CLIENTBOUND,"PLAYER_ROTATION",Types.FLOAT,Types.BOOLEAN,Types.FLOAT,Types.BOOLEAN);
  fields(p,PLAY,CLIENTBOUND,"SET_DEFAULT_SPAWN_POSITION",Types.GLOBAL_POSITION,Types.FLOAT,Types.FLOAT);
  fields(p,PLAY,CLIENTBOUND,"SET_ENTITY_MOTION",Types.VAR_INT,Types.LOW_PRECISION_VECTOR);
  fields(p,PLAY,CLIENTBOUND,"TAG_QUERY",Types.VAR_INT,Types.COMPOUND_TAG);
  handler(p,CLIENTBOUND,"TELEPORT_ENTITY",w->{
   int entity=w.passthrough(Types.VAR_INT);for(int i=0;i<6;i++)w.passthrough(Types.DOUBLE);
   w.passthrough(Types.FLOAT);w.passthrough(Types.FLOAT);w.passthrough(Types.INT);
   w.user().get(BridgeConnectionState.class).vehicles.ground(entity,w.passthrough(Types.BOOLEAN));
  });
  fields(p,PLAY,CLIENTBOUND,"DEBUG_SAMPLE",Types.LONG_ARRAY_PRIMITIVE,Types.VAR_INT);
  handler(p,CLIENTBOUND,"TEST_INSTANCE_BLOCK_STATUS",w->{w.passthrough(Types.TAG);if(w.passthrough(Types.BOOLEAN)){for(int i=0;i<3;i++)w.passthrough(Types.VAR_INT);}});
  handler(p,CLIENTBOUND,"MOVE_MINECART_ALONG_TRACK",w->{w.passthrough(Types.VAR_INT);int n=count(w,65536);for(int i=0;i<n;i++){for(int j=0;j<6;j++)w.passthrough(Types.DOUBLE);w.passthrough(Types.BYTE);w.passthrough(Types.BYTE);w.passthrough(Types.FLOAT);}});
  handler(p,CLIENTBOUND,"SET_OBJECTIVE",w->{w.passthrough(Types.STRING);byte method=w.passthrough(Types.BYTE);if(method==0||method==2){w.passthrough(Types.TRUSTED_TAG);w.passthrough(Types.VAR_INT);format(w);}});
  handler(p,CLIENTBOUND,"SET_SCORE",w->{w.passthrough(Types.STRING);w.passthrough(Types.STRING);w.passthrough(Types.VAR_INT);w.passthrough(Types.TRUSTED_OPTIONAL_TAG);format(w);});
  handler(p,CLIENTBOUND,"SET_PLAYER_TEAM",w->{w.passthrough(Types.STRING);byte method=w.passthrough(Types.BYTE);if(method==0||method==2){for(int i=0;i<3;i++)w.passthrough(Types.TRUSTED_TAG);w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);w.passthrough(Types.BOOL_OPTIONAL_VAR_INT);w.passthrough(Types.BYTE);}if(method==0||method==3||method==4)w.passthrough(Types.STRING_ARRAY);});
  handler(p,CLIENTBOUND,"BOSS_EVENT",w->{w.passthrough(Types.UUID);int action=w.passthrough(Types.VAR_INT);switch(action){
   case 0->{w.passthrough(Types.TRUSTED_TAG);w.passthrough(Types.FLOAT);w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);w.passthrough(Types.BYTE);}
   case 1->{} case 2->w.passthrough(Types.FLOAT);case 3->w.passthrough(Types.TRUSTED_TAG);case 4->{w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);}case 5->w.passthrough(Types.BYTE);default->throw new IllegalArgumentException("Unknown boss operation");}});
 }
}


