package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.protocol.packet.*;
import java.util.*;
import java.nio.file.*;
public final class MovementGuardTest {
 public static void main(String[] args)throws Exception {
  var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);int count=0;
  var field=com.viaversion.viaversion.api.protocol.AbstractProtocol.class.getDeclaredField("clientboundMappings");field.setAccessible(true);
  var mappings=(com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings)field.get(p);
  for(String[] test:new String[][]{
   {"MOVE_ENTITY_POS","a347828008","Too many movement"},
   {"MOVE_ENTITY_POS_ROT","a347ffffffff0f","Too many movement"},
   {"ENTITY_POSITION_SYNC","a34702","Invalid absolute"},
   {"ENTITY_POSITION_SYNC","a3470100","Invalid absolute"}
  }) {
   boolean rejected=false;
   var input=io.netty.buffer.Unpooled.wrappedBuffer(HexFormat.of().parseHex(test[1]));
   try{int id=SessionPackets.type(State.PLAY,Direction.CLIENTBOUND,test[0]).getId();mappings.mappedPacket(State.PLAY,id).handler().handle(new com.viaversion.viaversion.protocol.packet.PacketWrapperImpl(id,input,u));}
   catch(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();rejected=cause instanceof IllegalArgumentException&&cause.getMessage().contains(test[2]);}
   finally{input.release();}
   BridgeTest.check(rejected,"Movement guard did not reject "+test[0]);count++;
  }
  Files.writeString(Path.of(args[0]),new com.google.gson.Gson().toJson(Map.of("rejectedUnsafePaths",count,"passed",true)));
 }
}
