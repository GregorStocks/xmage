package mage.client.headless;

import mage.view.GameView;
import java.io.Serializable;
import java.util.*;

/**
 * Represents a decision that needs to be made by the player
 */
public class GameDecision {

    public enum DecisionType {
        TARGET,           // GAME_TARGET - choose a target (UUID)
        SELECT,           // GAME_SELECT - priority/pass (boolean)
        ASK,              // GAME_ASK - yes/no question (boolean)
        CHOOSE_ABILITY,   // GAME_CHOOSE_ABILITY - pick an ability (UUID)
        CHOOSE_PILE,      // GAME_CHOOSE_PILE - pick a pile (UUID)
        CHOOSE_CHOICE,    // GAME_CHOOSE_CHOICE - pick from list (string)
        PLAY_MANA,        // GAME_PLAY_MANA - choose mana to pay (UUID)
        PLAY_XMANA,       // GAME_PLAY_XMANA - choose X mana (integer)
        GET_AMOUNT,       // GAME_GET_AMOUNT - choose a number (integer)
        GET_MULTI_AMOUNT  // GAME_GET_MULTI_AMOUNT - choose multiple numbers (map)
    }

    private final DecisionType type;
    private final String message;
    private final GameView gameView;
    private final Map<String, Serializable> options;
    private final Object extraData; // for type-specific data

    public GameDecision(DecisionType type, String message, GameView gameView,
                       Map<String, Serializable> options, Object extraData) {
        this.type = type;
        this.message = message;
        this.gameView = gameView;
        this.options = options;
        this.extraData = extraData;
    }

    public DecisionType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public GameView getGameView() {
        return gameView;
    }

    public Map<String, Serializable> getOptions() {
        return options;
    }

    public Object getExtraData() {
        return extraData;
    }
}
