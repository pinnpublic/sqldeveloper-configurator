package io.github.sqldevgrid;

import javax.swing.SwingUtilities;

public final class ResultGridFontAddin implements oracle.ide.Addin {
    private static ResultGridWatcher watcher;
    public void initialize() {
        BootstrapAgent.start();
    }
}
