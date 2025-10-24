package mage.client.headless;

import mage.constants.PlayerAction;
import mage.remote.Connection;
import mage.remote.Session;
import mage.remote.SessionImpl;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

/**
 * Simple test that creates a game with 2 bots that auto-pass
 *
 * Usage:
 *   java -cp target/mage-client-headless-1.4.58.jar mage.client.headless.SimpleTest [server] [port]
 */
public class SimpleTest {

    private static final Logger logger = Logger.getLogger(SimpleTest.class);

    public static void main(String[] args) {
        String server = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 17171;

        logger.info("=== Simple Test: 2 Bots Auto-Passing ===");
        logger.info("Server: " + server + ":" + port);

        try {
            // Create Bot 1 (will create the game)
            logger.info("\n--- Creating Bot 1 ---");
            AutoPassBot bot1 = new AutoPassBot("TestBot1", server, port);
            bot1.connect();

            // Create game
            logger.info("\n--- Creating Game Table ---");
            UUID tableId = bot1.createGame();
            if (tableId == null) {
                logger.error("Failed to create game");
                System.exit(1);
            }

            // Bot 1 joins
            logger.info("\n--- Bot 1 Joining Table ---");
            if (!bot1.joinGame(tableId)) {
                logger.error("Bot 1 failed to join table");
                System.exit(1);
            }

            // Create Bot 2
            logger.info("\n--- Creating Bot 2 ---");
            AutoPassBot bot2 = new AutoPassBot("TestBot2", server, port);
            bot2.connect();

            // Bot 2 joins
            logger.info("\n--- Bot 2 Joining Table ---");
            if (!bot2.joinGame(tableId)) {
                logger.error("Bot 2 failed to join table");
                System.exit(1);
            }

            // Start the game
            logger.info("\n--- Starting Match ---");
            if (!bot1.startMatch(tableId)) {
                logger.error("Failed to start match");
                System.exit(1);
            }

            logger.info("\n=== Game Started ===");
            logger.info("Bots will auto-pass until game ends...");
            logger.info("Press Ctrl+C to stop\n");

            // Start auto-passing threads
            bot1.startAutoPass();
            bot2.startAutoPass();

            // Wait for games to end
            while (!bot1.isGameOver() || !bot2.isGameOver()) {
                Thread.sleep(1000);
            }

            logger.info("\n=== Game Over ===");
            logger.info("Bot 1 result: " + bot1.getGameResult());
            logger.info("Bot 2 result: " + bot2.getGameResult());

            // Cleanup
            bot1.disconnect();
            bot2.disconnect();

        } catch (Exception e) {
            logger.error("Test failed", e);
            System.exit(1);
        }
    }

    /**
     * Bot that automatically passes priority
     */
    static class AutoPassBot {
        private final String username;
        private final String server;
        private final int port;
        private HeadlessClient client;
        private Session session;
        private Thread autoPassThread;

        public AutoPassBot(String username, String server, int port) {
            this.username = username;
            this.server = server;
            this.port = port;
        }

        public void connect() {
            logger.info("Connecting " + username + "...");
            client = new HeadlessClient();
            session = new SessionImpl(client);
            client.setSession(session);

            Connection connection = new Connection();
            connection.setHost(server);
            connection.setPort(port);
            connection.setUsername(username);
            connection.setProxyType(Connection.ProxyType.NONE);

            if (!session.connectStart(connection)) {
                throw new RuntimeException("Failed to connect");
            }

            if (!session.isConnected()) {
                throw new RuntimeException("Not connected");
            }

            // Enable auto-payment
            session.sendPlayerAction(PlayerAction.MANA_AUTO_PAYMENT_ON, null, null);
            session.sendPlayerAction(PlayerAction.USE_FIRST_MANA_ABILITY_ON, null, null);

            logger.info(username + " connected successfully");
        }

        public UUID createGame() {
            return GameCreator.createTwoPlayerDuel(session, username).orElse(null);
        }

        public boolean joinGame(UUID tableId) {
            return GameCreator.joinTable(session, tableId, username);
        }

        public boolean startMatch(UUID tableId) {
            return GameCreator.startMatch(session, tableId);
        }

        public void startAutoPass() {
            autoPassThread = new Thread(() -> {
                logger.info(username + " auto-pass thread started");

                while (!client.isGameOver()) {
                    try {
                        // Wait for decision
                        GameDecision decision = client.waitForDecision();
                        logger.info(username + " received decision: " + decision.getType() + " - " + decision.getMessage());

                        // Always pass priority / say no, but for targets pick first option
                        switch (decision.getType()) {
                            case TARGET: {
                                // For target decisions, pick the first available target
                                Object extraData = decision.getExtraData();
                                if (extraData instanceof java.util.Set) {
                                    @SuppressWarnings("unchecked")
                                    java.util.Set<UUID> targets = (java.util.Set<UUID>) extraData;
                                    if (!targets.isEmpty()) {
                                        UUID firstTarget = targets.iterator().next();
                                        logger.info(username + " selecting first target: " + firstTarget);
                                        client.submitUUID(firstTarget);
                                    } else {
                                        logger.warn(username + " no targets available, submitting false");
                                        client.submitBoolean(false);
                                    }
                                } else {
                                    logger.warn(username + " unexpected extraData type for TARGET: " +
                                        (extraData != null ? extraData.getClass() : "null"));
                                    client.submitBoolean(false);
                                }
                                break;
                            }

                            case CHOOSE_ABILITY:
                            case CHOOSE_PILE:
                            case PLAY_MANA: {
                                // For choices that need a UUID, check if we have options
                                Map<String, Serializable> options = decision.getOptions();
                                if (options != null && !options.isEmpty()) {
                                    // Try to find a UUID in options
                                    for (Serializable value : options.values()) {
                                        if (value instanceof UUID) {
                                            logger.info(username + " selecting first UUID option: " + value);
                                            client.submitUUID((UUID) value);
                                            break;
                                        }
                                    }
                                } else {
                                    logger.info(username + " no options, passing");
                                    client.submitBoolean(false);
                                }
                                break;
                            }

                            case SELECT:
                            case ASK:
                                // Pass priority / say no
                                logger.info(username + " passing priority");
                                client.submitBoolean(false);
                                break;

                            case PLAY_XMANA:
                            case GET_AMOUNT:
                                // Choose 0
                                logger.info(username + " choosing 0");
                                client.submitInteger(0);
                                break;

                            case CHOOSE_CHOICE:
                                // Submit empty string
                                logger.info(username + " submitting empty choice");
                                client.submitString("");
                                break;

                            default:
                                logger.warn(username + " unknown decision type: " + decision.getType());
                                client.submitBoolean(false);
                        }

                    } catch (InterruptedException e) {
                        logger.info(username + " auto-pass thread interrupted");
                        break;
                    } catch (Exception e) {
                        logger.error(username + " error in auto-pass", e);
                    }
                }

                logger.info(username + " auto-pass thread ended");
            });

            autoPassThread.start();
        }

        public boolean isGameOver() {
            return client.isGameOver();
        }

        public String getGameResult() {
            return client.getGameResult();
        }

        public void disconnect() {
            if (autoPassThread != null) {
                autoPassThread.interrupt();
            }
            if (session != null) {
                session.connectStop(false, false);
            }
            logger.info(username + " disconnected");
        }
    }
}
