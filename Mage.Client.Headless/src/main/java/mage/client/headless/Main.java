package mage.client.headless;

import mage.constants.PlayerAction;
import mage.remote.Connection;
import mage.remote.Session;
import mage.remote.SessionImpl;
import org.apache.log4j.Logger;

import java.util.UUID;

/**
 * Entry point for headless MCP client
 *
 * Usage:
 *   java -jar mage-client-headless.jar <server> <port> <username> [gameId]
 *
 * Example:
 *   java -jar mage-client-headless.jar localhost 17171 ClaudeBot1
 *   java -jar mage-client-headless.jar localhost 17171 ClaudeBot1 550e8400-e29b-41d4-a716-446655440000
 */
public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java -jar mage-client-headless.jar <server> <port> <username> [gameId]");
            System.err.println("Example: java -jar mage-client-headless.jar localhost 17171 ClaudeBot1");
            System.exit(1);
        }

        String server = args[0];
        int port = Integer.parseInt(args[1]);
        String username = args[2];
        UUID gameId = args.length > 3 ? UUID.fromString(args[3]) : null;

        logger.info("Starting headless XMage client");
        logger.info("Server: " + server + ":" + port);
        logger.info("Username: " + username);

        try {
            // Create headless client
            HeadlessClient client = new HeadlessClient();

            // Create session
            Session session = new SessionImpl(client);
            client.setSession(session);

            // Connect to server
            logger.info("Connecting to server...");
            Connection connection = new Connection();
            connection.setHost(server);
            connection.setPort(port);
            connection.setUsername(username);
            connection.setProxyType(Connection.ProxyType.NONE);

            if (!session.connectStart(connection)) {
                logger.error("Failed to connect to server");
                System.exit(1);
            }

            if (!session.isConnected()) {
                logger.error("Connection established but not connected");
                System.exit(1);
            }

            logger.info("Connected successfully");

            // If gameId provided, join that game
            if (gameId != null) {
                logger.info("Joining game: " + gameId);
                session.joinGame(gameId);
            } else {
                logger.info("No game ID provided. Waiting for game start...");
                logger.info("(You'll need to create/join a game through the server)");
            }

            // Enable auto-payment for smoother gameplay
            session.sendPlayerAction(PlayerAction.MANA_AUTO_PAYMENT_ON, null, null);
            session.sendPlayerAction(PlayerAction.USE_FIRST_MANA_ABILITY_ON, null, null);

            // Start MCP server (blocks)
            logger.info("Starting MCP server on stdin/stdout...");
            MCPServer mcpServer = new MCPServer(client);
            mcpServer.start();

            // Cleanup
            logger.info("Game ended, disconnecting...");
            session.connectStop(false, false);
            logger.info("Goodbye!");

        } catch (Exception e) {
            logger.error("Fatal error", e);
            System.exit(1);
        }
    }
}
