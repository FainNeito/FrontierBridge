package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.channel.*;

/** Snapshot login option acknowledgements did not exist on legacy clients.
 * Authentication remains entirely in the native login listener.
 */
final class LegacyLoginTransport extends ChannelOutboundHandlerAdapter {
 private final Object connection;
 private LegacyLoginTransport(Object connection){this.connection=connection;}
 static void install(UserConnection user){
  var channel=user.getChannel();if(channel==null)return;
  var pipeline=channel.pipeline();var nativeConnection=pipeline.get("packet_handler");
  if(nativeConnection==null||!nativeConnection.getClass().getName().equals("net.minecraft.network.Connection"))return;
  // During the handshake the native encoder has not been installed yet.
  // Anchor before the stable connection handler, after future outbound codecs.
  if(pipeline.get("frontier-login")==null)pipeline.addBefore("packet_handler","frontier-login",new LegacyLoginTransport(nativeConnection));
 }
 @Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise)throws Exception {
  String type=message.getClass().getSimpleName();
  if(type.equals("ClientboundApplyOptionsPacket")){
   int options=((Number)message.getClass().getMethod("options").invoke(message)).intValue();
   if(options!=1)throw new IllegalStateException("Unsupported login options: "+options);
   Object listener=connection.getClass().getMethod("getPacketListener").invoke(connection);
   var ack=Class.forName("net.minecraft.network.protocol.login.ServerboundOptionsAppliedPacket");
   listener.getClass().getMethod("handleOptionsEnabled",ack).invoke(listener,ack.getField("INSTANCE").get(null));
   promise.setSuccess();return;
  }
  if(type.equals("ClientboundSetupCompressionPacket")){
   promise=promise.unvoid();
   int threshold=((Number)message.getClass().getMethod("compressionThreshold").invoke(message)).intValue();
   promise.addListener(future->{if(future.isSuccess())try{connection.getClass().getMethod("setupCompression",int.class,boolean.class).invoke(connection,threshold,true);}catch(Exception e){ctx.fireExceptionCaught(e);ctx.close();}});
  }
  ctx.write(message,promise);
 }
}
