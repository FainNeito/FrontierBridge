package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.minecraft.item.data.ChatType;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static net.lumalyte.frontier.bridge.RemainingControlPackets.*;

final class ChatPackets {
 static void packedSignature(PacketWrapper w) {if(w.passthrough(Types.VAR_INT)==0)w.passthrough(Types.SIGNATURE_BYTES);}
 static void acknowledgement(PacketWrapper w){w.passthrough(Types.VAR_INT);w.passthrough(Types.ACKNOWLEDGED_BIT_SET);w.passthrough(Types.BYTE);}
 static void bound(PacketWrapper w){w.passthrough(ChatType.TYPE);w.passthrough(Types.TRUSTED_TAG);w.passthrough(Types.TRUSTED_OPTIONAL_TAG);}
 static void register(ProtocolSnapshot8To26_3 p) {
  handler(p,SERVERBOUND,"CHAT",w->{w.passthrough(Types.STRING);w.passthrough(Types.LONG);w.passthrough(Types.LONG);boolean signed=w.passthrough(Types.OPTIONAL_SIGNATURE_BYTES)!=null;acknowledgement(w);var state=w.user().get(BridgeConnectionState.class);if(signed)state.signedChatsReceived++;else state.unsignedChatsReceived++;});
  handler(p,SERVERBOUND,"CHAT_COMMAND_SIGNED",w->{w.passthrough(Types.STRING);w.passthrough(Types.LONG);w.passthrough(Types.LONG);int n=count(w,8);for(int i=0;i<n;i++){w.passthrough(Types.STRING);w.passthrough(Types.SIGNATURE_BYTES);}acknowledgement(w);});
  handler(p,CLIENTBOUND,"DELETE_CHAT",ChatPackets::packedSignature);
  handler(p,CLIENTBOUND,"DISGUISED_CHAT",w->{w.passthrough(Types.TRUSTED_TAG);bound(w);});
  handler(p,CLIENTBOUND,"PLAYER_CHAT",w->{w.passthrough(Types.VAR_INT);w.passthrough(Types.UUID);w.passthrough(Types.VAR_INT);w.passthrough(Types.OPTIONAL_SIGNATURE_BYTES);w.passthrough(Types.STRING);w.passthrough(Types.LONG);w.passthrough(Types.LONG);int n=count(w,20);for(int i=0;i<n;i++)packedSignature(w);w.passthrough(Types.TRUSTED_OPTIONAL_TAG);int filter=w.passthrough(Types.VAR_INT);if(filter==2)w.passthrough(Types.BIT_SET);else if(filter!=0&&filter!=1)throw new IllegalArgumentException("Unknown chat filter");bound(w);});
 }
}

