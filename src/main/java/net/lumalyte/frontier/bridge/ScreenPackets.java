package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static com.viaversion.viaversion.api.protocol.packet.State.PLAY;
import static net.lumalyte.frontier.bridge.SessionPackets.fields;

final class ScreenPackets {
 private static final java.util.Set<String> OLD_ENTITY_TYPES=oldEntityTypes();
 private static java.util.Set<String> oldEntityTypes() {
  var types=new java.util.HashSet<String>();
  for(var row:ItemMappingData.resource("hash-registries.json").getAsJsonObject("old").getAsJsonArray("minecraft:entity_type"))
   types.add(row.getAsJsonObject().get("key").getAsString());
  return java.util.Set.copyOf(types);
 }
 static void sanitizeDeathComponent(com.viaversion.nbt.tag.Tag tag) {
  if(tag instanceof com.viaversion.nbt.tag.CompoundTag compound) {
   var hover=compound.getCompoundTag("hover_event");
   if(hover!=null && "show_entity".equals(hover.getString("action"))) {
    String id=hover.getString("id");
    if(id!=null && !OLD_ENTITY_TYPES.contains(id.contains(":")?id:"minecraft:"+id)) compound.remove("hover_event");
   }
   for(var value:compound.values()) sanitizeDeathComponent(value);
  } else if(tag instanceof com.viaversion.nbt.tag.ListTag<?> list) {
   for(var value:list) sanitizeDeathComponent(value);
  }
 }
 static void register(ProtocolSnapshot8To26_3 p) {
  var tables=ItemMappingData.resource("hash-registries.json");
  var target=new java.util.HashMap<String,Integer>();
  for(var row:ItemMappingData.resource("menu-registry-26.3.json").getAsJsonArray("menus")){var entry=row.getAsJsonObject();target.put(entry.get("key").getAsString(),entry.get("id").getAsInt());}
  var source=tables.getAsJsonObject("snapshot").getAsJsonArray("minecraft:menu");int[] menus=new int[source.size()];
  for(var row:source){var entry=row.getAsJsonObject();String key=entry.get("key").getAsString();if(key.equals("minecraft:jukebox"))key="minecraft:generic_9x3";Integer mapped=target.get(key);if(mapped==null)throw new IllegalStateException("Unmapped menu "+key);menus[entry.get("id").getAsInt()]=mapped;}
  int screen=SessionPackets.type(PLAY,CLIENTBOUND,"OPEN_SCREEN").getId();
  p.registerClientbound(PLAY,screen,screen,w->{w.passthrough(Types.VAR_INT);int menu=w.read(Types.VAR_INT);if(menu<0||menu>=menus.length)throw new IllegalArgumentException("Unknown menu");w.write(Types.VAR_INT,menus[menu]);w.passthrough(Types.TRUSTED_TAG);},false);
  fields(p,PLAY,CLIENTBOUND,"OPEN_BOOK",Types.VAR_INT);
  int death=SessionPackets.type(PLAY,CLIENTBOUND,"PLAYER_COMBAT_KILL").getId();
  p.registerClientbound(PLAY,death,death,w->{w.passthrough(Types.VAR_INT);var message=w.read(Types.TRUSTED_TAG);sanitizeDeathComponent(message);w.write(Types.TRUSTED_TAG,message);},false);
  fields(p,PLAY,CLIENTBOUND,"RESET_SCORE",Types.STRING,Types.OPTIONAL_STRING);
  fields(p,PLAY,CLIENTBOUND,"SET_DISPLAY_OBJECTIVE",Types.VAR_INT,Types.STRING);
  fields(p,PLAY,CLIENTBOUND,"SELECT_ADVANCEMENTS_TAB",Types.OPTIONAL_STRING);
  fields(p,PLAY,SERVERBOUND,"BLOCK_ENTITY_TAG_QUERY",Types.VAR_INT,Types.LONG);
  fields(p,PLAY,SERVERBOUND,"ENTITY_TAG_QUERY",Types.VAR_INT,Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"LOCK_DIFFICULTY",Types.BOOLEAN);
  fields(p,PLAY,SERVERBOUND,"CHANGE_DIFFICULTY",Types.VAR_INT);
  fields(p,PLAY,SERVERBOUND,"TELEPORT_TO_ENTITY",Types.UUID);
  fields(p,PLAY,SERVERBOUND,"USE_ITEM_ON",Types.VAR_INT,Types.LONG,Types.VAR_INT,Types.FLOAT,Types.FLOAT,Types.FLOAT,Types.BOOLEAN,Types.BOOLEAN,Types.VAR_INT);
  int id=SessionPackets.type(PLAY,SERVERBOUND,"SEEN_ADVANCEMENTS").getId();
  p.registerServerbound(PLAY,id,id,w->{int action=w.passthrough(Types.VAR_INT);if(action==0)w.passthrough(Types.STRING);else if(action!=1)throw new IllegalArgumentException("Unknown advancement action");},false);
 }
}
