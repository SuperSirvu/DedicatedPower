/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.mixin;

import net.minecraft.server.Main;
import net.supersirvu.ServerGuiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public final class CaptureNoGuiMixin {
    @Inject(method = "main", at = @At("HEAD"))
    private static void dedicatedpower$captureNoGui(String[] args, CallbackInfo callback) {
        if (args == null) {
            return;
        }

        for (String arg : args) {
            // Accepts "nogui", "--nogui" and "-nogui", matching joptsimple usage.
            if (arg != null && arg.replace("-", "").equalsIgnoreCase("nogui")) {
                ServerGuiState.noGuiRequested = true;
                return;
            }
        }
    }
}
