package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.minecraft.item.*;
import com.viaversion.viaversion.api.minecraft.data.StructuredData;
import com.viaversion.viaversion.api.minecraft.item.data.PotionContents;
import com.viaversion.viaversion.api.minecraft.item.data.PotionEffect;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.codec.hash.*;
import com.viaversion.viaversion.data.item.ItemHasherBase;
import io.netty.buffer.ByteBuf;
import java.util.*;

/** Restores client prediction hashes; the server still validates every inventory transaction. */
public final class InventoryHashTranslator {
    private final HashOps source,target;
    private final int capacity;
    private final LinkedHashMap<HashedItem,HashedItem> issued=new LinkedHashMap<>(16,0.75f,true);
    public InventoryHashTranslator(RegistryHashContext source,RegistryHashContext target){this(source,target,4096);}
    public InventoryHashTranslator(RegistryHashContext source,RegistryHashContext target,int capacity) {
        if(capacity<1||capacity>4096)throw new IllegalArgumentException("Invalid hash cache bound");this.capacity=capacity;
        this.source=new HashOps(source,HashFunction.crc32c());this.target=new HashOps(target,HashFunction.crc32c());
    }
    public HashedItem hash(Item item,boolean mapped) {
        if(Item.isEmpty(item))return HashedStructuredItem.empty();
        var ops=mapped?target:source;
        var result=new HashedStructuredItem(item.identifier(),item.amount());
        for (StructuredData<?> data:item.dataContainer().data().values()) {
            if(data.isEmpty()) { result.removedDataIds().add(data.id()); continue; }
            ops.reset();
            if(data.value() instanceof PotionContents potion) {
                // Via's built-in enum stops at 45; 26.3 and snapshot 8 add freezing potions.
                var key=potion.potion()==null?null:ops.context().registryAccess().registryKey("potion",potion.potion());
                ops.writeMap(map -> map.writeOptional("potion",Types.IDENTIFIER,key)
                    .writeOptional("custom_color",Types.INT,potion.customColor())
                    .writeOptional("custom_effects",PotionEffect.ARRAY_TYPE,potion.customEffects(),new PotionEffect[0])
                    .writeOptional("custom_name",Types.STRING,potion.customName()));
            } else { writeComponent(ops,data); }
            result.dataHashesById().put(data.id(),ops.hash());
        }
        return result;
    }
    private static <T> void writeComponent(HashOps ops,StructuredData<T> data) {
        ops.write(data.key().type(),data.value());
    }
    private static HashedItem identity(HashedItem item){var copy=item.copy();copy.setAmount(1);return copy;}
    public void track(Item original,Item translated) {
        if(Item.isEmpty(original))return;
        HashedItem key=identity(hash(translated,true)),value=identity(hash(original,false));
        if(issued.containsKey(key)&&!Objects.equals(issued.get(key),value))issued.put(key,null); // ambiguous CRC collision: resync, never guess
        else if(!issued.containsKey(key))issued.put(key,value);
        while(issued.size()>capacity)issued.remove(issued.keySet().iterator().next());
    }
    public void clear(){issued.clear();}
    public HashedItem restore(HashedItem client) {
        if(client.identifier()==0&&client.amount()==0&&client.dataHashesById().isEmpty()&&client.removedDataIds().isEmpty())return HashedStructuredItem.empty();
        if(client.amount()<1||client.amount()>99)throw new IllegalArgumentException("Invalid predicted stack size");
        var original=issued.get(identity(client));if(original==null)throw new MissingReference();
        var restored=original.copy();restored.setAmount(client.amount());return restored;
    }
    public static final class MissingReference extends IllegalArgumentException {MissingReference(){super("Unknown/expired/ambiguous item prediction; inventory resync required");}}
    /** False cancels the whole click and requires an authoritative inventory resend. */
    public boolean rewriteClick(ByteBuf input,ByteBuf output) {
        int container=Types.VAR_INT.readPrimitive(input),state=Types.VAR_INT.readPrimitive(input);
        short slot=input.readShort();byte button=input.readByte();int mode=Types.VAR_INT.readPrimitive(input),count=Types.VAR_INT.readPrimitive(input);
        if(count<0||count>128)throw new IllegalArgumentException("Invalid changed-slot count");
        short[] slots=new short[count];HashedItem[] items=new HashedItem[count];var seen=new HashSet<Short>();
        for(int i=0;i<count;i++){slots[i]=input.readShort();if(!seen.add(slots[i]))throw new IllegalArgumentException("Duplicate changed slot");items[i]=Types.HASHED_ITEM.read(input);}
        var carried=Types.HASHED_ITEM.read(input);if(input.isReadable())throw new IllegalArgumentException("Trailing inventory click bytes");
        try{for(int i=0;i<count;i++)items[i]=restore(items[i]);carried=restore(carried);}catch(MissingReference missing){return false;}
        Types.VAR_INT.writePrimitive(output,container);Types.VAR_INT.writePrimitive(output,state);output.writeShort(slot).writeByte(button);Types.VAR_INT.writePrimitive(output,mode);Types.VAR_INT.writePrimitive(output,count);
        for(int i=0;i<count;i++){output.writeShort(slots[i]);Types.HASHED_ITEM.write(output,items[i]);}Types.HASHED_ITEM.write(output,carried);return true;
    }
}
