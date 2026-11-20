package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ServerboundPackets26_3;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import io.netty.buffer.Unpooled;
public final class CreativeResyncTest {
 public static void main(String[] args)throws Exception {
  var debug=(com.viaversion.viaversion.api.debug.DebugHandler)java.lang.reflect.Proxy.newProxyInstance(CreativeResyncTest.class.getClassLoader(),new Class[]{com.viaversion.viaversion.api.debug.DebugHandler.class},(o,m,a)->false);
  var manager=(com.viaversion.viaversion.api.ViaManager)java.lang.reflect.Proxy.newProxyInstance(CreativeResyncTest.class.getClassLoader(),new Class[]{com.viaversion.viaversion.api.ViaManager.class},(o,m,a)-> {if(m.getName().equals("debugHandler"))return debug;throw new UnsupportedOperationException(m.getName());});
  com.viaversion.viaversion.api.Via.init(manager);
  int[] resync={0};var p=new ProtocolSnapshot8To26_3(new BridgeMappings(),u->resync[0]++);p.initialize();var u=ConnectionPipelineTest.user();p.init(u);
  var items=u.get(BridgeConnectionState.class).items;
  var original=new StructuredItem(new ItemMappingData().getFullItemMappings().id("ice_ball"),3);
  for(boolean expired:new boolean[]{false,true}) {
   var client=items.toClient(original);
   if(expired)items.clear();else client.setIdentifier(client.identifier()+1);
   var b=Unpooled.buffer();try {
    b.writeShort(7);new CodecContext(false).types.lengthPrefixedItem.write(b,client);
    var w=new PacketWrapperImpl(ServerboundPackets26_3.SET_CREATIVE_MODE_SLOT.getId(),b,u);
    try {p.transform(Direction.SERVERBOUND,State.PLAY,w);}catch(com.viaversion.viaversion.exception.CancelException cancelled){}
    BridgeTest.check(w.isCancelled(),"Invalid creative update must be cancelled");
   }finally{b.release();}
  }
  BridgeTest.check(resync[0]==2,"Each rejected update requests inventory resync");
  var valid=items.toClient(original);valid.setAmount(8);
  BridgeTest.check(items.toServer(valid).amount()==8,"Valid creative stack resize rejected");
  System.out.println("CREATIVE_RESYNC_PASS modified=true expired=true valid_resize=true");
 }
}
