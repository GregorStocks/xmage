package mage.client.streaming;

import mage.client.chat.ChatPanelBasic;
import mage.view.ChatMessage.MessageColor;
import mage.view.ChatMessage.MessageType;

import java.util.Date;
import java.util.regex.Pattern;

/**
 * Combined chat panel that displays both game log and chat messages
 * in a single interleaved view for streaming/observer mode.
 */
public class CombinedChatPanel extends ChatPanelBasic {

    public CombinedChatPanel() {
        super();
        useExtendedView(VIEW_MODE.GAME);
        setChatType(ChatType.GAME);
        disableInput();  // Observers cannot chat
    }

    @Override
    public void receiveMessage(String username, String message, Date time,
            String turnInfo, MessageType messageType, MessageColor color) {
        if (shouldFilterMessage(message)) {
            return;
        }
        super.receiveMessage(username, message, time, turnInfo, messageType, color);
    }

    // Patterns for spammy messages to filter out
    private static final Pattern[] FILTER_PATTERNS = {
        // "Player skip attack" - fires every turn when not attacking
        Pattern.compile(" skip attack$"),
        // "Player draws a card" or "Player draws X cards"
        Pattern.compile(" draws (a card|\\d+ cards)$"),
        // "Player puts CardName [id] from hand/stack onto the Battlefield"
        Pattern.compile(" puts .+ from (hand|stack) onto the Battlefield$"),
    };

    private boolean shouldFilterMessage(String message) {
        if (message == null) {
            return false;
        }
        for (Pattern pattern : FILTER_PATTERNS) {
            if (pattern.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }
}
