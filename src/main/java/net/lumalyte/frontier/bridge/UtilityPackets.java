package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

final class UtilityPackets {
 static void register(ProtocolSnapshot8To26_3 p) {
  fields(p,PLAY,CLIENTBOUND,"BUNDLE_DELIMITER");
  fields(p,PLAY,CLIENTBOUND,"SERVER_DATA",Types.TRUSTED_TAG,Types.OPTIONAL_BYTE_ARRAY_PRIMITIVE);
  fields(p,PLAY,CLIENTBOUND,"OPEN_SIGN_EDITOR",Types.BLOCK_POSITION1_14,Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"SET_BEACON",Types.BOOL_OPTIONAL_VAR_INT,Types.BOOL_OPTIONAL_VAR_INT);
  fields(p,PLAY,CLIENTBOUND,"PONG_RESPONSE",Types.LONG);
  fields(p,PLAY,SERVERBOUND,"PING_REQUEST",Types.LONG);
  fields(p,PLAY,CLIENTBOUND,"SHOW_DIALOG",Types.TRUSTED_COMPOUND_TAG_HOLDER);
  int id=SessionPackets.type(PLAY,SERVERBOUND,"SIGN_UPDATE").getId();
  p.registerServerbound(PLAY,id,id,w->{
   w.passthrough(Types.BLOCK_POSITION1_14);for(int i=0;i<4;i++){String line=w.passthrough(Types.STRING);if(line.length()>384)throw new IllegalArgumentException("Sign line too long");}
   int side=w.passthrough(Types.VAR_INT);if(side<0||side>1)throw new IllegalArgumentException("Unknown sign side");
  },false);
 }
}
