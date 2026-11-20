package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import com.viaversion.viaversion.api.type.types.chunk.PaletteType1_21_5;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.HexFormat;
public final class SnapshotBiomeTest {
 public static void main(String[] args)throws Exception{
  int count=0;
  for(var entry:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()){
   var row=entry.getAsJsonObject();var input=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.get("hex").getAsString()));var output=Unpooled.buffer();
   try{var palette=SnapshotChunkType.readBiomes(input,row.get("registryCount").getAsInt());BridgeTest.check(!input.isReadable(),"Native biome bytes left unread");
    var codec=new PaletteType1_21_5(PaletteType.BIOMES,9);codec.write(output,palette);var decoded=codec.read(output);BridgeTest.check(!output.isReadable(),"Legacy biome bytes left unread");
    for(int i=0;i<64;i++)BridgeTest.check(decoded.idAt(i)==row.getAsJsonArray("expected").get(i).getAsInt(),"Wrong native biome sample");count++;
   }finally{input.release();output.release();}
  }
  String result="{\"passed\":true,\"nativePaletteCases\":"+count+",\"sampledBiomeCells\":"+(count*64)+"}";Files.writeString(Path.of(args[1]),result);System.out.println(result);
 }
}
