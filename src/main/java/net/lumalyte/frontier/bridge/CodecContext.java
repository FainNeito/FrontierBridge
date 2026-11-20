package net.lumalyte.frontier.bridge;

import com.google.gson.JsonParser;
import com.viaversion.viaversion.api.data.*;
import com.viaversion.viaversion.api.minecraft.data.version.StructuredDataKeys26_3;
import com.viaversion.viaversion.api.minecraft.entitydata.types.EntityDataTypes26_3;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.types.version.Types26_1;
import com.viaversion.viaversion.api.type.types.misc.ParticleType;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Independent codec instances: never mutates Via's shared 26.3 types. */
public final class CodecContext extends AbstractProtocol<ClientboundPacketType,ClientboundPacketType,ServerboundPacketType,ServerboundPacketType> {
    public final Types26_1<StructuredDataKeys26_3,EntityDataTypes26_3> types = new Types26_1<>(StructuredDataKeys26_3::new,EntityDataTypes26_3::new);
    private final CodecMappings mappings;
    public final boolean snapshot;
    private final com.viaversion.viaversion.rewriter.ItemRewriter<ClientboundPacketType,ServerboundPacketType,CodecContext> itemRewriter = new com.viaversion.viaversion.rewriter.ItemRewriter<>(this);
    public CodecContext(boolean snapshot) {
        super(null,null,null,null);
        this.snapshot=snapshot;
        mappings=new CodecMappings(snapshot);
        ComponentKeys.initialize(this);
        ParticleType.Fillers.fill26_2(this);
    }
    @Override public Types26_1<StructuredDataKeys26_3,EntityDataTypes26_3> mappedTypes() { return types; }
    @Override public MappingData getMappingData() { return mappings; }
    @Override public com.viaversion.viaversion.rewriter.ItemRewriter<ClientboundPacketType,ServerboundPacketType,CodecContext> getItemRewriter() { return itemRewriter; }
    @Override public Types26_1<StructuredDataKeys26_3,EntityDataTypes26_3> types() { return types; }

    private static final class CodecMappings extends MappingDataBase {
        CodecMappings(boolean snapshot) {
            super("codec","codec");
            try(var stream=CodecContext.class.getResourceAsStream("/codec-registries.json"); var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var root=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject(snapshot?"snapshot":"old");
                var components=names(root.getAsJsonArray("components"));
                var particles=names(root.getAsJsonArray("particles"));
                itemDataSerializerMappings=FullMappingsBase.of(new MappingDataLoader.IdentifiersPair(components,components,true),new IdentityMappings(components.size(),components.size()));
                particleMappings=new ParticleMappings(new MappingDataLoader.IdentifiersPair(particles,particles,true),new IdentityMappings(particles.size(),particles.size()));
            } catch(java.io.IOException e) { throw new IllegalStateException(e); }
        }
        private static java.util.List<String> names(com.google.gson.JsonArray rows) {
            var names=new ArrayList<String>();
            for(var row:rows) names.add(row.getAsJsonObject().get("key").getAsString().replace("minecraft:",""));
            return names;
        }
    }
}
