package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import io.netty.buffer.*;
import java.nio.file.*;
public final class VehicleImpulseCandidateTest {
 public static void main(String[] args)throws Exception {
  var rows=new JsonArray();var user=ConnectionPipelineTest.user();
  double[][] impulses={{0,0,0},{0.25,0.5,-0.125},{-2,1,3},{8,-4,-8}};
  for(boolean ground:new boolean[]{false,true})for(var v:impulses){
   var out=Unpooled.buffer();
   try{
    var packet=new PacketWrapperImpl(ClientboundPackets26_3.TELEPORT_ENTITY,null,user);
    VehicleImpulseCandidate.write(packet,987,v[0],v[1],v[2],ground);packet.writeToBuffer(out);
    BridgeTest.check(Types.VAR_INT.readPrimitive(out)==ClientboundPackets26_3.TELEPORT_ENTITY.getId(),"Wrong vehicle packet");
    var row=new JsonObject();row.addProperty("hex",ByteBufUtil.hexDump(out));row.addProperty("x",v[0]);row.addProperty("y",v[1]);row.addProperty("z",v[2]);row.addProperty("onGround",ground);rows.add(row);
   }finally{out.release();}
  }
  Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(rows));
  System.out.println("Vehicle impulse candidate fixtures: "+rows.size());
 }
}
