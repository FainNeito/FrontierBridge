package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.minecraft.Holder;
import com.viaversion.viaversion.api.minecraft.SoundEvent;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.data.Equippable;
import com.viaversion.viaversion.api.minecraft.item.data.consumable.Consumable1_21_2;
import com.viaversion.viaversion.api.minecraft.item.data.consumable.ConsumeEffect;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import com.viaversion.viaversion.api.minecraft.codec.Ops;
import com.viaversion.viaversion.api.minecraft.item.data.EnumTypes;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.type.types.misc.HolderSetType;
import com.viaversion.viaversion.util.Key;

/** Snapshot 8 made these sound holders optional. Keep absence distinct while decoding. */
public final class SnapshotComponents {
    private SnapshotComponents() { }
    private static Holder<SoundEvent> readSound(ByteBuf b) { return b.readBoolean()?Types.SOUND_EVENT.read(b):null; }
    private static void writeSound(ByteBuf b,Holder<SoundEvent> v) { b.writeBoolean(v!=null);if(v!=null)Types.SOUND_EVENT.write(b,v); }
    public static final StructuredDataKey<Equippable> EQUIPPABLE = new StructuredDataKey<>("equippable",new Type<>(Equippable.class) {
        @Override public void write(Ops ops,Equippable v) {
            ops.writeMap(m->m.write("slot",EnumTypes.EQUIPMENT_SLOT,v.equipmentSlot())
                .writeOptional("equip_sound",Types.SOUND_EVENT,v.soundEvent())
                .writeOptional("asset_id",Types.IDENTIFIER,v.model()==null?null:Key.of(v.model()))
                .writeOptional("camera_overlay",Types.IDENTIFIER,v.cameraOverlay()==null?null:Key.of(v.cameraOverlay()))
                .writeOptional("allowed_entities",new HolderSetType(MappingData.MappingType.ENTITY_TYPE),v.allowedEntities())
                .writeOptional("dispensable",Types.BOOLEAN,v.dispensable(),true)
                .writeOptional("swappable",Types.BOOLEAN,v.swappable(),true)
                .writeOptional("damage_on_hurt",Types.BOOLEAN,v.damageOnHurt(),true)
                .writeOptional("equip_on_interact",Types.BOOLEAN,v.equipOnInteract(),false)
                .writeOptional("can_be_sheared",Types.BOOLEAN,v.canBeSheared(),false)
                .writeOptional("shearing_sound",Types.SOUND_EVENT,v.shearingSound()));
        }
        @Override public Equippable read(ByteBuf b) {
            return new Equippable(Types.VAR_INT.readPrimitive(b),readSound(b),Types.OPTIONAL_STRING.read(b),Types.OPTIONAL_STRING.read(b),
                Types.OPTIONAL_HOLDER_SET.read(b),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),readSound(b));
        }
        @Override public void write(ByteBuf b,Equippable v) {
            Types.VAR_INT.writePrimitive(b,v.equipmentSlot());writeSound(b,v.soundEvent());Types.OPTIONAL_STRING.write(b,v.model());Types.OPTIONAL_STRING.write(b,v.cameraOverlay());
            Types.OPTIONAL_HOLDER_SET.write(b,v.allowedEntities());b.writeBoolean(v.dispensable()).writeBoolean(v.swappable()).writeBoolean(v.damageOnHurt()).writeBoolean(v.equipOnInteract()).writeBoolean(v.canBeSheared());writeSound(b,v.shearingSound());
        }
    });
    public static final StructuredDataKey<Consumable1_21_2> CONSUMABLE = new StructuredDataKey<>("consumable",new Type<>(Consumable1_21_2.class) {
        @Override public void write(Ops ops,Consumable1_21_2 v) {
            ops.writeMap(m->m.writeOptional("consume_seconds",Types.FLOAT,v.consumeSeconds(),1.6F)
                .writeOptional("animation",EnumTypes.ITEM_USE_ANIMATION,v.animationType(),1)
                .writeOptional("sound",Types.SOUND_EVENT,v.sound())
                .writeOptional("has_consume_particles",Types.BOOLEAN,v.hasConsumeParticles(),true)
                .writeOptional("on_consume_effects",ConsumeEffect.ARRAY_TYPE26_3,v.consumeEffects(),new ConsumeEffect<?>[0]));
        }
        @Override public Consumable1_21_2 read(ByteBuf b) {
            return new Consumable1_21_2(b.readFloat(),Types.VAR_INT.readPrimitive(b),readSound(b),b.readBoolean(),ConsumeEffect.ARRAY_TYPE26_3.read(b));
        }
        @Override public void write(ByteBuf b,Consumable1_21_2 v) {
            b.writeFloat(v.consumeSeconds());Types.VAR_INT.writePrimitive(b,v.animationType());writeSound(b,v.sound());b.writeBoolean(v.hasConsumeParticles());ConsumeEffect.ARRAY_TYPE26_3.write(b,v.consumeEffects());
        }
    });
}
