package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.protocol.packet.*;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

final class MovementPackets {
 static void register(ProtocolSnapshot8To26_3 p) {
  for(String name:new String[]{"MOVE_ENTITY_POS","MOVE_ENTITY_POS_ROT"}) {
   int id=SessionPackets.type(PLAY,CLIENTBOUND,name).getId();
   p.registerClientbound(PLAY,id,id,w->{
    int entity=w.passthrough(Types.VAR_INT);int properties=w.passthrough(Types.VAR_INT);int steps=properties>>>1;
    w.user().get(BridgeConnectionState.class).vehicles.ground(entity,(properties&1)!=0);
    if(steps>65536)throw new IllegalArgumentException("Too many movement steps");
    if(steps>0)w.read(Types.BOOLEAN); // Snapshot-8 immediate interpolation flag has no 26.3 equivalent.
    long x=0,y=0,z=0;short[][] deltas=new short[Math.max(1,steps)][3];
    for(int i=0;i<Math.max(1,steps);i++) {
     if(steps>0)w.passthrough(Types.VAR_INT);
     x+=(deltas[i][0]=w.passthrough(Types.SHORT));y+=(deltas[i][1]=w.passthrough(Types.SHORT));z+=(deltas[i][2]=w.passthrough(Types.SHORT));
    }
    byte yaw=0,pitch=0;boolean rotation=name.equals("MOVE_ENTITY_POS_ROT");
    if(rotation){yaw=w.passthrough(Types.BYTE);pitch=w.passthrough(Types.BYTE);}
    // ViaBackwards collapses steps into a single short per axis. Split overflowing totals first.
    if(x<Short.MIN_VALUE||x>Short.MAX_VALUE||y<Short.MIN_VALUE||y>Short.MAX_VALUE||z<Short.MIN_VALUE||z>Short.MAX_VALUE) {
     for(int i=0;i<steps-1;i++) {
      var part=w.create(com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3.MOVE_ENTITY_POS);
      part.write(Types.VAR_INT,entity);part.write(Types.VAR_INT,properties&1);
      for(short value:deltas[i])part.write(Types.SHORT,value);
      part.send(ProtocolSnapshot8To26_3.class);
     }
     w.clearPacket();w.write(Types.VAR_INT,entity);w.write(Types.VAR_INT,properties&1);
     for(short value:deltas[steps-1])w.write(Types.SHORT,value);
     if(rotation){w.write(Types.BYTE,yaw);w.write(Types.BYTE,pitch);}
    }
   },false);
  }
  int rotate=SessionPackets.type(PLAY,CLIENTBOUND,"MOVE_ENTITY_ROT").getId();
  p.registerClientbound(PLAY,rotate,rotate,w->{
   int entity=w.passthrough(Types.VAR_INT);w.user().get(BridgeConnectionState.class).vehicles.ground(entity,w.passthrough(Types.BOOLEAN));
   w.passthrough(Types.BYTE);w.passthrough(Types.BYTE);
  },false);
  int sync=SessionPackets.type(PLAY,CLIENTBOUND,"ENTITY_POSITION_SYNC").getId();
  p.registerClientbound(PLAY,sync,sync,w->{
   int entity=w.passthrough(Types.VAR_INT);int path=w.passthrough(Types.VAR_INT);
   int steps=path==0?1:path==1?w.passthrough(Types.VAR_INT):-1;
   if(steps<1||steps>65536)throw new IllegalArgumentException("Invalid absolute movement path");
   for(int i=0;i<steps;i++){w.passthrough(Types.DOUBLE);w.passthrough(Types.DOUBLE);w.passthrough(Types.DOUBLE);if(path==1)w.passthrough(Types.VAR_INT);}
   if(path==1)w.read(Types.BOOLEAN); // startImmediately
   w.passthrough(Types.FLOAT);w.passthrough(Types.FLOAT);
   float headYaw=w.read(Types.FLOAT);
   if(!Float.isFinite(headYaw))throw new IllegalArgumentException("Non-finite head rotation");
   w.user().get(BridgeConnectionState.class).vehicles.ground(entity,w.passthrough(Types.BOOLEAN));
   var head=w.create(com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3.ROTATE_HEAD);
   head.write(Types.VAR_INT,entity);head.write(Types.BYTE,(byte)Math.floor(headYaw*256.0F/360.0F));
   head.send(ProtocolSnapshot8To26_3.class);
  },false);
  fields(p,PLAY,CLIENTBOUND,"MOVE_VEHICLE",Types.DOUBLE,Types.DOUBLE,Types.DOUBLE,Types.FLOAT,Types.FLOAT);
  int vehicle=SessionPackets.type(PLAY,SERVERBOUND,"MOVE_VEHICLE").getId();
  p.registerServerbound(PLAY,vehicle,vehicle,w->{
   for(int i=0;i<3;i++)w.passthrough(Types.DOUBLE);w.passthrough(Types.FLOAT);w.passthrough(Types.FLOAT);
   w.user().get(BridgeConnectionState.class).vehicles.clientGround(w.passthrough(Types.BOOLEAN));
  },false);
 }
}
