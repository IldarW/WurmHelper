package net.ildar.wurm;

import net.ildar.wurm.bot.Bot;

import java.util.*;

public class BotController {
    private static BotController instance;
    private List<BotRegistration> botList;
    private List<Bot> activeBots = new ArrayList<>();
    private boolean gPaused = false;

    public static synchronized BotController getInstance() {
        if (instance == null)
            instance = new BotController();
        return instance;
    }

    private BotController() {
        botList = BotRegistrationProvider.getBotList();
    }

    public void handleInput(String data[]) {
        String usageString = getBotUsageString();

        if (data.length < 1) {
            Utils.consolePrint(usageString);
            Utils.writeToConsoleInputLine(WurmHelper.ConsoleCommand.bot.name() + " ");
            return;
        }
        if (data[0].equals("off")) {
            deactivateAllBots();
            return;
        }
        if (data[0].equals("pause")) {
            pauseAllBots();
            Utils.writeToConsoleInputLine(WurmHelper.ConsoleCommand.bot.name() + " pause");
            return;
        }
        Class<? extends Bot> botClass = getBotClass(data[0]);
        if (botClass == null) {
            Utils.consolePrint("Didn't find a bot with abbreviation \"" + data[0] + "\"");
            Utils.consolePrint(usageString);
            return;
        }

        if (data.length == 1) {
            printBotDescription(botClass);
            Utils.writeToConsoleInputLine(WurmHelper.ConsoleCommand.bot.name() + " " + data[0] + " ");
            return;
        }
        if (isInstantiated(botClass)) {
            Bot botInstance = getInstance(botClass);
            if (botInstance.isInterrupted()) {
                Utils.consolePrint(botClass.getSimpleName() + " is trying to stop");
            } else if (data[1].equals("on")) {
                Utils.consolePrint(botClass.getSimpleName() + " is already on");
            } else {
                try {
                    botInstance.handleInput(Arrays.copyOfRange(data, 1, data.length));
                } catch (Exception e) {
                    Utils.consolePrint("Unable to configure  " + botClass.getSimpleName());
                    e.printStackTrace();
                }
            }
        } else {
            if (data[1].equals("on")) {
                Bot botInstance = getInstance(botClass);
                if (botInstance != null) {
                    botInstance.start();
                    Utils.consolePrint(botClass.getSimpleName() + " is on!");
                    printBotDescription(botClass);
                } else {
                    Utils.consolePrint("Internal error on bot activation");
                }
            } else {
                Utils.consolePrint(botClass.getSimpleName() + " is not running!");
            }
        }
        Utils.writeToConsoleInputLine(WurmHelper.ConsoleCommand.bot.name() + " " + data[0] + " ");
    }

    public synchronized boolean isActive(Bot bot) {
        return activeBots.contains(bot);
    }

    private synchronized void deactivateAllBots() {
        List<Bot> bots = new ArrayList<>(activeBots);
        bots.forEach(Bot::deactivate);
    }

    public synchronized void onBotInterruption(Bot bot) {
        activeBots.remove(bot);
    }

    private synchronized void pauseAllBots() {
        if (activeBots.size() > 0) {
            gPaused = !gPaused;
            if (gPaused) {
                activeBots.forEach(Bot::setPaused);
            } else {
                activeBots.forEach(Bot::setResumed);
            }
            Utils.consolePrint("All bots have been " + (gPaused ? "paused!" : "resumed!"));
        } else {
            Utils.consolePrint("No bots are running!");
        }
    }

    //this method is being invoked from com.wurmonline.client.renderer.cell.GroundItemCellRenderable
    @SuppressWarnings("WeakerAccess")
    public synchronized boolean isInstantiated(Class<? extends Bot> botClass) {
        return activeBots.stream().anyMatch(bot -> bot.getClass().equals(botClass));
    }

    //this method is being invoked from com.wurmonline.client.renderer.cell.GroundItemCellRenderable
    @SuppressWarnings("WeakerAccess")
    public synchronized <T extends Bot> T getInstance(Class<T> botClass) {
        T instance = null;
        try {
            Optional<Bot> optionalBot = activeBots.stream().filter(bot -> bot.getClass().equals(botClass)).findAny();
            if (!optionalBot.isPresent()) {
                instance = botClass.newInstance();
                activeBots.add(instance);
            } else
                //noinspection unchecked
                instance = (T) optionalBot.get();
        } catch (InstantiationException | IllegalAccessException | NoSuchElementException | NullPointerException e) {
            e.printStackTrace();
        }
        return instance;
    }

    public void printBotDescription(Class<? extends Bot> botClass) {
        BotRegistration botRegistration = getBotRegistration(botClass);
        String description = "no description";
        if (botRegistration != null)
            description = botRegistration.getDescription();
        Utils.consolePrint("=== " + botClass.getSimpleName() + " ===");
        Utils.consolePrint(description);
        if (isInstantiated(botClass)) {
            Bot botInstance = getInstance(botClass);
            Utils.consolePrint(botInstance.getUsageString());
        } else {
            String abbreviation = "*";
            if (botRegistration != null)
                abbreviation = botRegistration.getAbbreviation();
            Utils.consolePrint("Type \"" + WurmHelper.ConsoleCommand.bot.name() + " " + abbreviation + " " + "on\" to activate the bot");
        }
    }

    public String getBotUsageString() {
        StringBuilder result = new StringBuilder("Usage: " + WurmHelper.ConsoleCommand.bot.name() + " {");
        for (BotRegistration botRegistration : botList)
            result.append(botRegistration.getAbbreviation()).append("|");
        result.append("pause|off}");
        return result.toString();
    }

    private Class<? extends Bot> getBotClass(String abbreviation) {
        for (BotRegistration botRegistration : botList)
            if (botRegistration.getAbbreviation().equals(abbreviation))
                return botRegistration.getBotClass();
        return null;
    }

    public BotRegistration getBotRegistration(Class<? extends Bot> botClass) {
        for (BotRegistration botRegistration : botList) {
            if (botRegistration.getBotClass().equals(botClass))
                return botRegistration;
        }
        return null;
    }
}
