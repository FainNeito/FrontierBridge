package net.lumalyte.frontier.bridge;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.minecraft.data.StructuredData;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import java.util.Map;

/** Snapshot-only vanilla defaults absent from a sparse item patch. Explicit patches win. */
final class ItemDefaultNames {
    private static final Map<String,String> NAMES=Map.of(
        "packed_ice_bricks","Packed Ice Bricks","packed_ice_brick_slab","Packed Ice Brick Slab",
        "packed_ice_brick_stairs","Packed Ice Brick Stairs","packed_ice_brick_wall","Packed Ice Brick Wall",
        "chiseled_packed_ice","Chiseled Packed Ice","brittle_ice","Brittle Ice",
        "ice_ball","Ice Ball","frostbite_spawn_egg","Frostbite Spawn Egg",
        "ice_crystal","Ice Crystal","icicle","Icicle");
    static void apply(Item item,ItemMappingData mappings) {
        var key=StructuredDataKey.ITEM_NAME;
        if(item.dataContainer().data().containsKey(key))return; // Includes explicit component removal.
        String name=mappings.getFullItemMappings().identifier(item.identifier());
        if(name==null)return;
        name=name.replace("minecraft:","");String fallback=NAMES.get(name);if(fallback==null)return;
        var text=new CompoundTag();
        boolean block=!(name.equals("ice_ball")||name.equals("frostbite_spawn_egg"));
        text.putString("translate",(block?"block":"item")+".minecraft."+name);
        text.putString("fallback",fallback);
        int id=mappings.getDataComponentSerializerMappings().id("item_name");
        item.dataContainer().data().put(key,StructuredData.of(key,text,id));
    }
}
