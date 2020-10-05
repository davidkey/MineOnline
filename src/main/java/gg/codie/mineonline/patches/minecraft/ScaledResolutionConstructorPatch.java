package gg.codie.mineonline.patches.minecraft;

import gg.codie.mineonline.Settings;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;

import java.net.URLClassLoader;

public class ScaledResolutionConstructorPatch {
    public static void useGUIScale(String scaledResolutionClassName, URLClassLoader classLoader) {
        try {
            ScaledResolutionConstructorAdvice.guiScale = Settings.settings.optInt(Settings.GUI_SCALE, 0);

            new ByteBuddy()
                    .redefine(classLoader.loadClass(scaledResolutionClassName))
                    .visit(Advice.to(ScaledResolutionConstructorAdvice.class).on(ElementMatchers.isConstructor()))
                    .make()
                    .load(classLoader, ClassReloadingStrategy.fromInstalledAgent());
        } catch (ClassNotFoundException ex) {
            // If the lib isn't loaded the version must not need it, no need to patch it.
        }
    }
}
