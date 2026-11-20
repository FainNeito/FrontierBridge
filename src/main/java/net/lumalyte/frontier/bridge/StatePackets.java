package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

final class StatePackets {
 static void register(ProtocolSnapshot8To26_3 p) {
  var tables=ItemMappingData.resource("hash-registries.json");var nativeTables=ItemMappingData.resource("state-registries-26.3.json");
  if(!tables.getAsJsonObject("snapshot").get("minecraft:attribute").equals(nativeTables.get("attributes")))throw new IllegalStateException("Attribute registry changed");
  var effects=new java.util.HashMap<String,Integer>();
  for(var v:nativeTables.getAsJsonArray("effects")){var e=v.getAsJsonObject();effects.put(e.get("key").getAsString(),e.get("id").getAsInt());}
  var source=tables.getAsJsonObject("snapshot").getAsJsonArray("minecraft:mob_effect");int[] effectIds=new int[source.size()];
  for(var v:source){var e=v.getAsJsonObject();effectIds[e.get("id").getAsInt()]=effects.getOrDefault(e.get("key").getAsString(),-1);}
  for(String name:new String[]{"UPDATE_MOB_EFFECT","REMOVE_MOB_EFFECT"}) {
   int id=SessionPackets.type(PLAY,CLIENTBOUND,name).getId();
   p.registerClientbound(PLAY,id,id,w->{w.passthrough(Types.VAR_INT);int effect=w.read(Types.VAR_INT);if(effect<0||effect>=effectIds.length)throw new IllegalArgumentException("Unknown effect");int mapped=effectIds[effect];
    if(mapped>=0)w.write(Types.VAR_INT,mapped);
    if(name.equals("UPDATE_MOB_EFFECT")){w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);w.passthrough(Types.BYTE);}
    if(mapped<0)w.cancel();
   },false);
  }
  int attributes=SessionPackets.type(PLAY,CLIENTBOUND,"UPDATE_ATTRIBUTES").getId();
  p.registerClientbound(PLAY,attributes,attributes,w->{
   w.passthrough(Types.VAR_INT);int count=w.passthrough(Types.VAR_INT);if(count<0||count>128)throw new IllegalArgumentException("Invalid attribute count");
   for(int i=0;i<count;i++){int id=w.passthrough(Types.VAR_INT);if(id<0||id>=40)throw new IllegalArgumentException("Unknown attribute");w.passthrough(Types.DOUBLE);int mods=w.passthrough(Types.VAR_INT);if(mods<0||mods>4096)throw new IllegalArgumentException("Too many modifiers");for(int j=0;j<mods;j++){w.passthrough(Types.STRING);w.passthrough(Types.DOUBLE);w.passthrough(Types.BYTE);}}
  },false);
  fields(p,PLAY,CLIENTBOUND,"CUSTOM_CHAT_COMPLETIONS",Types.VAR_INT,Types.STRING_ARRAY);
  fields(p,PLAY,CLIENTBOUND,"PROJECTILE_POWER",Types.VAR_INT,Types.DOUBLE);
  fields(p,PLAY,SERVERBOUND,"PICK_ITEM_FROM_BLOCK",Types.LONG,Types.BOOLEAN);
  fields(p,PLAY,SERVERBOUND,"PICK_ITEM_FROM_ENTITY",Types.VAR_INT,Types.BOOLEAN);
  fields(p,PLAY,SERVERBOUND,"BUNDLE_ITEM_SELECTED",Types.VAR_INT,Types.VAR_INT);
  int look=SessionPackets.type(PLAY,CLIENTBOUND,"PLAYER_LOOK_AT").getId();
  p.registerClientbound(PLAY,look,look,w->{w.passthrough(Types.VAR_INT);w.passthrough(Types.DOUBLE);w.passthrough(Types.DOUBLE);w.passthrough(Types.DOUBLE);if(w.passthrough(Types.BOOLEAN)){w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);}},false);
  int book=SessionPackets.type(PLAY,SERVERBOUND,"EDIT_BOOK").getId();
  p.registerServerbound(PLAY,book,book,w->{w.passthrough(Types.VAR_INT);int count=w.passthrough(Types.VAR_INT);if(count<0||count>100)throw new IllegalArgumentException("Invalid page count");for(int i=0;i<count;i++){String page=w.passthrough(Types.STRING);if(page.length()>1024)throw new IllegalArgumentException("Book page too long");}String title=w.passthrough(Types.OPTIONAL_STRING);if(title!=null&&title.length()>32)throw new IllegalArgumentException("Book title too long");},false);
 }
}
