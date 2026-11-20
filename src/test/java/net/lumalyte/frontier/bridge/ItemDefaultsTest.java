package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.api.minecraft.data.*;
import java.nio.file.*;
import java.util.*;
public final class ItemDefaultsTest {
 public static void main(String[] args)throws Exception {
  var source=new CodecContext(true);var target=new CodecContext(false);var session=new ItemTranslationSession();var maps=new ItemMappingData();
  var old=new HashMap<Integer,StructuredItem>();
  for(var v:JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonArray()){var i=ItemDefaultsAuditTest.decode(v.getAsJsonObject(),target);old.put(i.identifier(),i);}
  int names=0,heads=0,items=0;
  for(var v:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()) {
   var defaults=ItemDefaultsAuditTest.decode(v.getAsJsonObject(),source);
   var sparse=new StructuredItem(defaults.identifier(),1);var translated=session.toClientDisplay(sparse);
   var name=translated.dataContainer().get(StructuredDataKey.ITEM_NAME);
   var effective=ItemDefaultsAuditTest.values(old.get(translated.identifier()),target);
   var patch=ItemDefaultsAuditTest.values(translated,target);effective.putAll(patch);
   var desired=ItemDefaultsAuditTest.values(session.toClientDisplay(defaults),target);
   if(name!=null)desired.put(9,patch.get(9)); // Translation key checked below; fallback is deliberate.
   BridgeTest.check(effective.equals(desired),"Sparse item patch left incorrect effective defaults: "+defaults.identifier());items++;
   if(name!=null) {
    var actual=(CompoundTag)name;var expected=(CompoundTag)defaults.dataContainer().get(StructuredDataKey.ITEM_NAME);
    BridgeTest.check(actual.getString("translate").equals(expected.getString("translate")),"Wrong native translation key");
    BridgeTest.check(!actual.getString("fallback").isBlank(),"Missing older-client fallback");names++;
    ItemRoundTripTest.roundtrip(session,sparse);
    var custom=new CompoundTag();custom.putString("text","Custom name");
    var changed=sparse.copy();changed.dataContainer().data().put(StructuredDataKey.ITEM_NAME,StructuredData.of(StructuredDataKey.ITEM_NAME,custom,9));
    BridgeTest.check(session.toClientDisplay(changed).dataContainer().get(StructuredDataKey.ITEM_NAME).equals(custom),"Explicit item name overridden");
    changed.dataContainer().data().put(StructuredDataKey.ITEM_NAME,StructuredData.empty(StructuredDataKey.ITEM_NAME,9));
    BridgeTest.check(session.toClientDisplay(changed).dataContainer().data().get(StructuredDataKey.ITEM_NAME).isEmpty(),"Explicit name removal overridden");
    ItemRoundTripTest.roundtrip(session,changed);
   }
   String key=maps.getFullItemMappings().identifier(defaults.identifier()).replace("minecraft:","");
   if(Set.of("skeleton_skull","zombie_head","piglin_head").contains(key)) {
    var headDesired=ItemDefaultsAuditTest.values(session.toClientDisplay(defaults),target);
    var nativeOld=ItemDefaultsAuditTest.values(old.get(translated.identifier()),target);
    BridgeTest.check(headDesired.equals(nativeOld),"Mob-head defaults still differ after entity mapping");heads++;
    BridgeTest.check(Arrays.equals(ItemRoundTripTest.encode(source,defaults),ItemRoundTripTest.encode(source,session.toServer(session.toClientDisplay(defaults)))),"Canonical reverse visibility mapping failed");
   }
  }
  BridgeTest.check(names==10&&heads==3&&items==1667,"Incomplete default coverage");
  var report=Map.of("effectiveItemDefaultsChecked",items,"newItemNames",names,"mobHeadsCorrected",heads,"explicitNamesAndRemovalsPreserved",true,"nativeTranslationKeysMatched",true,"passed",true);
  Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
 }
}
