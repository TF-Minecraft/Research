package net.tfminecraft.research.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Event;

/**
 * Fired when a player completes a research project and receives a result item.
 * Codex or other plugins may listen to unlock entries from {@link #getResultItemRef()}.
 */
public final class ResearchCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String inputId;
    private final String outputId;
    private final String resultItemRef;

    public ResearchCompleteEvent(Player player, String inputId, String outputId, String resultItemRef) {
        this.player = player;
        this.inputId = inputId;
        this.outputId = outputId;
        this.resultItemRef = resultItemRef;
    }

    public Player getPlayer() {
        return player;
    }

    public String getInputId() {
        return inputId;
    }

    public String getOutputId() {
        return outputId;
    }

    /**
     * @deprecated Use {@link #getOutputId()} for the output-centric model.
     */
    @Deprecated
    public String getProjectId() {
        return outputId;
    }

    public String getResultItemRef() {
        return resultItemRef;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
