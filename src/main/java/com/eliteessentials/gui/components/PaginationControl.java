package com.eliteessentials.gui.components;

import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;

/**
 * Shared pagination control helpers for custom UI pages.
 */
public final class PaginationControl {
    private static final String ACTION_PREV = "Prev";
    private static final String ACTION_NEXT = "Next";

    private PaginationControl() {
    }

    public static void bind(UIEventBuilder eventBuilder, String rootSelector) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            rootSelector + " #PrevButton",
            EventData.of("PageAction", ACTION_PREV)
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            rootSelector + " #NextButton",
            EventData.of("PageAction", ACTION_NEXT)
        );
    }

    public static void update(UICommandBuilder commandBuilder, String rootSelector, int pageIndex, int totalPages) {
        commandBuilder.set(rootSelector + " #PageLabel.Text", "Page " + (pageIndex + 1) + " / " + totalPages);
        commandBuilder.set(rootSelector + " #PrevButton.Disabled", pageIndex <= 0);
        commandBuilder.set(rootSelector + " #NextButton.Disabled", pageIndex >= totalPages - 1);
    }

    public static void setEmpty(UICommandBuilder commandBuilder, String rootSelector) {
        commandBuilder.set(rootSelector + " #PageLabel.Text", "Page 0 / 0");
        commandBuilder.set(rootSelector + " #PrevButton.Disabled", true);
        commandBuilder.set(rootSelector + " #NextButton.Disabled", true);
    }
}
