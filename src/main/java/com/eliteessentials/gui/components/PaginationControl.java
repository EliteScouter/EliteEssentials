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

    public static void update(UICommandBuilder commandBuilder, String rootSelector, int pageIndex, int totalPages, String labelFormat) {
        commandBuilder.set(rootSelector + " #PageLabel.Text", formatLabel(labelFormat, pageIndex + 1, totalPages));
        commandBuilder.set(rootSelector + " #PrevButton.Disabled", pageIndex <= 0);
        commandBuilder.set(rootSelector + " #NextButton.Disabled", pageIndex >= totalPages - 1);
    }

    public static void setEmpty(UICommandBuilder commandBuilder, String rootSelector, String labelFormat) {
        commandBuilder.set(rootSelector + " #PageLabel.Text", formatLabel(labelFormat, 0, 0));
        commandBuilder.set(rootSelector + " #PrevButton.Disabled", true);
        commandBuilder.set(rootSelector + " #NextButton.Disabled", true);
    }

    public static void updateOrHide(UICommandBuilder commandBuilder, String rootSelector, int pageIndex, int totalPages, String labelFormat) {
        if (totalPages <= 1) {
            commandBuilder.set(rootSelector + ".Visible", false);
            return;
        }
        commandBuilder.set(rootSelector + ".Visible", true);
        update(commandBuilder, rootSelector, pageIndex, totalPages, labelFormat);
    }

    public static void setEmptyAndHide(UICommandBuilder commandBuilder, String rootSelector, String labelFormat) {
        setEmpty(commandBuilder, rootSelector, labelFormat);
        commandBuilder.set(rootSelector + ".Visible", false);
    }

    public static void setButtonLabels(UICommandBuilder commandBuilder, String rootSelector, String prevText, String nextText) {
        commandBuilder.set(rootSelector + " #PrevButton.Text", prevText);
        commandBuilder.set(rootSelector + " #NextButton.Text", nextText);
    }

    private static String formatLabel(String labelFormat, int current, int total) {
        String format = (labelFormat == null || labelFormat.isBlank())
            ? "Page {current} / {total}"
            : labelFormat;
        return format.replace("{current}", String.valueOf(current))
                     .replace("{total}", String.valueOf(total));
    }
}
