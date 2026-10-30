/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;

/** Shared state used by the server GUI mixins without referencing the mixin package. */
public final class ServerGuiState {
    public static final Logger LOGGER = LoggerFactory.getLogger("DedicatedPower");

    /** True when the server was launched with --nogui; the GUI must not be forced. */
    public static volatile boolean noGuiRequested;

    /** The enhanced GUI frame, retained when the user chooses to close only the GUI. */
    private static volatile JFrame frame;
    private static volatile boolean opening;

    private ServerGuiState() {
    }

    public static boolean isNoGuiRequested() {
        return noGuiRequested;
    }

    public static JFrame getFrame() {
        return frame;
    }

    public static synchronized void setFrame(JFrame frame) {
        ServerGuiState.frame = frame;
    }

    public static synchronized boolean beginOpening() {
        if (opening) {
            return false;
        }
        opening = true;
        return true;
    }

    public static synchronized void endOpening() {
        opening = false;
    }

    public static boolean isOpening() {
        return opening;
    }
}
