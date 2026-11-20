package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class VehicleMotionStateTest {
 static int checks;
 static void rejected(Runnable action){try{action.run();throw new AssertionError("Unsafe vehicle state accepted");}catch(IllegalStateException expected){checks++;}}
 public static void main(String[] args)throws Exception {
  var state=new VehicleMotionState();
  rejected(()->state.requireGround(987));state.spawn(987);state.passengers(987,true);
  rejected(()->state.requireGround(987));state.clientGround(true);BridgeTest.check(state.requireGround(987),"Client ground lost");checks++;
  state.ground(987,false);BridgeTest.check(!state.requireGround(987),"Air state lost");checks++;
  state.passengers(987,false);rejected(()->state.requireGround(987));
  state.passengers(987,true);state.remove(987);rejected(()->state.requireGround(987));
  state.spawn(987);rejected(()->state.requireGround(987));state.passengers(987,true);rejected(()->state.requireGround(987));
  state.clientGround(false);state.clear();rejected(()->state.requireGround(987));
  var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var user=ConnectionPipelineTest.user();p.init(user);
  var connection=user.get(BridgeConnectionState.class);connection.player(123);connection.vehicles.spawn(987);connection.vehicles.passengers(987,true);connection.vehicles.clientGround(true);
  byte[] fixture=HexFormat.of().parseHex(JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray().get(1).getAsJsonObject().get("hex").getAsString());
  var original=Unpooled.wrappedBuffer(fixture);Types.VAR_INT.readPrimitive(original);var vector=Types.LOW_PRECISION_VECTOR.read(original);original.release();
  var input=Unpooled.buffer();var output=Unpooled.buffer();
  try{
   Types.VAR_INT.writePrimitive(input,987);Types.LOW_PRECISION_VECTOR.write(input,vector);
   connection.vehicleImpulseTesting=true;
   var packet=new PacketWrapperImpl(2,input,user);p.transform(Direction.CLIENTBOUND,State.PLAY,packet);packet.writeToBuffer(output);
   BridgeTest.check(Types.VAR_INT.readPrimitive(output)==com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3.TELEPORT_ENTITY.getId(),"Opt-in route missing");
   BridgeTest.check(Types.VAR_INT.readPrimitive(output)==987,"Vehicle target changed");for(int i=0;i<3;i++)BridgeTest.check(output.readDouble()==0,"Position changed");
   BridgeTest.check(output.readDouble()==vector.x()&&output.readDouble()==vector.y()&&output.readDouble()==vector.z(),"Impulse changed");
   BridgeTest.check(output.readFloat()==0&&output.readFloat()==0&&output.readInt()==255&&output.readBoolean()&&!output.isReadable(),"Ground/flags changed");checks++;
  }finally{input.release();output.release();}
  connection.reset();rejected(()->connection.vehicles.requireGround(987));
  var report=Map.of("passed",true,"checks",checks,"liveGameplayValidated",false,"defaultEnabled",Boolean.getBoolean("frontierbridge.vehicleImpulses"));
  Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
 }
}
