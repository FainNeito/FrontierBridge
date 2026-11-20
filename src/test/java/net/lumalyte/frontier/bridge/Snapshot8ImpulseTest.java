package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class Snapshot8ImpulseTest {
 public static void main(String[] args)throws Exception {
  var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);var state=u.get(BridgeConnectionState.class);state.player(123);
  var rows=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();var results=new JsonArray();
  for(var value:rows){var row=value.getAsJsonObject();byte[] source=HexFormat.of().parseHex(row.get("hex").getAsString());
   var input=Unpooled.wrappedBuffer(source);var out=Unpooled.buffer();var decoded=Unpooled.wrappedBuffer(source);
   try{
    Types.VAR_INT.readPrimitive(decoded);var impulse=Types.LOW_PRECISION_VECTOR.read(decoded);
    var w=new PacketWrapperImpl(2,input,u);p.transform(Direction.CLIENTBOUND,State.PLAY,w);w.writeToBuffer(out);
    BridgeTest.check(Types.VAR_INT.readPrimitive(out)==ClientboundPackets26_3.EXPLODE.getId(),"Impulse must use additive knockback");
    var r=new JsonObject();r.addProperty("hex",ByteBufUtil.hexDump(out));r.addProperty("x",impulse.x());r.addProperty("y",impulse.y());r.addProperty("z",impulse.z());results.add(r);
    for(int i=0;i<3;i++)BridgeTest.check(out.readDouble()==0,"Unexpected explosion center");
    BridgeTest.check(out.readFloat()==0&&out.readInt()==0&&out.readBoolean(),"Impulse caused terrain effects or lost knockback");
    BridgeTest.check(out.readDouble()==impulse.x()&&out.readDouble()==impulse.y()&&out.readDouble()==impulse.z(),"Impulse vector changed");
    var particle=new CodecContext(false).types.particle.read(out);BridgeTest.check((Integer)particle.getArgument(0).getValue()==0,"Impulse has visible block sprite");
    var sound=Types.SOUND_EVENT.read(out);BridgeTest.check(!sound.hasId()&&sound.value().identifier().equals("minecraft:intentionally_empty"),"Impulse makes extra sound");
    BridgeTest.check(Types.VAR_INT.readPrimitive(out)==0&&!out.readBoolean()&&!out.isReadable(),"Unexpected impulse tail");
   }finally{input.release();out.release();decoded.release();}
  }
  BridgeTest.check(!state.isPlayer(999),"Vehicle mistaken for player");state.reset();BridgeTest.check(!state.isPlayer(123),"Player identity survived reset");
  Files.writeString(Path.of(args[1]),new Gson().toJson(results));
  var report=Map.of("passed",true,"nativePlayerImpulseCases",rows.size(),"silent",true,"controlledVehiclesValidated",false,"networkTest",false);
  Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
 }
}
