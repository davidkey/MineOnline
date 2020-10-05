package gg.codie.mineonline.patches.lwjgl;

import net.bytebuddy.asm.Advice;

public class LWJGLDisplayCreateAdvice {
    @Advice.OnMethodExit
    static void intercept() {
        if(LWJGLDisplayPatch.createListener != null)
            LWJGLDisplayPatch.createListener.onCreateEvent();
    }
}
