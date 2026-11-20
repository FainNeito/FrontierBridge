package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class Snapshot8MovementCodecTest {
 public static void main(String[] args)throws Exception {
  var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var user=ConnectionPipelineTest.user();p.init(user);
  var rows=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();var results=new JsonArray();int heads=0;
  for(var value:rows){var row=value.getAsJsonObject();var type=ClientboundPackets26_3.valueOf(row.get("name").getAsString());
   var input=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.get("hex").getAsString()));var output=Unpooled.buffer();var generated=new ArrayList<byte[]>();
   try {
    var wrapper=new PacketWrapperImpl(ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,type.getId()),input,user){
     @Override public PacketWrapperImpl create(PacketType packetType){return new PacketWrapperImpl(packetType,null,user){
      @Override public void send(Class<? extends Protocol> protocol,boolean skip){
       BridgeTest.check(protocol==ProtocolSnapshot8To26_3.class&&skip,"Head packet must skip source translation");
       var buf=Unpooled.buffer();try{writeToBuffer(buf);byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);generated.add(bytes);}finally{buf.release();}
      }
     };}
    };
    p.transform(Direction.CLIENTBOUND,State.PLAY,wrapper);wrapper.writeToBuffer(output);
    BridgeTest.check(Types.VAR_INT.readPrimitive(output)==type.getId(),"Wrong target movement ID");
    byte[] bytes=new byte[output.readableBytes()];output.readBytes(bytes);var translated=row.deepCopy();translated.addProperty("targetHex",HexFormat.of().formatHex(bytes));results.add(translated);
    if(type==ClientboundPackets26_3.ENTITY_POSITION_SYNC){
     BridgeTest.check(generated.size()==1,"Expected exactly one head update");var head=Unpooled.wrappedBuffer(generated.getFirst());
     try{BridgeTest.check(Types.VAR_INT.readPrimitive(head)==ClientboundPackets26_3.ROTATE_HEAD.getId()&&Types.VAR_INT.readPrimitive(head)==123&&head.readByte()==-64&&!head.isReadable(),"Head angle/entity lost");}finally{head.release();}heads++;
    }else BridgeTest.check(generated.isEmpty(),"Unexpected generated packet");
   }finally{input.release();output.release();}
  }
  Files.writeString(Path.of(args[1]),new Gson().toJson(results));
  var report=Map.of("nativeMovementFixtures",rows.size(),"headUpdates",heads,"passed",true,"networkTest",false);
  Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
 }
}
