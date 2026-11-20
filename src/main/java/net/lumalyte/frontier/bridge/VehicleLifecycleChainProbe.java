package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import io.netty.buffer.*;
import java.util.*;

/** Replay real spawn/mount/movement/impulse/removal handlers, rather than seeding tracker state. */
final class VehicleLifecycleChainProbe {
 @FunctionalInterface interface Encode {void write(ByteBuf b)throws Exception;}
 static int run(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,JsonObject fixtures)throws Exception {
  int type=-1;for(var value:fixtures.getAsJsonArray("metadata")){var row=value.getAsJsonObject();if(row.get("key").getAsString().equals("minecraft:oak_boat"))type=row.get("type").getAsInt();}
  byte[] spawn=null;for(var value:fixtures.getAsJsonArray("entities")){var row=value.getAsJsonObject();if(row.get("type").getAsInt()==type)spawn=HexFormat.of().parseHex(row.get("hex").getAsString());}
  if(spawn==null)throw new IllegalStateException("Missing native boat fixture");
  var state=user.get(BridgeConnectionState.class);boolean enabled=state.vehicleImpulseTesting;int checks=0;
  try{
   state.vehicleImpulseTesting=true;
   final byte[] boat=spawn;
   send(user,down,ClientboundPackets26_3.ADD_ENTITY,b->b.writeBytes(boat));
   passengers(user,down,true);missing(state);checks++;
   for(boolean grounded:new boolean[]{true,false}){
    send(user,down,ClientboundPackets26_3.MOVE_ENTITY_ROT,b->{Types.VAR_INT.writePrimitive(b,9123);b.writeBoolean(grounded).writeByte(17).writeByte(-8);});
    if(state.vehicles.requireGround(9123)!=grounded)throw new IllegalStateException("Ground packet not tracked");checks++;
    var input=Unpooled.buffer();var output=Unpooled.buffer();
    try{
     Types.VAR_INT.writePrimitive(input,9123);Types.LOW_PRECISION_VECTOR.write(input,new com.viaversion.viaversion.api.minecraft.Vector3d(0.25,0.5,-0.125));
     var decoded=input.duplicate();Types.VAR_INT.readPrimitive(decoded);var expected=Types.LOW_PRECISION_VECTOR.read(decoded);
     var packet=new PacketWrapperImpl(2,input,user);packet.apply(Direction.CLIENTBOUND,State.PLAY,down);packet.writeToBuffer(output);
     if(Types.VAR_INT.readPrimitive(output)!=oldest.getPacketTypesProvider().mappedClientboundType(State.PLAY,"TELEPORT_ENTITY").getId()||Types.VAR_INT.readPrimitive(output)!=9123)throw new IllegalStateException("Registered vehicle handler target mismatch");
     for(int i=0;i<3;i++)if(output.readDouble()!=0)throw new IllegalStateException("Vehicle moved position");
     double x=output.readDouble(),y=output.readDouble(),z=output.readDouble();float yaw=output.readFloat(),pitch=output.readFloat();int flags=output.readInt();boolean ground=output.readBoolean();
     if(x!=expected.x()||y!=expected.y()||z!=expected.z()||yaw!=0||pitch!=0||flags!=255||ground!=grounded||output.isReadable())throw new IllegalStateException("Registered vehicle impulse changed: "+x+","+y+","+z+" flags="+flags+" ground="+ground);checks++;
    }finally{input.release();output.release();}
   }
   passengers(user,down,false);missing(state);checks++;
   passengers(user,down,true);
   send(user,down,ClientboundPackets26_3.REMOVE_ENTITIES,b->Types.VAR_INT_ARRAY_PRIMITIVE.write(b,new int[]{9123}));missing(state);checks++;
   send(user,down,ClientboundPackets26_3.ADD_ENTITY,b->b.writeBytes(boat));missing(state);checks++;
   passengers(user,down,true);missing(state);checks++;
  }finally{
   state.vehicleImpulseTesting=enabled;
   send(user,down,ClientboundPackets26_3.REMOVE_ENTITIES,b->Types.VAR_INT_ARRAY_PRIMITIVE.write(b,new int[]{9123}));
  }
  return checks;
 }
 static void missing(BridgeConnectionState state){try{state.vehicles.requireGround(9123);throw new AssertionError("Stale mount/ground accepted");}catch(IllegalStateException expected){}}
 static void passengers(UserConnectionImpl user,List<Protocol> down,boolean mounted)throws Exception{
  send(user,down,ClientboundPackets26_3.SET_PASSENGERS,b->{Types.VAR_INT.writePrimitive(b,9123);Types.VAR_INT_ARRAY_PRIMITIVE.write(b,mounted?new int[]{123}:new int[0]);});
 }
 static void send(UserConnectionImpl user,List<Protocol> down,ClientboundPackets26_3 type,Encode encode)throws Exception{
  var input=Unpooled.buffer();var output=Unpooled.buffer();
  try{encode.write(input);var packet=new PacketWrapperImpl(ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,type.getId()),input,user);packet.apply(Direction.CLIENTBOUND,State.PLAY,down);packet.writeToBuffer(output);if(!output.isReadable())throw new IllegalStateException("Empty lifecycle packet");}
  finally{input.release();output.release();}
 }
}
