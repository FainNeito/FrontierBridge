package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.minecraft.data.*;
import com.viaversion.viaversion.api.minecraft.item.*;
import com.viaversion.viaversion.api.minecraft.item.data.*;
import com.viaversion.viaversion.codec.hash.*;
import com.viaversion.viaversion.data.item.ItemHasherBase;

public final class FreezingPotionHashTest {
    public static void main(String[] args) {
        var registries=ItemMappingData.resource("hash-registries.json");
        var source=new RegistryHashContext(registries.getAsJsonObject("snapshot"),new CodecContext(true),false);
        var target=new RegistryHashContext(registries.getAsJsonObject("old"),new CodecContext(false),true);
        var translator=new InventoryHashTranslator(source,target);
        for(int id=0;id<48;id++) {
            var item=new StructuredItem(1,1);
            var key=StructuredDataKey.POTION_CONTENTS1_21_2;
            item.dataContainer().data().put(key,StructuredData.of(key,new PotionContents(id,null,new PotionEffect[0],null),42));
            var actual=translator.hash(item,false);
            if(id<46) {
                var expected=ItemHasherBase.toHashedItem(new HashOps(source,HashFunction.crc32c()),item);
                BridgeTest.check(expected.equals(actual),"Regression for potion "+id);
            }
            System.out.println("BRIDGE_POTION_HASH "+id+" "+actual.dataHashesById().get(42));
            translator.track(item,item);
            BridgeTest.check(translator.restore(translator.hash(item,true)).equals(actual),"Hash round trip for "+id);
        }
        System.out.println("PASS potion hashes 0..47 and inventory restore");
    }
}
