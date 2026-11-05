package lunar.dread.compat;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Never resolve an optional integration's classes when its mod is absent. */
public final class OptionalMixins implements IMixinConfigPlugin {
    private boolean installed(String id) { return LoadingModList.get().getModFileById(id)!=null; }
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        if(mixin.endsWith("StellarMoonMixin"))return installed("stellarview")&&installed("enhancedcelestials");
        if(mixin.endsWith("MoonDirectionMixin"))return installed("stellarview");
        if(mixin.endsWith("HordeConversionGuardMixin"))return installed("hordes");
        return true;
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> own,Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
