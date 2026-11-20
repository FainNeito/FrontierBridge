package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.api.minecraft.data.StructuredData;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
/** Compares effective native defaults; does not change runtime item patches. */
public final class ItemDefaultsAuditTest {
 static StructuredItem decode(JsonObject row,CodecContext codec)throws Exception {
  var item=new StructuredItem(row.get("id").getAsInt(),1);
  for(var hex:row.getAsJsonArray("components")) {
   var b=Unpooled.wrappedBuffer(HexFormat.of().parseHex(hex.getAsString()));
   try{var data=codec.types.structuredData.read(b);BridgeTest.check(!b.isReadable(),"Trailing default component bytes");item.dataContainer().data().put(data.key(),data);}finally{b.release();}
  }
  return item;
 }
 static Map<Integer,String> values(com.viaversion.viaversion.api.minecraft.item.Item item,CodecContext codec)throws Exception {
  var values=new TreeMap<Integer,String>();
  for(var data:item.dataContainer().data().values()) {
   var b=Unpooled.buffer();try{codec.types.structuredData.write(b,data);byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);values.put(data.id(),HexFormat.of().formatHex(bytes));}finally{b.release();}
  }
  return values;
 }
 public static void main(String[] args)throws Exception {
  var source=new CodecContext(true);var target=new CodecContext(false);var session=new ItemTranslationSession();var mappings=new ItemMappingData();
  var old=new HashMap<Integer,Map<Integer,String>>();
  for(var value:JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonArray()){var item=decode(value.getAsJsonObject(),target);old.put(item.identifier(),values(item,target));}
  var differences=new ArrayList<Object>();int checked=0;
  for(var value:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()) {
   var item=decode(value.getAsJsonObject(),source);var translated=session.toClientDisplay(item);var nativeOld=old.get(translated.identifier());
   BridgeTest.check(nativeOld!=null,"Missing mapped item's native defaults");var desired=values(translated,target);var changed=new ArrayList<Integer>();var removed=new ArrayList<Integer>();
   for(var entry:desired.entrySet())if(!entry.getValue().equals(nativeOld.get(entry.getKey())))changed.add(entry.getKey());
   for(int key:nativeOld.keySet())if(!desired.containsKey(key))removed.add(key);
   if(!changed.isEmpty()||!removed.isEmpty())differences.add(Map.of("sourceId",item.identifier(),"targetId",translated.identifier(),"changedOrAddedComponents",changed,"targetOnlyComponents",removed));
   checked++;
  }
  BridgeTest.check(checked==1667&&session.cachedItems()==0,"Incomplete default audit or inventory cache polluted");
  var report=Map.of("itemsCompared",checked,"itemsWithDifferentDefaults",differences.size(),"differences",differences,"runtimeExpansionImplemented",false,"scope","Captured vanilla registry context; dynamic registry defaults need connection-aware handling","passed",true);
  Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("Default audit: "+checked+" items; "+differences.size()+" differ from mapped 26.3 defaults");
 }
}
