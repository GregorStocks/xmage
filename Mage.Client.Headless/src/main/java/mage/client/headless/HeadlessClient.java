package mage.client.headless;

import mage.interfaces.MageClient;
import mage.interfaces.callback.ClientCallback;
import mage.remote.Session;
import mage.utils.MageVersion;
import mage.view.*;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * Headless XMage client that stores game state and queues decisions
 */
public class HeadlessClient implements MageClient {

    private static final Logger logger = Logger.getLogger(HeadlessClient.class);

    private Session session;
    private UUID gameId;
    private UUID playerId;
    private GameView latestGameView;

    // Blocking queue for decisions that need to be made
    private final BlockingQueue<GameDecision> pendingDecisions = new LinkedBlockingQueue<>();

    // For submitting decisions back
    private volatile Object decisionResponse;
    private final Object decisionLock = new Object();

    private volatile boolean gameOver = false;
    private volatile String gameResult = "unknown";

    public HeadlessClient() {
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public UUID getGameId() {
        return gameId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public GameView getLatestGameView() {
        return latestGameView;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public String getGameResult() {
        return gameResult;
    }

    /**
     * Blocks until a decision is needed, then returns it
     */
    public GameDecision waitForDecision() throws InterruptedException {
        return pendingDecisions.take();
    }

    /**
     * Submit a decision response
     */
    public void submitUUID(UUID value) {
        if (gameId == null) {
            logger.error("Cannot submit UUID: game not initialized");
            return;
        }
        session.sendPlayerUUID(gameId, value);
    }

    public void submitBoolean(boolean value) {
        if (gameId == null) {
            logger.error("Cannot submit boolean: game not initialized");
            return;
        }
        session.sendPlayerBoolean(gameId, value);
    }

    public void submitInteger(int value) {
        if (gameId == null) {
            logger.error("Cannot submit integer: game not initialized");
            return;
        }
        session.sendPlayerInteger(gameId, value);
    }

    public void submitString(String value) {
        if (gameId == null) {
            logger.error("Cannot submit string: game not initialized");
            return;
        }
        session.sendPlayerString(gameId, value);
    }

    @Override
    public void onNewConnection() {
        logger.info("New connection established");
    }

    @Override
    public void onCallback(ClientCallback callback) {
        callback.decompressData();

        logger.debug("Callback: " + callback.getMethod());

        try {
            switch (callback.getMethod()) {
                case START_GAME: {
                    TableClientMessage message = (TableClientMessage) callback.getData();
                    logger.info("Game started");
                    gameId = message.getGameId();
                    playerId = message.getPlayerId();
                    session.joinGame(gameId);
                    break;
                }

                case GAME_INIT: {
                    GameView gameView = (GameView) callback.getData();
                    latestGameView = gameView;
                    logger.info("Game initialized");
                    break;
                }

                case GAME_UPDATE: {
                    GameView gameView = (GameView) callback.getData();
                    latestGameView = gameView;
                    break;
                }

                case GAME_UPDATE_AND_INFORM: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Game inform: " + message.getMessage());
                    break;
                }

                case GAME_TARGET: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Target needed: " + message.getMessage());
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.TARGET,
                        message.getMessage(),
                        message.getGameView(),
                        message.getOptions(),
                        message.getTargets()
                    ));
                    break;
                }

                case GAME_SELECT: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Select needed: " + message.getMessage());
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.SELECT,
                        message.getMessage(),
                        message.getGameView(),
                        message.getOptions(),
                        null
                    ));
                    break;
                }

                case GAME_ASK: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Ask needed: " + message.getMessage());
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.ASK,
                        message.getMessage(),
                        message.getGameView(),
                        message.getOptions(),
                        null
                    ));
                    break;
                }

                case GAME_CHOOSE_ABILITY: {
                    AbilityPickerView abilityPicker = (AbilityPickerView) callback.getData();
                    latestGameView = abilityPicker.getGameView();
                    logger.info("Choose ability needed");
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.CHOOSE_ABILITY,
                        "Choose an ability",
                        abilityPicker.getGameView(),
                        null,
                        abilityPicker
                    ));
                    break;
                }

                case GAME_CHOOSE_PILE: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Choose pile needed: " + message.getMessage());
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.CHOOSE_PILE,
                        message.getMessage(),
                        message.getGameView(),
                        message.getOptions(),
                        new Object[]{message.getCardsView1(), message.getCardsView2()}
                    ));
                    break;
                }

                case GAME_CHOOSE_CHOICE: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Choose choice needed: " + message.getMessage());
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.CHOOSE_CHOICE,
                        message.getMessage(),
                        message.getGameView(),
                        message.getOptions(),
                        message.getChoice()
                    ));
                    break;
                }

                case GAME_PLAY_MANA: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Play mana needed: " + message.getMessage());
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.PLAY_MANA,
                        message.getMessage(),
                        message.getGameView(),
                        message.getOptions(),
                        null
                    ));
                    break;
                }

                case GAME_PLAY_XMANA: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Play X mana needed: " + message.getMessage());
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.PLAY_XMANA,
                        message.getMessage(),
                        message.getGameView(),
                        null,
                        null
                    ));
                    break;
                }

                case GAME_GET_AMOUNT: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Get amount needed: " + message.getMessage());
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.GET_AMOUNT,
                        message.getMessage(),
                        message.getGameView(),
                        message.getOptions(),
                        new int[]{message.getMin(), message.getMax()}
                    ));
                    break;
                }

                case GAME_GET_MULTI_AMOUNT: {
                    GameClientMessage message = (GameClientMessage) callback.getData();
                    latestGameView = message.getGameView();
                    logger.info("Get multi amount needed");
                    pendingDecisions.offer(new GameDecision(
                        GameDecision.DecisionType.GET_MULTI_AMOUNT,
                        "Choose multiple amounts",
                        message.getGameView(),
                        message.getOptions(),
                        new Object[]{message.getMessages(), message.getMin(), message.getMax()}
                    ));
                    break;
                }

                case GAME_OVER: {
                    logger.info("Game over");
                    gameOver = true;
                    break;
                }

                case END_GAME_INFO: {
                    GameEndView endView = (GameEndView) callback.getData();
                    gameResult = endView.hasWon() ? "win" : "lose";
                    logger.info("Game ended: " + gameResult);
                    break;
                }

                case CHATMESSAGE: {
                    ChatMessage message = (ChatMessage) callback.getData();
                    logger.debug("Chat: " + message.getMessage());
                    break;
                }

                case SHOW_USERMESSAGE: {
                    List<String> messageData = (List<String>) callback.getData();
                    logger.info("User message: " + String.join(" - ", messageData));
                    break;
                }

                case JOINED_TABLE:
                    logger.info("Joined table");
                    break;

                default:
                    logger.warn("Unhandled callback: " + callback.getMethod());
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing callback", e);
        }
    }

    @Override
    public MageVersion getVersion() {
        return new MageVersion(HeadlessClient.class);
    }

    @Override
    public void connected(String message) {
        logger.info("Connected: " + message);
    }

    @Override
    public void disconnected(boolean askToReconnect, boolean keepMySessionActive) {
        logger.info("Disconnected (askReconnect=" + askToReconnect + ", keepSession=" + keepMySessionActive + ")");
    }

    @Override
    public void showMessage(String message) {
        logger.info("Message: " + message);
    }

    @Override
    public void showError(String message) {
        logger.error("Error: " + message);
    }
}
