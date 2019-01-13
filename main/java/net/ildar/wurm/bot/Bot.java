package net.ildar.wurm.bot;

import net.ildar.wurm.Chat;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class Bot extends Thread {
    private static List<Bot> activeBots = new ArrayList<>();

    /**
     * The list of all bot implementations.
     */
    private static List<BotRegistration> botList = new ArrayList<>();
    static {
        registerBot(ArcherBot.class,
                "Automatically shoots at selected target with currently equipped bow. " +
                        "When the string breaks tries to place a new one. " +
                        "Deactivates on target death.",
                "ar");
        registerBot(AssistantBot.class,
                "Assists player in various ways",
                "a");
        registerBot(BulkItemGetterBot.class,
                "Automatically transfers items to player's inventory from configured bulk storages. " +
                        "The n-th  source item will be transferred to the n-th target item",
                "big");
        registerBot(ChopperBot.class,
                "Automatically chops felled trees near player",
                "ch");
        registerBot(CrafterBot.class,
                "Automatically does crafting operations using items from crafting window. " +
                "New crafting operations are not starting until an action queue becomes empty. This behaviour can be disabled. ", "c");
        registerBot(FlowerPlanterBot.class,
                "Skills up player's gardening skill by planting and picking flowers in surrounding area",
                "fp");
        registerBot(ImproverBot.class,
                "Improves selected items in provided inventories. Tools searched from player's inventory. " +
                        "Items like water or stone searched before each improve, " +
                        "actual instruments searched one time before improve of the first item that must be improved with this tool. " +
                        "Tool for improving is determined by improve icon that you see on the right side of item row in inventory. " +
                        "For example improve icons for stone chisel and carving knife are equal, and sometimes bot can choose wrong tool. " +
                        "Use \"" + ImproverBot.InputKey.ci.name() + "\" key to change the chosen instrument.",
                "i");
        registerBot(ForageStuffMoverBot.class,
                "Moves foragable and botanizable items from your inventory to the target inventories. " +
                        "Optionally you can toggle the moving of rocks or rare items on and off.",
                "fsm");
        registerBot(ForesterBot.class,
                "A forester bot. Can pick and plant sprouts, cut trees/bushes and gather the harvest in 3x3 area around player. " +
                        "Bot can be configured to process rectangular area of any size. " +
                        "Sprouts, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. " +
                        "Default container name is \"" + ForesterBot.DEFAULT_CONTAINER_NAME + "\". Containers only in root directory of player's inventory will be taken into account. " +
                        "New item names can be added(harvested fruits for example) to be moved to containers too. " +
                        "Steppe and moss tiles will be cultivated if planting is enabled and player have shovel in his inventory. ",
                "fr");
        registerBot(ForagerBot.class,
                "Can forage, botanize, collect grass and flowers in an area surrounding player. " +
                        "Bot can be configured to process rectangular area of any size. " +
                        "Picked items, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. " +
                        "Default container name is \"" + ForagerBot.DEFAULT_CONTAINER_NAME + "\". Containers only in root directory of player's inventory will be taken into account. " +
                        "Bot can be configured to drop picked items on the floor. ",
                "fg");
        registerBot(GroundItemGetterBot.class,
                "Collects items from the ground around player.",
                "gig");
        registerBot(GuardBot.class,
                "Looks for messages in Event and Combat tabs. " +
                        "Raises alarm if no messages were received during configured time. " +
                        "With no provided keywords the bot will be satisfied with every message. " +
                        "If user adds some keywords bot will compare messages only with them.",
                "g");
        registerBot(ItemMoverBot.class,
                "Moves items from your inventory to the target destination.", "im");
        registerBot(MinerBot.class,
                "Mines rocks and smelts ores.", "m");
        registerBot(MeditationBot.class,
                "Meditates on the carpet. Assumes that there are no restrictions on meditation skill.", "md");
        registerBot(HealingBot.class,
                "Heals the player's wounds with cotton found in inventory", "h");
        registerBot(FarmerBot.class,
                "Tends the fields, plants the seeds, cultivates the ground, collects harvests", "f");
        registerBot(DiggerBot.class,
                "Does the dirty job for you", "d");
        registerBot(PileCollector.class,
                "Collects piles of items to bulk containers. Default name for target items is \"dirt\"", "pc");
        registerBot(FisherBot.class,
                "Catches and cuts fish", "fsh");
		registerBot(ProspectorBot.class,
                "Prospect selected tile", "pr");
		registerBot(TreeCutterBot.class,
                "Cut trees", "tc");
    }

    /**
     * The bot implementation should register his input handlers with {@link #registerInputHandler(InputKey, InputHandler)}
     */
    private Map<InputKey, InputHandler> inputHandlers = new HashMap<>();

    /**
     * Store all registered message processors here to unregister them on bot deactivation to prevent memory leaks
     */
    private List<Chat.MessageProcessor> registeredMessageProcessors = new ArrayList<>();

    protected long timeout = 1000;

    public static synchronized void deactivateAllBots() {
        List<Bot> bots = new ArrayList<>(Bot.activeBots);
        bots.forEach(Bot::deactivate);
    }

    public static synchronized boolean isInstantiated(Class<? extends Bot> botClass) {
        return Bot.activeBots.stream().anyMatch(bot -> bot.getClass().equals(botClass));
    }

    public static synchronized Bot getInstance(Class<? extends Bot> botClass) {
        Bot instance = null;
        try {
            Optional<Bot> optionalKimeBot = activeBots.stream().filter(bot -> bot.getClass().equals(botClass)).findAny();
            if (!optionalKimeBot.isPresent()) {
                instance = botClass.newInstance();
                activeBots.add(instance);
            } else
                instance = optionalKimeBot.get();
        } catch (InstantiationException | IllegalAccessException | NoSuchElementException | NullPointerException e) {
            e.printStackTrace();
        }
        return instance;
    }

    public static void printBotDescription(Class<? extends Bot> botClass) {
        BotRegistration botRegistration = getBotRegistration(botClass);
        String description = "no description";
        if (botRegistration != null)
            description = botRegistration.description;
        Utils.consolePrint("=== " + botClass.getSimpleName() + " ===");
        Utils.consolePrint(description);
        if (isInstantiated(botClass)) {
            Bot botInstance = getInstance(botClass);
            Utils.consolePrint(botInstance.getUsageString());
        } else {
            String abbreviation = "*";
            if (botRegistration != null)
                abbreviation = botRegistration.abbreviation;
            Utils.consolePrint("Type \"" + Mod.ConsoleCommand.bot.name() + " " + abbreviation + " " + "on\" to activate the bot");
        }
    }

    public static String getBotUsageString() {
        StringBuilder result = new StringBuilder("Usage: " + Mod.ConsoleCommand.bot.name() + " {");
        for(BotRegistration botRegistration : botList)
            result.append(botRegistration.abbreviation).append("|");
        result.append("off}");
        return result.toString();
    }

    public static Class<? extends Bot> getBotClass(String abbreviation) {
        for(BotRegistration botRegistration : botList)
            if (botRegistration.abbreviation.equals(abbreviation))
                return botRegistration.botClass;
        return null;
    }

    private static void registerBot(Class<? extends Bot> botClass, String description, String abbreviation) {
        Utils.consolePrint("Registering new bot with abbreviation " + abbreviation);
        botList.add(new BotRegistration(botClass, description, abbreviation));
    }

    private static BotRegistration getBotRegistration(Class<? extends Bot> botClass) {
        for(BotRegistration botRegistration : botList) {
            if (botRegistration.botClass.equals(botClass))
                return botRegistration;
        }
        return null;
    }

    public Bot() {
        //register standard input handlers
        registerInputHandler(InputKeyBase.t, this::handleTimeoutChange);
        registerInputHandler(InputKeyBase.off, inputs -> deactivate());
        registerInputHandler(InputKeyBase.info, this::handleInfoCommand);
    }

    protected abstract void work() throws Exception;

    @Override
    public void run() {
        try {
            work();
        } catch (InterruptedException ignored) {
        } catch(Exception e) {
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
            Utils.consolePrint( e.toString());
        }
        unregisterMessageProcessors();
        synchronized (Bot.class) {
            activeBots.remove(this);
        }
        Utils.consolePrint(this.getClass().getSimpleName() + " was stopped");
    }

    public boolean isActive() {
        synchronized (Bot.class) {
            return activeBots.contains(this) && !isInterrupted();
        }
    }

    public void deactivate() {
        synchronized (Bot.class) {
            if (!super.isAlive()) {
                activeBots.remove(this);
                return;
            }
            Utils.consolePrint("Deactivating " + getClass().getSimpleName());
            interrupt();
        }
    }

    protected String getAbbreviation() {
        BotRegistration botRegistration = getBotRegistration(this.getClass());
        if (botRegistration == null) return null;
        return botRegistration.abbreviation;
    }

    String getUsageString() {
        StringBuilder output = new StringBuilder();
        output
                .append("Usage: ")
                .append(Mod.ConsoleCommand.bot.name())
                .append(" ")
                .append(getAbbreviation())
                .append(" {");
        boolean firstInputKeyString = true;
        List<String> sortedInputKeys = inputHandlers.keySet().stream().map(InputKey::getName).sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        for(String inputKey : sortedInputKeys) {
            if (firstInputKeyString)
                firstInputKeyString = false;
            else
                output.append("|");
            output.append(inputKey);
        }
        output.append("}");
        return output.toString();
    }

    void printInputKeyUsageString(InputKey inputKey) {
        Utils.consolePrint("Usage: " + Mod.ConsoleCommand.bot.name() + " " + getAbbreviation() + " " + inputKey.getName() + " " + inputKey.getUsage());
    }

    /**
     * Handle the console input for current bot instance
     * @param data console input
     * @return true if input was processed and false if input should be handled by derived classes
     */
    final public boolean handleInput(String data[]) {
        if (data == null || data.length == 0)
            return false;
        InputHandler inputHandler = getInputHandler(data[0]);
        if (inputHandler == null) {
            Utils.consolePrint("Unknown key - " + data[0]);
            printBotDescription(this.getClass());
            return false;
        }
        String[] handlerParameters = null;
        if (data.length > 1)
            handlerParameters = Arrays.copyOfRange(data, 1, data.length);
        inputHandler.handle(handlerParameters);
        return true;
    }

    private void handleInfoCommand(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKeyBase.info);
            return;
        }
        InputKey inputKey = getInputKey(input[0]);
        if (inputKey == null) {
            Utils.consolePrint("Unknown key");
            Utils.consolePrint(getUsageString());
            return;
        }
        Utils.consolePrint(inputKey.getDescription());
    }

    private void handleTimeoutChange(String []input){
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKeyBase.t);
            return;
        }
        try {
            int timeout = Integer.parseInt(input[0]);
            setTimeout(timeout);
        } catch (Exception e) {
            Utils.consolePrint("Wrong timeout value!");
        }
    }

    protected void setTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            timeout = 100;
        }
        this.timeout = timeout;
        Utils.consolePrint("Current timeout is " + timeout + " milliseconds");
    }

    /**
     * The enumeration type of the key must have a usage and description string fields for each item
     */
    void registerInputHandler(InputKey key, InputHandler inputHandler) {
        InputKey oldKey = getInputKey(key.getName());
        if (oldKey != null)
            inputHandlers.remove(oldKey);
        inputHandlers.put(key, inputHandler);
    }

    private InputKey getInputKey(String key) {
        for(InputKey inputKey : inputHandlers.keySet())
            if (inputKey.getName().equals(key))
                return inputKey;
        return null;
    }

    private InputHandler getInputHandler(String key) {
        InputKey inputKey = getInputKey(key);
        if (inputKey == null) return null;
        return inputHandlers.get(inputKey);
    }

    final void registerEventProcessor(Function<String, Boolean> filter, Runnable callback) {
        registerMessageProcessor(":Event", filter, callback);
    }

    final void registerMessageProcessor(String tabName, Function<String, Boolean> filter, Runnable callback) {
        registeredMessageProcessors.add(Chat.registerMessageProcessor(tabName, filter, callback));
    }

    private void unregisterMessageProcessors() {
        registeredMessageProcessors.forEach(Chat::unregisterMessageProcessor);
    }

    private enum InputKeyBase implements InputKey {
        t("Set the timeout for bot. The bot will wait for specified time(in milliseconds) after each iteration/update",
                "timeout(in milliseconds)"),
        off("Deactivate the bot",
                ""),
        info("Get information about configuration key",
                "key");

        private String description;
        private String usage;
        InputKeyBase(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getUsage() {
            return usage;
        }

        @Override
        public String getName() {
            return name();
        }
    }

    private static class BotRegistration{
        private Class<? extends Bot> botClass;
        private String description;
        private String abbreviation;

        BotRegistration(Class<? extends Bot> botClass, String description, String abbreviation) {
            this.botClass = botClass;
            this.description = description;
            this.abbreviation = abbreviation;
        }
    }

    protected interface InputHandler{
        void handle(String []inputData);
    }

    interface InputKey {
        String getName();
        String getDescription();
        String getUsage();
    }
}
