package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.minecraft.data.*;
import com.viaversion.viaversion.api.minecraft.item.*;
import com.viaversion.viaversion.api.minecraft.item.data.*;

public final class FreezingPotionTranslationTest {
    public static void main(String[] args) {
        for (int id = 0; id < 48; id++) {
            var session = new ItemTranslationSession();
            var item = new StructuredItem(new ItemMappingData().getFullItemMappings().id("potion"), 1);
            var key = StructuredDataKey.POTION_CONTENTS1_21_2;
            item.dataContainer().data().put(key, StructuredData.of(key, new PotionContents(id, null, new PotionEffect[0], null), 53));
            var translated = session.toClient(item);
            var content = translated.dataContainer().get(key);
            BridgeTest.check(content.potion() == (id < 46 ? id : 0), "Unsupported potion id " + id);
            var restored = session.toServer(translated);
            BridgeTest.check(restored.dataContainer().get(key).potion() == id, "Restoration of " + id);
        }
        System.out.println("PASS potion translation and server-side restoration");
    }
}
