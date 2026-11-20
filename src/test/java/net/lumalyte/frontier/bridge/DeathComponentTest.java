package net.lumalyte.frontier.bridge;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;

public final class DeathComponentTest {
    private static CompoundTag entity(String id) {
        var result=new CompoundTag(); result.putString("translate","entity."+id.replace(':','.'));
        var hover=new CompoundTag(); hover.putString("action","show_entity"); hover.putString("id",id);
        result.put("hover_event",hover); return result;
    }
    public static void main(String[] args) {
        var message=new CompoundTag(); message.putString("translate","death.attack.mob");
        var player=entity("minecraft:player"); var frostbite=entity("minecraft:frostbite");
        var arguments=new ListTag<CompoundTag>(CompoundTag.class);arguments.add(player);arguments.add(frostbite);message.put("with",arguments);
        ScreenPackets.sanitizeDeathComponent(message);
        BridgeTest.check(player.contains("hover_event"),"Known player tooltip removed");
        BridgeTest.check(!frostbite.contains("hover_event"),"Unsupported entity tooltip remains");
        BridgeTest.check(frostbite.getString("translate").equals("entity.minecraft.frostbite"),"Death message changed");
        System.out.println("DeathComponentTest PASS");
    }
}
