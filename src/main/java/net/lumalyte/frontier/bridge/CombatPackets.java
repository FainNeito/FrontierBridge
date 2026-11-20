package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.minecraft.item.data.SwingAnimation;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

final class CombatPackets {
 static void register(ProtocolSnapshot8To26_3 p) {
  fields(p,PLAY,SERVERBOUND,"ATTACK",Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"PUNCH");
  fields(p,PLAY,SERVERBOUND,"INTERACT",Types.VAR_INT,Types.VAR_INT,Types.LOW_PRECISION_VECTOR,Types.BOOLEAN);
  fields(p,PLAY,CLIENTBOUND,"SWING_ANIMATION",Types.VAR_INT,Types.VAR_INT,SwingAnimation.TYPE);
 }
}
