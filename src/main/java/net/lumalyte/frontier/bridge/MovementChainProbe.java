package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;

final class MovementChainProbe {
 static int run(UserConnectionImpl user,List<Protocol> down,Protocol<?,?,?,?> oldest,EmbeddedChannel channel)throws Exception {
  ByteBuf pending;while((pending=channel.readOutbound())!=null)pending.release();int count=0;
  for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1})for(boolean rotation:new boolean[]{false,true}) {
   var input=Unpooled.buffer();var output=Unpooled.buffer();
   try {
    Types.VAR_INT.writePrimitive(input,9123);Types.VAR_INT.writePrimitive(input,7); // Three steps, on ground.
    input.writeBoolean(true); // Snapshot-8 immediate interpolation flag.
    for(int step=0;step<3;step++){Types.VAR_INT.writePrimitive(input,step+1);for(int a=0;a<3;a++)input.writeShort(a==axis?sign*20000:0);}
    if(rotation)input.writeByte(127).writeByte(255);
    String name=rotation?"MOVE_ENTITY_POS_ROT":"MOVE_ENTITY_POS";
    var packet=new PacketWrapperImpl(ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,SessionPackets.type(State.PLAY,Direction.CLIENTBOUND,name).getId()),input,user);
    packet.apply(Direction.CLIENTBOUND,State.PLAY,down);packet.writeToBuffer(output);
    for(int step=0;step<3;step++) {
     ByteBuf actual=step==2?output:channel.readOutbound();if(actual==null)throw new IllegalStateException("Missing split movement");
     try {
      String expected=step==2?name:"MOVE_ENTITY_POS";
      if(Types.VAR_INT.readPrimitive(actual)!=oldest.getPacketTypesProvider().mappedClientboundType(State.PLAY,expected).getId()||Types.VAR_INT.readPrimitive(actual)!=9123)throw new IllegalStateException("Wrong split movement packet");
      for(int a=0;a<3;a++)if(actual.readShort()!=(a==axis?sign*20000:0))throw new IllegalStateException("Movement overflow or wrong split order");
      if(step==2&&rotation&&(actual.readUnsignedByte()!=127||actual.readUnsignedByte()!=255))throw new IllegalStateException("Rotation lost");
      if(!actual.readBoolean()||actual.isReadable())throw new IllegalStateException("Invalid split movement tail");
     }finally{if(step!=2)actual.release();}
    }
    if((pending=channel.readOutbound())!=null){pending.release();throw new IllegalStateException("Extra split movement packet");}count++;
   }finally{input.release();output.release();}
  }
  return count;
 }
}
