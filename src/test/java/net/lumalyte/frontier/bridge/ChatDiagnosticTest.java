package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.minecraft.ProfileKey;
import com.viaversion.viaversion.api.protocol.packet.*;
import io.netty.buffer.Unpooled;
import java.util.*;
public final class ChatDiagnosticTest {
 public static void main(String[] args)throws Exception {
  var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);
  for(String name:List.of("CHAT_SESSION_UPDATE","CHAT","CHAT")) {
   var b=Unpooled.buffer();try {
    if(name.equals("CHAT_SESSION_UPDATE")){Types.UUID.write(b,new UUID(1,2));Types.PROFILE_KEY.write(b,new ProfileKey(1000,new byte[]{1,2,3},new byte[]{4,5,6}));}
    else {Types.STRING.write(b,"synthetic test");b.writeLong(1000);b.writeLong(1);Types.OPTIONAL_SIGNATURE_BYTES.write(b,u.get(BridgeConnectionState.class).signedChatsReceived==0?new byte[256]:null);Types.VAR_INT.write(b,0);Types.ACKNOWLEDGED_BIT_SET.write(b,new BitSet());b.writeByte(0);}
    byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);
    var result=ConnectionPipelineTest.rewrite(p,u,Direction.SERVERBOUND,State.PLAY,SessionPackets.type(State.PLAY,Direction.SERVERBOUND,name).getId(),bytes);
    BridgeTest.check(Arrays.equals(bytes,result),"Diagnostic modified chat payload");
   }finally{b.release();}
  }
  var s=u.get(BridgeConnectionState.class);
  BridgeTest.check(s.chatSessionsReceived==1&&s.signedChatsReceived==1&&s.unsignedChatsReceived==1,"Incorrect diagnostic counts");
  System.out.println("CHAT_DIAGNOSTIC_PASS payloads_unchanged=true sessions=1 signed=1 unsigned=1");
 }
}
