package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import java.nio.file.*;
import java.util.*;
public final class RecipePacketTest {
 public static void main(String[] args)throws Exception {
  var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);
  var registries=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject().getAsJsonArray("registries");
  for(var value:registries){var row=value.getAsJsonObject();if(row.get("key").getAsString().equals("minecraft:block_sound_set"))continue;ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.CONFIGURATION,ClientboundConfigurationPackets26_3.REGISTRY_DATA.getId(),HexFormat.of().parseHex(row.get("hex").getAsString()));}
  var output=new JsonArray();
  for(var value:JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonArray()){
   var row=value.getAsJsonObject();var packet=ClientboundPackets26_3.valueOf(row.get("name").getAsString());
   var bytes=ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,packet.getId(),HexFormat.of().parseHex(row.get("hex").getAsString()));
   var result=row.deepCopy();result.addProperty("hex",HexFormat.of().formatHex(bytes));output.add(result);
  }
  BridgeTest.check(u.get(BridgeConnectionState.class).items.cachedItems()==0,"Recipe displays polluted inventory restoration cache");
  Files.writeString(Path.of(args[2]),new Gson().toJson(output));
  var report=Map.of("nativeRecipePackets",output.size(),"inventoryCacheUnchanged",true,"passed",true);
  Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
 }
}
