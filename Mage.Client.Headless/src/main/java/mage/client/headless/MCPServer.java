package mage.client.headless;

import com.google.gson.*;
import mage.players.PlayableObjectsList;
import mage.view.*;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * MCP (Model Context Protocol) server that exposes game state and actions via stdio
 */
public class MCPServer {

    private static final Logger logger = Logger.getLogger(MCPServer.class);
    private final HeadlessClient client;
    private final Gson gson;
    private final BufferedReader stdin;
    private final PrintWriter stdout;

    public MCPServer(HeadlessClient client) {
        this.client = client;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.stdin = new BufferedReader(new InputStreamReader(System.in));
        this.stdout = new PrintWriter(System.out, true);
    }

    public void start() {
        logger.info("MCP Server started, listening on stdin");

        while (!client.isGameOver()) {
            try {
                String line = stdin.readLine();
                if (line == null) {
                    break; // EOF
                }

                processRequest(line);
            } catch (IOException e) {
                logger.error("Error reading from stdin", e);
                break;
            } catch (Exception e) {
                logger.error("Error processing request", e);
            }
        }

        logger.info("MCP Server stopped");
    }

    private void processRequest(String requestJson) {
        try {
            JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
            String method = request.get("method").getAsString();
            JsonElement id = request.get("id");

            logger.debug("MCP request: " + method);

            switch (method) {
                case "initialize":
                    handleInitialize(id);
                    break;
                case "tools/list":
                    handleToolsList(id);
                    break;
                case "tools/call":
                    handleToolCall(request, id);
                    break;
                default:
                    sendError(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            logger.error("Error processing request", e);
            sendError(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    private void handleInitialize(JsonElement id) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2024-11-05");
        result.addProperty("serverInfo", "XMage Headless Client MCP Server");

        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("tools", true);
        result.add("capabilities", capabilities);

        sendResponse(id, result);
    }

    private void handleToolsList(JsonElement id) {
        JsonArray tools = new JsonArray();

        // Tool 1: wait_for_my_turn (long-polling)
        JsonObject waitTool = new JsonObject();
        waitTool.addProperty("name", "wait_for_my_turn");
        waitTool.addProperty("description",
            "Blocks until the game needs you to make a decision. Returns the game state, " +
            "what decision is needed, and available options. This is a long-polling call " +
            "that will wait until it's your turn to act.");
        JsonObject waitSchema = new JsonObject();
        waitSchema.addProperty("type", "object");
        waitSchema.add("properties", new JsonObject());
        waitTool.add("inputSchema", waitSchema);
        tools.add(waitTool);

        // Tool 2: submit_uuid_decision
        JsonObject submitUuidTool = new JsonObject();
        submitUuidTool.addProperty("name", "submit_uuid_decision");
        submitUuidTool.addProperty("description",
            "Submit a decision by UUID (for targets, abilities, cards, etc.)");
        JsonObject uuidSchema = new JsonObject();
        uuidSchema.addProperty("type", "object");
        JsonObject uuidProps = new JsonObject();
        JsonObject uuidProp = new JsonObject();
        uuidProp.addProperty("type", "string");
        uuidProp.addProperty("description", "The UUID of the chosen option");
        uuidProps.add("uuid", uuidProp);
        uuidSchema.add("properties", uuidProps);
        JsonArray required = new JsonArray();
        required.add("uuid");
        uuidSchema.add("required", required);
        submitUuidTool.add("inputSchema", uuidSchema);
        tools.add(submitUuidTool);

        // Tool 3: submit_boolean_decision
        JsonObject submitBoolTool = new JsonObject();
        submitBoolTool.addProperty("name", "submit_boolean_decision");
        submitBoolTool.addProperty("description",
            "Submit a boolean decision (yes/no, pass priority, mulligan, etc.)");
        JsonObject boolSchema = new JsonObject();
        boolSchema.addProperty("type", "object");
        JsonObject boolProps = new JsonObject();
        JsonObject boolProp = new JsonObject();
        boolProp.addProperty("type", "boolean");
        boolProp.addProperty("description", "true or false");
        boolProps.add("value", boolProp);
        boolSchema.add("properties", boolProps);
        JsonArray boolRequired = new JsonArray();
        boolRequired.add("value");
        boolSchema.add("required", boolRequired);
        submitBoolTool.add("inputSchema", boolSchema);
        tools.add(submitBoolTool);

        // Tool 4: submit_integer_decision
        JsonObject submitIntTool = new JsonObject();
        submitIntTool.addProperty("name", "submit_integer_decision");
        submitIntTool.addProperty("description",
            "Submit an integer decision (amount, X value, etc.)");
        JsonObject intSchema = new JsonObject();
        intSchema.addProperty("type", "object");
        JsonObject intProps = new JsonObject();
        JsonObject intProp = new JsonObject();
        intProp.addProperty("type", "integer");
        intProp.addProperty("description", "The integer value");
        intProps.add("value", intProp);
        intSchema.add("properties", intProps);
        JsonArray intRequired = new JsonArray();
        intRequired.add("value");
        intSchema.add("required", intRequired);
        submitIntTool.add("inputSchema", intSchema);
        tools.add(submitIntTool);

        // Tool 5: peek_game_state (non-blocking)
        JsonObject peekTool = new JsonObject();
        peekTool.addProperty("name", "peek_game_state");
        peekTool.addProperty("description",
            "Get the current game state without blocking. Useful for querying context " +
            "while making a decision.");
        JsonObject peekSchema = new JsonObject();
        peekSchema.addProperty("type", "object");
        peekSchema.add("properties", new JsonObject());
        peekTool.add("inputSchema", peekSchema);
        tools.add(peekTool);

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        sendResponse(id, result);
    }

    private void handleToolCall(JsonObject request, JsonElement id) {
        JsonObject params = request.getAsJsonObject("params");
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        logger.info("Tool call: " + toolName);

        try {
            switch (toolName) {
                case "wait_for_my_turn":
                    handleWaitForMyTurn(id);
                    break;
                case "submit_uuid_decision":
                    handleSubmitUuid(arguments, id);
                    break;
                case "submit_boolean_decision":
                    handleSubmitBoolean(arguments, id);
                    break;
                case "submit_integer_decision":
                    handleSubmitInteger(arguments, id);
                    break;
                case "peek_game_state":
                    handlePeekGameState(id);
                    break;
                default:
                    sendError(id, -32602, "Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            logger.error("Error executing tool: " + toolName, e);
            sendError(id, -32603, "Tool execution error: " + e.getMessage());
        }
    }

    private void handleWaitForMyTurn(JsonElement id) throws InterruptedException {
        logger.info("Waiting for decision (blocking)...");
        GameDecision decision = client.waitForDecision(); // BLOCKS HERE
        logger.info("Decision received: " + decision.getType());

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", serializeDecision(decision));
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content);
        sendResponse(id, result);
    }

    private void handleSubmitUuid(JsonObject arguments, JsonElement id) {
        String uuidStr = arguments.get("uuid").getAsString();
        UUID uuid = UUID.fromString(uuidStr);
        client.submitUUID(uuid);

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", "UUID decision submitted: " + uuid);
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content);
        sendResponse(id, result);
    }

    private void handleSubmitBoolean(JsonObject arguments, JsonElement id) {
        boolean value = arguments.get("value").getAsBoolean();
        client.submitBoolean(value);

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", "Boolean decision submitted: " + value);
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content);
        sendResponse(id, result);
    }

    private void handleSubmitInteger(JsonObject arguments, JsonElement id) {
        int value = arguments.get("value").getAsInt();
        client.submitInteger(value);

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", "Integer decision submitted: " + value);
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content);
        sendResponse(id, result);
    }

    private void handlePeekGameState(JsonElement id) {
        GameView gameView = client.getLatestGameView();

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", serializeGameView(gameView));
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content);
        sendResponse(id, result);
    }

    private String serializeDecision(GameDecision decision) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DECISION NEEDED ===\n");
        sb.append("Type: ").append(decision.getType()).append("\n");
        sb.append("Message: ").append(decision.getMessage()).append("\n\n");

        sb.append("=== GAME STATE ===\n");
        sb.append(serializeGameView(decision.getGameView()));
        sb.append("\n");

        if (decision.getOptions() != null && !decision.getOptions().isEmpty()) {
            sb.append("=== OPTIONS ===\n");
            decision.getOptions().forEach((key, value) -> {
                sb.append(key).append(": ").append(value).append("\n");
            });
        }

        return sb.toString();
    }

    private String serializeGameView(GameView gameView) {
        if (gameView == null) {
            return "No game state available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Turn: ").append(gameView.getTurn()).append("\n");
        sb.append("Phase: ").append(gameView.getPhase()).append("\n");
        sb.append("Step: ").append(gameView.getStep()).append("\n");
        sb.append("Priority Player: ").append(gameView.getPriorityPlayerName()).append("\n");
        sb.append("Active Player: ").append(gameView.getActivePlayerName()).append("\n\n");

        // Players
        sb.append("=== PLAYERS ===\n");
        for (PlayerView player : gameView.getPlayers()) {
            sb.append(player.getName())
              .append(" - Life: ").append(player.getLife())
              .append(", Hand: ").append(player.getHandCount())
              .append(", Library: ").append(player.getLibraryCount())
              .append(", Graveyard: ").append(player.getGraveyard().size())
              .append("\n");
        }
        sb.append("\n");

        // My hand
        if (gameView.getMyHand() != null && !gameView.getMyHand().isEmpty()) {
            sb.append("=== MY HAND ===\n");
            gameView.getMyHand().values().forEach(card -> {
                sb.append(card.getId()).append(": ").append(card.getName()).append("\n");
            });
            sb.append("\n");
        }

        // Playable objects
        PlayableObjectsList canPlay = gameView.getCanPlayObjects();
        if (canPlay != null && !canPlay.isEmpty()) {
            sb.append("=== PLAYABLE ===\n");
            canPlay.getObjects().forEach((uuid, stats) -> {
                sb.append(uuid).append(": ")
                  .append(stats.getPlayableAmount()).append(" abilities\n");
            });
            sb.append("\n");
        }

        // Stack
        if (gameView.getStack() != null && !gameView.getStack().isEmpty()) {
            sb.append("=== STACK ===\n");
            gameView.getStack().values().forEach(card -> {
                sb.append(card.getName()).append("\n");
            });
            sb.append("\n");
        }

        return sb.toString();
    }

    private void sendResponse(JsonElement id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        stdout.println(gson.toJson(response));
    }

    private void sendError(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("error", error);
        stdout.println(gson.toJson(response));
    }
}
