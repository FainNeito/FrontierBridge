package net.lumalyte.frontier.bridge;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import io.netty.buffer.*;

/** Paper item sanitization needs the real server singleton, so exercise those native codecs here. */
final class NativeLiveSessionProbe {
 static int run(Path file)throws Exception {
  var serverType=Class.forName("net.minecraft.server.MinecraftServer");var server=serverType.getMethod("getServer").invoke(null);var access=serverType.getMethod("registryAccess").invoke(server);
  var ctor=Class.forName("net.minecraft.network.RegistryFriendlyByteBuf").getConstructor(ByteBuf.class,Class.forName("net.minecraft.core.RegistryAccess"));
  var stream=Class.forName("net.minecraft.network.codec.StreamCodec");var decode=stream.getMethod("decode",Object.class);var encode=stream.getMethod("encode",Object.class,Object.class);int count=0;
  for(var value:JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
   var row=value.getAsJsonObject();if(!row.has("liveSnapshotCodec"))continue;
   byte[] bytes=HexFormat.of().parseHex(row.get("hex").getAsString());var in=Unpooled.wrappedBuffer(bytes);var out=Unpooled.buffer();
   try{var codec=Class.forName(row.get("nativeClass").getAsString()).getField("STREAM_CODEC").get(null);Object packet=decode.invoke(codec,ctor.newInstance(in,access));if(in.isReadable())throw new IllegalStateException("Trailing live native input");encode.invoke(codec,ctor.newInstance(out,access),packet);if(!Arrays.equals(bytes,ByteBufUtil.getBytes(out)))throw new IllegalStateException("Live native payload changed: "+row.get("name"));count++;}finally{in.release();out.release();}
  }
  Files.writeString(file.resolveSibling("native-live-session.json"),new Gson().toJson(Map.of("fixtures",count,"passed",true)));return count;
 }
}
