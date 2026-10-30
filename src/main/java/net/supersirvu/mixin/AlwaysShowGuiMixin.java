/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.mixin;

import net.minecraft.server.dedicated.DedicatedServer;
import net.supersirvu.ServerGuiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.GraphicsEnvironment;

@Mixin(DedicatedServer.class)
public final class AlwaysShowGuiMixin {
    @Inject(method = "initServer", at = @At("TAIL"))
    private void dedicatedpower$showGui(CallbackInfoReturnable<Boolean> callback) {
        if (!Boolean.TRUE.equals(callback.getReturnValue())) {
            return;
        }

        // Respect the --nogui launch option and headless environments, exactly like
        // vanilla does in Main.main, so the enhanced GUI is never forced open.
        if (ServerGuiState.isNoGuiRequested() || GraphicsEnvironment.isHeadless()) {
            return;
        }

        if (!ServerGuiState.beginOpening()) {
            return;
        }

        try {
            ((DedicatedServer) (Object) this).showGui();
        } catch (Throwable throwable) {
            ServerGuiState.endOpening();
            ServerGuiState.LOGGER.error("Failed to show the enhanced server GUI", throwable);
        }
    }
}
