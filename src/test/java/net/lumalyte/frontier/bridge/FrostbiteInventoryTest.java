package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.minecraft.item.StructuredItem;

/** Exercises the snapshot-only egg's translation and issued-item restoration. */
public final class FrostbiteInventoryTest {
    public static void main(String[] args) {
        var mappings = new ItemMappingData();
        var source = mappings.getFullItemMappings().id("frostbite_spawn_egg");
        var expected = mappings.getFullItemMappings().mappedId("drowned_spawn_egg");
        var session = new ItemTranslationSession();
        var original = new StructuredItem(source, 3);
        var client = session.toClient(original);
        BridgeTest.check(client.identifier() == expected, "Unexpected client fallback");
        var name=(com.viaversion.nbt.tag.CompoundTag)client.dataContainer().get(com.viaversion.viaversion.api.minecraft.data.StructuredDataKey.ITEM_NAME);
        var reordered=new com.viaversion.nbt.tag.CompoundTag();
        reordered.put("fallback",name.get("fallback").copy());
        reordered.put("translate",name.get("translate").copy());
        client.dataContainer().set(com.viaversion.viaversion.api.minecraft.data.StructuredDataKey.ITEM_NAME,reordered);
        var tampered=client.copy();
        var changed=(com.viaversion.nbt.tag.CompoundTag)tampered.dataContainer().get(com.viaversion.viaversion.api.minecraft.data.StructuredDataKey.ITEM_NAME).copy();
        changed.putString("fallback","Tampered");
        tampered.dataContainer().set(com.viaversion.viaversion.api.minecraft.data.StructuredDataKey.ITEM_NAME,changed);
        ItemRoundTripTest.rejected(()->session.toServer(tampered));
        for (int n = 0; n < 100; n++) {
            var restored = session.toServer(client.copy());
            BridgeTest.check(restored.identifier() == source && restored.amount() == 3,
                "Creative echo lost original item at iteration " + n);
            client = session.toClient(restored);
        }
        System.out.println("PASS frostbite egg repeated issued-item restoration");
    }
}
