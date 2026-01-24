/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.mixin;

import com.mojang.logging.LogQueues;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.dedicated.gui.DedicatedServerGui;
import net.minecraft.util.logging.UncaughtExceptionLogger;
import net.supersirvu.gui.EnhancedLogPanel;
import net.supersirvu.gui.EnhancedPlayerListGui;
import net.supersirvu.gui.EnhancedPlayerStatsGui;
import net.supersirvu.gui.EnhancedServerMenuBar;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.swing.*;
import java.util.function.Function;

public class ServerGuiFixes {
    @Mixin(MinecraftServer.class)
    public static class AlwaysShowGui {
        @Inject(
                method = "startServer",
                at = @At(value = "TAIL")
        )
        private static <S extends MinecraftServer> void alwaysEnableGui(Function<Thread, S> serverFactory, CallbackInfoReturnable<S> cir) {
            if(cir.getReturnValue() instanceof MinecraftDedicatedServer dedicatedServer) {
                dedicatedServer.createGui();
            }
        }
    }

    @Mixin(DedicatedServerGui.class)
    public static class DedicatedServerGuiMixin {
        @Shadow
        @Final
        private MinecraftDedicatedServer server;

        @Shadow
        private Thread consoleUpdateThread;

        @Shadow
        @Final
        private static Logger LOGGER;

        @Inject(method = "createStatsPanel", at = @At("HEAD"), cancellable = true)
        private void replaceStatsPanel(CallbackInfoReturnable<JComponent> cir) {
            JPanel jPanel = new JPanel(new java.awt.BorderLayout());

            // Use our enhanced stats GUI instead of the vanilla one
            EnhancedPlayerStatsGui enhancedStatsGui = new EnhancedPlayerStatsGui(this.server);

            // Add stop task (using reflection to access the private method)
            try {
                java.lang.reflect.Method addStopTaskMethod = DedicatedServerGui.class.getDeclaredMethod("addStopTask", Runnable.class);
                addStopTaskMethod.setAccessible(true);
                addStopTaskMethod.invoke(this, (Runnable) enhancedStatsGui::stop);
            } catch (Exception e) {
                e.printStackTrace();
            }

            jPanel.add(enhancedStatsGui, "North");

            // Use our enhanced player list GUI
            EnhancedPlayerListGui enhancedPlayerList = new EnhancedPlayerListGui(this.server);
            JScrollPane jScrollPane = new JScrollPane(enhancedPlayerList, 22, 30);
            jScrollPane.setBorder(new javax.swing.border.TitledBorder(new javax.swing.border.EtchedBorder(), "Players"));

            jPanel.add(jScrollPane, "Center");
            jPanel.setBorder(new javax.swing.border.TitledBorder(new javax.swing.border.EtchedBorder(), "Stats"));

            // Cancel the original method and return our custom panel
            cir.setReturnValue(jPanel);
        }

        @Inject(method = "create", at = @At("TAIL"))
        private static void onCreate(
                MinecraftDedicatedServer server,
                CallbackInfoReturnable<DedicatedServerGui> cir
        ) {
            DedicatedServerGui gui = cir.getReturnValue();

            // Get the JFrame that contains this GUI
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(gui);
            if (frame == null) return;

            frame.setJMenuBar(new EnhancedServerMenuBar(server, frame));
            SwingUtilities.invokeLater(() -> {
                frame.revalidate();
                frame.repaint();
            });
        }

        @Inject(method = "createLogPanel", at = @At("HEAD"), cancellable = true)
        private void replaceLogPanel(CallbackInfoReturnable<JComponent> cir) {
            // Use our enhanced log panel
            EnhancedLogPanel enhancedLogPanel = new EnhancedLogPanel(this.server);

            JPanel jPanel = new JPanel(new java.awt.BorderLayout());
            jPanel.add(enhancedLogPanel, "Center");
            jPanel.setBorder(new javax.swing.border.TitledBorder(new javax.swing.border.EtchedBorder(), "Log and chat"));

            this.consoleUpdateThread = new Thread(() -> {
                String message;
                while ((message = LogQueues.getNextLogEvent("ServerGuiConsole")) != null) {
                    enhancedLogPanel.processLogMessage(message);
                }
            });
            this.consoleUpdateThread.setUncaughtExceptionHandler(new UncaughtExceptionLogger(LOGGER));
            this.consoleUpdateThread.setDaemon(true);
            this.consoleUpdateThread.start();

            // Cancel the original method and return our custom panel
            cir.setReturnValue(jPanel);
        }

        @Inject(method = "start()V", at = @At("HEAD"), cancellable = true)
        private void replaceLogPanel(CallbackInfo ci) {
            ci.cancel();
        }
    }
}