package mage.client.headless;

import mage.cards.decks.DeckCardLists;
import mage.cards.decks.DeckCardInfo;
import mage.constants.MultiplayerAttackOption;
import mage.constants.RangeOfInfluence;
import mage.game.match.MatchOptions;
import mage.players.PlayerType;
import mage.remote.Session;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Helper to create games programmatically
 */
public class GameCreator {

    private static final Logger logger = Logger.getLogger(GameCreator.class);

    /**
     * Create a simple test deck (60 cards: 24 Mountains, 36 Lightning Bolts)
     */
    public static DeckCardLists createSimpleTestDeck() {
        DeckCardLists deck = new DeckCardLists();
        List<DeckCardInfo> cards = new ArrayList<>();

        // 30 Plains from Magic 2012 (M12)
        for (int i = 0; i < 30; i++) {
            cards.add(new DeckCardInfo("Plains", "230", "M12"));
        }

        // 30 Mountains from Magic 2012 (M12)
        for (int i = 0; i < 30; i++) {
            cards.add(new DeckCardInfo("Mountain", "242", "M12"));
        }

        deck.setCards(cards);
        deck.setName("Simple Test Deck");
        return deck;
    }

    /**
     * Create a 2-player duel table
     */
    public static Optional<UUID> createTwoPlayerDuel(Session session, String playerName) {
        try {
            MatchOptions options = new MatchOptions("Test Duel", "Two Player Duel", false);
            options.getPlayerTypes().add(PlayerType.HUMAN);
            options.getPlayerTypes().add(PlayerType.HUMAN);
            options.setDeckType("Constructed - Freeform");
            options.setAttackOption(MultiplayerAttackOption.LEFT);
            options.setRange(RangeOfInfluence.ALL);
            options.setWinsNeeded(1);
            options.setFreeMulligans(1);
            options.setPassword("");
            options.setLimited(false);

            logger.info("Creating game table: " + options.getName());

            mage.view.TableView tableView = session.createTable(
                session.getMainRoomId(),
                options
            );

            if (tableView != null) {
                UUID tableId = tableView.getTableId();
                logger.info("Table created with ID: " + tableId);
                return Optional.of(tableId);
            } else {
                logger.error("Failed to create table");
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Error creating table", e);
            return Optional.empty();
        }
    }

    /**
     * Join a table with a simple test deck
     */
    public static boolean joinTable(Session session, UUID tableId, String playerName) {
        try {
            DeckCardLists deck = createSimpleTestDeck();

            logger.info("Joining table " + tableId + " as " + playerName);

            boolean joined = session.joinTable(
                session.getMainRoomId(),
                tableId,
                playerName,
                PlayerType.HUMAN,
                1,
                deck,
                ""
            );

            if (joined) {
                logger.info("Successfully joined table");
            } else {
                logger.error("Failed to join table");
            }

            return joined;

        } catch (Exception e) {
            logger.error("Error joining table", e);
            return false;
        }
    }

    /**
     * Start a match (must be table controller)
     */
    public static boolean startMatch(Session session, UUID tableId) {
        try {
            logger.info("Starting match for table " + tableId);
            session.startMatch(session.getMainRoomId(), tableId);
            return true;
        } catch (Exception e) {
            logger.error("Error starting match", e);
            return false;
        }
    }
}
