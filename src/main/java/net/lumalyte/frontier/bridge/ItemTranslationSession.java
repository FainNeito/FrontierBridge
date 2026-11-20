package net.lumalyte.frontier.bridge;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.*;
import com.viaversion.viaversion.api.minecraft.data.*;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.data.Equippable;
import com.viaversion.viaversion.api.minecraft.item.data.MobVisibility;
import com.viaversion.viaversion.api.minecraft.item.data.PotionContents;
import com.viaversion.viaversion.api.minecraft.item.data.consumable.Consumable1_21_2;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypesHolder;
import com.viaversion.viaversion.rewriter.ItemRewriter;
import com.viaversion.viaversion.util.Rewritable;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.util.*;

/** Per-connection full-stack translation/provenance. Inventory slot authority remains server-side.
 * Hashed click packets require a separate adapter; these methods do not authorize transactions.
 */
public final class ItemTranslationSession {
    private static final String TOKEN="frontierbridge:issued";
    private final CodecContext source=new CodecContext(true), target=new CodecContext(false);
    private final ItemMappingData mappings=new ItemMappingData();
    private final ValueContext context=new ValueContext();
    private final LinkedHashMap<String,Issued> issued=new LinkedHashMap<>(16,0.75f,true);
    private final Map<String,String> identities=new HashMap<>();
    private int cachedBytes;
    private final int capacity;
    private int depth;
    private record Issued(Item original, byte[] expected, String identity, int bytes) { }
    public ItemTranslationSession(){this(4096);}
    public ItemTranslationSession(int capacity){if(capacity<1||capacity>4096)throw new IllegalArgumentException("Invalid item cache bound");this.capacity=capacity;}
    public int cachedItems(){return issued.size();}
    /** Recipe previews are not inventory stacks and must not consume restoration-cache entries. */
    public Item toClientDisplay(Item original) {
        if(Item.isEmpty(original))return original;
        Item translated=convert(original.copy(),true);translated.dataContainer().setIdLookup(context,true);return translated;
    }
    public void clear(){issued.clear();identities.clear();cachedBytes=0;}

    public Item toClient(Item original) {
        if(Item.isEmpty(original))return original;
        Item translated=convert(original.copy(),true);
        var data=translated.dataContainer();data.setIdLookup(context,true);
        var custom=data.get(StructuredDataKey.CUSTOM_DATA);
        if(custom==null)custom=new CompoundTag();else custom=custom.copy();
        byte[] originalBytes=payload(original,source);
        String identity=HexFormat.of().formatHex(digest(originalBytes));
        String token=identities.get(identity);
        if(token==null)token=UUID.randomUUID().toString();
        custom.putString(TOKEN,token);data.set(StructuredDataKey.CUSTOM_DATA,custom);
        if(issued.get(token)==null) {
            int bytes=originalBytes.length+payload(translated,target).length;
            issued.put(token,new Issued(original.copy(),fingerprint(translated),identity,bytes));
            identities.put(identity,token);cachedBytes+=bytes;
        }
        while(issued.size()>capacity||cachedBytes>32*1024*1024) {
            var entry=issued.entrySet().iterator().next();var value=entry.getValue();
            identities.remove(value.identity());cachedBytes-=value.bytes();issued.remove(entry.getKey());
        }
        return translated;
    }
    public Item toServer(Item clientItem) {
        if(Item.isEmpty(clientItem))return clientItem;
        var custom=clientItem.dataContainer().get(StructuredDataKey.CUSTOM_DATA);
        if(custom!=null&&custom.contains(TOKEN)) {
            var saved=issued.get(custom.getString(TOKEN));
            if(saved==null)throw new IllegalArgumentException("Unknown or expired item reference; inventory resync required");
            // Splitting and merging stacks is legal. Transaction/slot validation belongs to the server.
            if(clientItem.amount()<1||clientItem.amount()>99||!MessageDigest.isEqual(saved.expected(),fingerprint(clientItem)))
                throw new IllegalArgumentException("Issued item contents or amount changed");
            Item restored=saved.original().copy();restored.setAmount(clientItem.amount());return restored;
        }
        // Genuine old-version items use canonical IDs, not a fallback-table inverse.
        return convert(clientItem.copy(),false);
    }

    private Item convert(Item item,boolean clientbound) {
        if(Item.isEmpty(item))return item;
        if(++depth>32){depth--;throw new IllegalArgumentException("Nested item depth exceeded");}
        try {
            if(clientbound) ItemDefaultNames.apply(item,mappings);
            int id=clientbound?mappings.getFullItemMappings().getNewId(item.identifier()):mappings.getFullItemMappings().inverse().getNewId(item.identifier());
            if(id<0)throw new IllegalArgumentException("Unknown item ID");item.setIdentifier(id);
            var container=item.dataContainer();var replacement=new HashMap<StructuredDataKey<?>,StructuredData<?>>();
            var destination=clientbound?target:source;
            for(var data:container.data().values()) {
                var key=destination.types.structuredData.key(data.id());
                if(key==null)throw new IllegalArgumentException("Unsupported item component "+data.id());
                if(data.isEmpty()){replacement.put(key,StructuredData.empty(key,data.id()));continue;}
                Object value=data.value();
                if(clientbound && value instanceof PotionContents v && v.potion() != null && v.potion() >= 46) {
                    // 26.3 has no freezing potion registry entries. Display water while
                    // the issued-item cache retains the authoritative original stack.
                    value=new PotionContents(0,v.customColor(),v.customEffects(),v.customName());
                } else if(value instanceof Equippable v) {
                    value=new Equippable(v.equipmentSlot(),sound(v.soundEvent(),clientbound),v.model(),v.cameraOverlay(),v.allowedEntities(),v.dispensable(),v.swappable(),v.damageOnHurt(),v.equipOnInteract(),v.canBeSheared(),sound(v.shearingSound(),clientbound));
                } else if(value instanceof Consumable1_21_2 v) {
                    value=new Consumable1_21_2(v.consumeSeconds(),v.animationType(),sound(v.sound(),clientbound),v.hasConsumeParticles(),v.consumeEffects());
                } else if(value instanceof MobVisibility v) {
                    var entities=v.targetingEntityTypes();
                    if(!entities.hasTagKey()) {
                        var table=clientbound?mappings.getEntityMappings():mappings.getEntityMappings().inverse();
                        int[] ids=entities.ids().clone();
                        for(int i=0;i<ids.length;i++){ids[i]=table.getNewId(ids[i]);if(ids[i]<0)throw new IllegalArgumentException("Unknown visibility entity");}
                        entities=HolderSet.of(ids);
                    }
                    value=new MobVisibility(entities,v.visibility());
                }
                value=rewrite(value,clientbound);
                replacement.put(key,filled(key,value,data.id()));
            }
            container.data().clear();container.data().putAll(replacement);container.setIdLookup(context,clientbound);
            return item;
        }finally{depth--;}
    }
    private Holder<SoundEvent> sound(Holder<SoundEvent> value,boolean clientbound) {
        if(value==null||(!clientbound&&value.hasId()&&mappings.getFullSoundMappings().mappedId("intentionally_empty")==value.id()))
            return Holder.of(new SoundEvent("minecraft:intentionally_empty",null));
        return value;
    }
    private Object rewrite(Object value,boolean clientbound) {
        if(value instanceof Item nested)return convert(nested,clientbound);
        if(value instanceof Item[] items){for(int i=0;i<items.length;i++)items[i]=convert(items[i],clientbound);return items;}
        if(value instanceof Rewritable r)return r.rewrite(null,context,clientbound);
        if(value instanceof Holder<?> h && h.isDirect() && h.value() instanceof Rewritable r)return Holder.of(r.rewrite(null,context,clientbound));
        if(value instanceof EitherHolder<?> h && h.hasHolder() && h.holder().isDirect() && h.holder().value() instanceof Rewritable r)return EitherHolder.of(Holder.of(r.rewrite(null,context,clientbound)));
        return value;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static StructuredData<?> filled(StructuredDataKey key,Object value,int id){return StructuredData.of(key,value,id);}
    private byte[] fingerprint(Item item) {
        return digest(payload(item,target));
    }
    private static byte[] digest(byte[] bytes) {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private byte[] payload(Item item,CodecContext codec) {
        var buffer=Unpooled.buffer();
        try {
            Types.VAR_INT.writePrimitive(buffer,item.identifier());
            for(var data:item.dataContainer().data().values().stream().sorted(Comparator.comparingInt(StructuredData::id)).toList()) {
                buffer.writeBoolean(data.isPresent());
                if(data.isPresent()) {
                    var canonical=data.value() instanceof com.viaversion.nbt.tag.Tag tag
                        ? filled(data.key(),canonicalTag(tag),data.id()) : data;
                    codec.types.structuredData.write(buffer,canonical);
                } else Types.VAR_INT.writePrimitive(buffer,data.id());
            }
            byte[] bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);
            if(bytes.length>1048576)throw new IllegalArgumentException("Item data exceeds bridge bound");
            return bytes;
        }finally{buffer.release();}
    }
    private static com.viaversion.nbt.tag.Tag canonicalTag(com.viaversion.nbt.tag.Tag tag) {
        if(tag instanceof CompoundTag compound) {
            var sorted=new CompoundTag();
            compound.keySet().stream().sorted().forEach(key->sorted.put(key,canonicalTag(compound.get(key))));
            return sorted;
        }
        if(tag instanceof com.viaversion.nbt.tag.ListTag<?> list) {
            @SuppressWarnings("rawtypes") var copy=(com.viaversion.nbt.tag.ListTag)list.copy();
            for(int i=0;i<copy.size();i++) copy.set(i,canonicalTag((com.viaversion.nbt.tag.Tag)copy.get(i)));
            return copy;
        }
        return tag.copy();
    }
    private final class ValueContext extends AbstractProtocol<ClientboundPacketType,ClientboundPacketType,ServerboundPacketType,ServerboundPacketType> {
        private final ItemRewriter<ClientboundPacketType,ServerboundPacketType,ValueContext> rewriter=new ItemRewriter<>(this) {
            @Override public Item handleItemToClient(UserConnection user,Item value){return convert(value,true);}
            @Override public Item handleItemToServer(UserConnection user,Item value){return convert(value,false);}
        };
        ValueContext(){super(null,null,null,null);}
        @Override public ItemMappingData getMappingData(){return mappings;}
        @Override public VersionedTypesHolder types(){return source.types;}
        @Override public VersionedTypesHolder mappedTypes(){return target.types;}
        @Override public ItemRewriter<ClientboundPacketType,ServerboundPacketType,ValueContext> getItemRewriter(){return rewriter;}
    }
}
