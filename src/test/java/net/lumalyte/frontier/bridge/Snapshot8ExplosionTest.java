package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class Snapshot8ExplosionTest {
 public static void main(String[] args)throws Exception {
  var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);var results=new JsonArray();
  for(var value:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()){
   var row=value.getAsJsonObject();var input=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.get("hex").getAsString()));var out=Unpooled.buffer();
   try{
    var w=new PacketWrapperImpl(ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,ClientboundPackets26_3.EXPLODE.getId()),input,u);
    p.transform(Direction.CLIENTBOUND,State.PLAY,w);w.writeToBuffer(out);
    BridgeTest.check(Types.VAR_INT.readPrimitive(out)==ClientboundPackets26_3.EXPLODE.getId(),"Wrong explosion packet");
    var result=new JsonObject();result.add("mask",row.get("mask"));result.addProperty("hex",ByteBufUtil.hexDump(out));results.add(result);
   }finally{input.release();out.release();}
  }
  Files.writeString(Path.of(args[1]),new Gson().toJson(results));System.out.println("Translated native explosion cases: "+results.size());
 }
}
