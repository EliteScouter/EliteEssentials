package com.eliteessentials.gui;

public final class DeleteConfirmState {
    private final String defaultLabel;
    private final String confirmLabel;
    private final long debounceMs;
    private String pendingKey;
    private long confirmAtMillis;

    public DeleteConfirmState(String defaultLabel, String confirmLabel, long debounceMs) {
        this.defaultLabel = defaultLabel;
        this.confirmLabel = confirmLabel;
        this.debounceMs = debounceMs;
    }

    public boolean request(String key) {
        if (pendingKey == null || !pendingKey.equals(key)) {
            pendingKey = key;
            confirmAtMillis = System.currentTimeMillis();
            return false;
        }
        return true;
    }

    public boolean cancel(String key) {
        if (!isConfirming(key)) {
            return false;
        }
        if (System.currentTimeMillis() - confirmAtMillis < debounceMs) {
            return false;
        }
        pendingKey = null;
        return true;
    }

    public void clear() {
        pendingKey = null;
    }

    public String getLabel(String key) {
        return isConfirming(key) ? confirmLabel : defaultLabel;
    }

    private boolean isConfirming(String key) {
        return pendingKey != null && pendingKey.equals(key);
    }
}
