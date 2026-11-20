package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import static com.viaversion.viaversion.api.protocol.packet.State.*;

final class LoginPackets {
 static void register(ProtocolSnapshot8To26_3 p){
  p.registerServerbound(HANDSHAKE,0,0,w->{w.passthrough(Types.VAR_INT);w.passthrough(Types.STRING);w.passthrough(Types.UNSIGNED_SHORT);w.passthrough(Types.VAR_INT);},false);
  p.registerServerbound(STATUS,0,0,w->{},false);
  p.registerServerbound(STATUS,1,1,w->w.passthrough(Types.LONG),false);
  p.registerClientbound(STATUS,0,0,w->w.passthrough(Types.STRING),false);
  p.registerClientbound(STATUS,1,1,w->w.passthrough(Types.LONG),false);
  p.registerClientbound(LOGIN,0,0,w->w.passthrough(Types.COMPONENT),false);
  p.registerClientbound(LOGIN,1,1,w->{w.passthrough(Types.STRING);w.passthrough(Types.BYTE_ARRAY_PRIMITIVE);w.passthrough(Types.BYTE_ARRAY_PRIMITIVE);w.passthrough(Types.BOOLEAN);},false);
  p.registerClientbound(LOGIN,2,2,w->{w.passthrough(Types.UUID);w.passthrough(Types.STRING);w.passthrough(Types.PROFILE_PROPERTY_ARRAY);w.passthrough(Types.UUID);int flags=w.read(Types.UNSIGNED_BYTE);if((flags&~2)!=0)throw new IllegalArgumentException("Unexpected login-finished connection options");},false);
  p.registerClientbound(LOGIN,3,3,w->w.passthrough(Types.VAR_INT),false);
  p.registerClientbound(LOGIN,4,4,w->{w.passthrough(Types.VAR_INT);w.passthrough(Types.STRING);w.passthrough(Types.REMAINING_BYTES);},false);
  p.registerClientbound(LOGIN,5,5,w->w.passthrough(Types.STRING),false);
  // APPLY_OPTIONS is consumed at the native transport boundary, before encryption.
  p.registerServerbound(LOGIN,0,0,w->{w.passthrough(Types.STRING);w.passthrough(Types.UUID);},false);
  p.registerServerbound(LOGIN,1,1,w->{w.passthrough(Types.BYTE_ARRAY_PRIMITIVE);w.passthrough(Types.BYTE_ARRAY_PRIMITIVE);},false);
  p.registerServerbound(LOGIN,2,2,w->{w.passthrough(Types.VAR_INT);if(w.passthrough(Types.BOOLEAN))w.passthrough(Types.REMAINING_BYTES);},false);
  p.registerServerbound(LOGIN,3,3,w->{},false);
  p.registerServerbound(LOGIN,4,4,w->{w.passthrough(Types.STRING);w.passthrough(Types.OPTIONAL_BYTE_ARRAY_PRIMITIVE);},false);
 }
}
