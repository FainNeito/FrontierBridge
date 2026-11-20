package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static net.lumalyte.frontier.bridge.RemainingControlPackets.*;

final class DebugPackets {
 static final BridgeMappings BLOCKS=new BridgeMappings();
 static void positions(PacketWrapper w){int n=count(w,65536);for(int i=0;i<n;i++)w.passthrough(Types.BLOCK_POSITION1_14);}
 static void optionalPosition(PacketWrapper w){if(w.passthrough(Types.BOOLEAN))w.passthrough(Types.BLOCK_POSITION1_14);}
 static void nodes(PacketWrapper w){int n=count(w,65536);for(int i=0;i<n;i++){for(int j=0;j<3;j++)w.passthrough(Types.INT);w.passthrough(Types.FLOAT);w.passthrough(Types.FLOAT);w.passthrough(Types.BOOLEAN);w.passthrough(Types.VAR_INT);w.passthrough(Types.FLOAT);}}
 static void value(PacketWrapper w,boolean optional){int id=w.passthrough(Types.VAR_INT);if(id<1||id>15)throw new IllegalArgumentException("Unknown debug value subscription");if(optional&&!w.passthrough(Types.BOOLEAN))return;switch(id){
  case 1->{optionalPosition(w);optionalPosition(w);w.passthrough(Types.VAR_INT);positions(w);}
  case 2->{w.passthrough(Types.STRING);w.passthrough(Types.STRING);w.passthrough(Types.INT);w.passthrough(Types.FLOAT);w.passthrough(Types.FLOAT);w.passthrough(Types.STRING);w.passthrough(Types.BOOLEAN);w.passthrough(Types.INT);for(int i=0;i<4;i++)w.passthrough(Types.STRING_ARRAY);positions(w);positions(w);}
  case 3->{w.passthrough(Types.BOOL_OPTIONAL_VAR_INT);optionalPosition(w);}
  case 4->{int n=count(w,65536);for(int i=0;i<n;i++){w.passthrough(Types.VAR_INT);w.passthrough(Types.BOOLEAN);w.passthrough(Types.STRING);}}
  case 5->{w.passthrough(Types.BOOLEAN);w.passthrough(Types.INT);w.passthrough(Types.BLOCK_POSITION1_14);for(int i=0;i<4;i++)nodes(w);w.passthrough(Types.FLOAT);w.read(Types.VAR_INT); /* New stuck countdown has no 26.3 debug rendering. */}
  case 6,9,13->w.passthrough(Types.VAR_INT);
  case 7->{w.write(Types.VAR_INT,BLOCKS.block(w.read(Types.VAR_INT)));w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);w.passthrough(Types.BOOLEAN);}
  case 8->{w.passthrough(Types.BLOCK_POSITION1_14);w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);}
  case 10->{}
  case 11->positions(w);
  case 12->{int n=count(w,65536);for(int i=0;i<n;i++){w.passthrough(Types.BLOCK_POSITION1_14);w.passthrough(Types.BLOCK_POSITION1_14);int pieces=count(w,65536);for(int j=0;j<pieces;j++){w.passthrough(Types.BLOCK_POSITION1_14);w.passthrough(Types.BLOCK_POSITION1_14);w.passthrough(Types.BOOLEAN);}}}
  case 14->w.passthrough(Types.BLOCK_POSITION1_14);
  case 15->{w.passthrough(Types.VAR_INT);for(int i=0;i<3;i++)w.passthrough(Types.DOUBLE);}
 }}
 static void register(ProtocolSnapshot8To26_3 p){
  handler(p,SERVERBOUND,"DEBUG_SUBSCRIPTION_REQUEST",w->{int n=count(w,32);for(int i=0;i<n;i++){int id=w.passthrough(Types.VAR_INT);if(id<0||id>15)throw new IllegalArgumentException("Unknown debug subscription");}});
  handler(p,CLIENTBOUND,"DEBUG_BLOCK_VALUE",w->{w.passthrough(Types.BLOCK_POSITION1_14);value(w,true);});
  handler(p,CLIENTBOUND,"DEBUG_CHUNK_VALUE",w->{w.passthrough(Types.LONG);value(w,true);});
  handler(p,CLIENTBOUND,"DEBUG_ENTITY_VALUE",w->{w.passthrough(Types.VAR_INT);value(w,true);});
  handler(p,CLIENTBOUND,"DEBUG_EVENT",w->value(w,false));
 }
}
