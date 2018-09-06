package net.ildar.wurm;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.client.startup.ServerBrowserDirectConnect;
import com.wurmonline.shared.constants.PlayerAction;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javassist.ClassPool;
import javassist.CtClass;
import net.ildar.wurm.bot.Bot;
import net.ildar.wurm.bot.BulkItemGetterBot;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Mod implements WurmClientMod, Initable {
    public static HeadsUpDisplay hud;
    public static List<WurmComponent> components;

    private static Logger logger;
    private static Map<ConsoleCommand, ConsoleCommandHandler> consoleCommandHandlers;

    static {
        logger = Logger.getLogger("IldarMod");
        consoleCommandHandlers = new HashMap<>();
        consoleCommandHandlers.put(ConsoleCommand.sleep, Mod::handleSleepCommand);
        consoleCommandHandlers.put(ConsoleCommand.look, Mod::handleLookCommand);
        consoleCommandHandlers.put(ConsoleCommand.combine, input -> handleCombineCommand());
        consoleCommandHandlers.put(ConsoleCommand.move, Mod::handleMoveCommand);
        consoleCommandHandlers.put(ConsoleCommand.stabilize, input -> Utils.stabilizePlayer());
        consoleCommandHandlers.put(ConsoleCommand.bot, Mod::configureBot);
        consoleCommandHandlers.put(ConsoleCommand.mts, Mod::handleMtsCommand);
        consoleCommandHandlers.put(ConsoleCommand.info, Mod::handleInfoCommand);
        consoleCommandHandlers.put(ConsoleCommand.iteminfo, input -> printItemInformation());
    }

    /**
     * Handle console commands
     */
    @SuppressWarnings("unused")
    public static boolean handleInput(final String cmd, final String[] data) {
        ConsoleCommand consoleCommand = ConsoleCommand.getByName(cmd);
        if (consoleCommand == ConsoleCommand.unknown)
            return false;
        ConsoleCommandHandler consoleCommandHandler = consoleCommandHandlers.get(consoleCommand);
        if (consoleCommandHandler == null)
            return false;
        try {
            consoleCommandHandler.handle(Arrays.copyOfRange(data, 1, data.length));
        } catch (Exception e) {
            Utils.consolePrint("Error on execution of command \"" + consoleCommand.name() + "\"");
            e.printStackTrace();
        }
        return true;
    }

    private static void printConsoleCommandUsage(ConsoleCommand consoleCommand) {
        if (consoleCommand == ConsoleCommand.look) {
            Utils.consolePrint("Usage: " + ConsoleCommand.look.name() + " {" + getCardinalDirectionsList() + "}");
            return;
        }
        if (consoleCommand == ConsoleCommand.bot) {
            Utils.consolePrint(Bot.getBotUsageString());
            return;
        }
        Utils.consolePrint("Usage: " + consoleCommand.name() + " " + consoleCommand.getUsage());
    }

    private static void handleInfoCommand(String [] input) {
        if (input.length != 1) {
            printConsoleCommandUsage(ConsoleCommand.info);
            printAvailableConsoleCommands();
            return;
        }

        ConsoleCommand command = ConsoleCommand.getByName(input[0]);
        if (command == ConsoleCommand.unknown) {
            Utils.consolePrint("Unknown console command");
            return;
        }
        printConsoleCommandUsage(command);
        Utils.consolePrint(command.description);
    }

    private static void printItemInformation() {
        WurmComponent inventoryComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (inventoryComponent == null) {
            Utils.consolePrint("Didn't find an inventory");
            return;
        }
        InventoryListComponent ilc;
        try {
            ilc = ReflectionUtil.getPrivateField(inventoryComponent,
                    ReflectionUtil.getField(inventoryComponent.getClass(), "component"));
        } catch(Exception e) {
            Utils.consolePrint("Unable to get inventory information");
            return;
        }
        List<InventoryMetaItem> items = Utils.getSelectedItems(ilc);
        if (items == null || items.size() == 0) {
            Utils.consolePrint("No items are selected");
            return;
        }
        for(InventoryMetaItem item : items)
            printItemInfo(item);
    }

    private static void printItemInfo(InventoryMetaItem item) {
        if (item == null) {
            Utils.consolePrint("Null item");
            return;
        }
        Utils.consolePrint("Item - \"" + item.getBaseName() + " with id " + item.getId());
        Utils.consolePrint(" QL:" + String.format("%.2f", item.getQuality()) + " DMG:" + String.format("%.2f", item.getDamage()) + " Weight:" + item.getWeight());
        Utils.consolePrint(" Rarity:" + item.getRarity() + " Color:" + String.format("(%d,%d,%d)", (int)(item.getR()*255), (int)(item.getG()*255), (int)(item.getB()*255)));
        List<InventoryMetaItem> children = item.getChildren();
        int childCound = children!=null?children.size():0;
        Utils.consolePrint(" Improve icon id:" + item.getImproveIconId() + " Child count:" + childCound + " Material id:" + item.getMaterialId());
        Utils.consolePrint(" Aux data:" + item.getAuxData() + " Price:" + item.getPrice() + " Temperature:" + item.getTemperature() + " " + item.getTemperatureStateText());
        Utils.consolePrint(" Custom name:" + item.getCustomName() + " Group name:" + item.getGroupName() + " Display name:" + item.getDisplayName());
        Utils.consolePrint(" Type:" + item.getType() + " Type bits:" + item.getTypeBits() + " Parent id:" + item.getParentId());
        Utils.consolePrint(" Color override:" + item.isColorOverride() + " Marked for update:" + item.isMarkedForUpdate() + " Unfinished:" + item.isUnfinished());
    }

    private static void handleMtsCommand(String []input) {
        if (input.length < 2)  {
            printConsoleCommandUsage(ConsoleCommand.mts);
            return;
        }

        float coefficient = 1;
        if (input.length == 3) {
            try {
                coefficient = Float.parseFloat(input[2]);
            } catch (NumberFormatException e) {
                Utils.consolePrint("Wrong coefficient value. Should be float");
                return;
            }
        }
        float favorLevel;
        try {
            favorLevel = Float.parseFloat(input[1]);
        } catch(NumberFormatException e) {
            Utils.consolePrint("Invalid float level number. Should be float");
            return;
        }
        moveToSacrifice(input[0], favorLevel, coefficient);
    }

    private static void handleMoveCommand(String []input) {
        if (input.length == 1) {
            try {
                float d = Float.parseFloat(input[0]);
                Utils.movePlayer(d);
            } catch (NumberFormatException e) {
                printConsoleCommandUsage(ConsoleCommand.move);
            }
        }
        else
            printConsoleCommandUsage(ConsoleCommand.move);
    }

    private static void handleCombineCommand() {
        long[] itemsToCombine = hud.getInventoryWindow().getInventoryListComponent().getSelectedCommandTargets();
        if (itemsToCombine == null || itemsToCombine.length == 0) {
            Utils.consolePrint("No selected items!");
            return;
        }
        hud.getWorld().getServerConnection().sendAction(itemsToCombine[0], itemsToCombine, PlayerAction.COMBINE);
    }

    private static void handleSleepCommand(String [] input) {
        if (input.length == 1) {
            try {
                Thread.sleep(Long.parseLong(input[0]));
            }catch(InputMismatchException e) {
                Utils.consolePrint("Bad value");
            }catch(InterruptedException e) {
                Utils.consolePrint("Interrupted");
            }
        } else printConsoleCommandUsage(ConsoleCommand.sleep);
    }

    private static void handleLookCommand(String []input) {
        if (input.length == 1) {
            CardinalDirection direction = CardinalDirection.getByName(input[0]);
            if (direction == CardinalDirection.unknown) {
                Utils.consolePrint("Unknown direction: " + input[0]);
                return;
            }
            try {
                Utils.turnPlayer(direction.angle,0);
            } catch (Exception e) {
                Utils.consolePrint("Can't change looking direction");
            }
        } else
            printConsoleCommandUsage(ConsoleCommand.look);
    }

    private static String getCardinalDirectionsList() {
        StringBuilder directions = new StringBuilder();
        for(CardinalDirection direction : CardinalDirection.values())
            directions.append(direction.name()).append("|");
        directions.deleteCharAt(directions.length() - 1);
        return directions.toString();
    }

    private static void printAvailableConsoleCommands() {
        StringBuilder commands = new StringBuilder();
        for(ConsoleCommand consoleCommand : consoleCommandHandlers.keySet())
            commands.append(consoleCommand.name()).append(", ");
        commands.deleteCharAt(commands.length() - 1);
        commands.deleteCharAt(commands.length() - 1);
        Utils.consolePrint("Available custom commands - " + commands.toString());
    }

    //move items to altar for a sacrifice
    private static void moveToSacrifice(String itemName, float favorLevel, float coefficient) {
        WurmComponent inventoryComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (inventoryComponent == null) {
            Utils.consolePrint("Didn't find an opened altar");
            return;
        }
        InventoryListComponent ilc;
        try {
            ilc = ReflectionUtil.getPrivateField(inventoryComponent,
                    ReflectionUtil.getField(inventoryComponent.getClass(), "component"));
        } catch(Exception e) {
            e.printStackTrace();
            return;
        }
        List<InventoryMetaItem> items = Utils.getInventoryItems(ilc, itemName);
        if (items == null || items.size() == 0) {
            Utils.consolePrint("No items");
            return;
        }
        List<InventoryMetaItem> itemsToMove = new ArrayList<>();
        float favor = Mod.hud.getWorld().getPlayer().getSkillSet().getSkillValue("favor");
        for (InventoryMetaItem item : items) {
            if (favor >= favorLevel) break;
            itemsToMove.add(item);
            favor += Utils.itemFavor(item, coefficient);
        }
        if (itemsToMove.size() == 0){
            Utils.consolePrint("No items to move");
            return;
        }
        if (components == null) {
            Utils.consolePrint("Components list is empty!");
            return;
        }
        for(WurmComponent component : components) {
            if (component instanceof ItemListWindow){
                try {
                    ilc = ReflectionUtil.getPrivateField(component,
                            ReflectionUtil.getField(component.getClass(), "component"));
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                InventoryMetaItem rootItem = Utils.getRootItem(ilc);
                if (rootItem == null) {
                    Utils.consolePrint("Internal error on moving items");
                    return;
                }
                if (!rootItem.getBaseName().contains("altar of")) continue;
                Mod.hud.getWorld().getServerConnection().sendMoveSomeItems(rootItem.getId(), Utils.getItemIds(itemsToMove));
                Mod.hud.sendAction(PlayerAction.SACRIFICE, rootItem.getId());
                return;
            }
        }
        Utils.consolePrint("Didn't find an opened altar");
    }

    private static void configureBot(String data[]) {
        String usageString = Bot.getBotUsageString();

        if (data.length < 1) {
            Utils.consolePrint(usageString);
            writeToConsoleInputLine(ConsoleCommand.bot.name() + " ");
            return;
        }
        if (data[0].equals("off")) {
            Bot.deactivateAllBots();
            return;
        }
        Class<? extends Bot> botClass = Bot.getBotClass(data[0]);
        if (botClass == null) {
            Utils.consolePrint("Didn't find a bot with abbreviation \"" + data[0] + "\"");
            Utils.consolePrint(usageString);
            return;
        }

        if (data.length == 1) {
            Bot.printBotDescription(botClass);
            writeToConsoleInputLine(ConsoleCommand.bot.name() + " " + data[0] + " ");
            return;
        }

        if (Bot.isInstantiated(botClass)) {
            if (data[1].equals("on")) {
                Utils.consolePrint(botClass.getSimpleName() + " is already on");
            } else {
                try {
                    Bot botInstance = Bot.getInstance(botClass);
                    botInstance.handleInput(Arrays.copyOfRange(data, 1, data.length));
                } catch (Exception e) {
                    Utils.consolePrint("Unable to configure  " + botClass.getSimpleName());
                    e.printStackTrace();
                }
            }
        } else {
            if (data[1].equals("on")) {
                Bot botInstance = Bot.getInstance(botClass);
                if (botInstance != null) {
                    botInstance.start();
                    Utils.consolePrint(botClass.getSimpleName() + " is on!");
                    Bot.printBotDescription(botClass);
                } else {
                    Utils.consolePrint("Internal error on bot activation");
                }
            } else {
                Utils.consolePrint(botClass.getSimpleName() + " is not running!");
            }
        }
        writeToConsoleInputLine(ConsoleCommand.bot.name() + " " + data[0] + " ");
    }

    private static void writeToConsoleInputLine(String s) {
        try {
            Object consoleComponent = ReflectionUtil.getPrivateField(hud, ReflectionUtil.getField(hud.getClass(), "consoleComponent"));
            Object inputField = ReflectionUtil.getPrivateField(consoleComponent, ReflectionUtil.getField(consoleComponent.getClass(), "inputField"));
            Method method = inputField.getClass().getDeclaredMethod("setTextMoveToEnd", String.class);
            method.setAccessible(true);
            method.invoke(inputField, s);
        } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void init() {
        try {
            final ClassPool classPool = HookManager.getInstance().getClassPool();
            final CtClass ctWurmConsole = classPool.getCtClass("com.wurmonline.client.console.WurmConsole");
            ctWurmConsole.getMethod("handleDevInput", "(Ljava/lang/String;[Ljava/lang/String;)Z").insertBefore("if (net.ildar.wurm.Mod.handleInput($1,$2)) return true;");
            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "init", "(II)V", () -> (proxy, method, args) -> {
                method.invoke(proxy, args);
                Mod.hud = (HeadsUpDisplay)proxy;
                return null;
            });

            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "addComponent", "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)Z", () -> (proxy, method, args) -> {
                WurmComponent wc = (WurmComponent)args[0];
                boolean notadd = false;
                if (BulkItemGetterBot.closeBMLWindow && wc instanceof BmlWindowComponent) {
                    String title = ReflectionUtil.getPrivateField(wc, ReflectionUtil.getField(wc.getClass(), "title"));
                    if (title.equals("Removing items")) {
                        Method clickButton = ReflectionUtil.getMethod(wc.getClass(), "processButtonPressed");
                        clickButton.setAccessible(true);
                        clickButton.invoke(wc, "submit");
                        notadd = true;
                        BulkItemGetterBot.closeBMLWindow = false;
                    }
                }
                if (!notadd) {
                    Object o = method.invoke(proxy, args);
                    components = new ArrayList<>(ReflectionUtil.getPrivateField(proxy, ReflectionUtil.getField(proxy.getClass(), "components")));
                    return o;
                }
                return (Object)true;
            });
            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "setActiveWindow", "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)V", () -> (proxy, method, args) -> {
                method.invoke(proxy, args);
                components = new ArrayList<>(ReflectionUtil.getPrivateField(proxy, ReflectionUtil.getField(proxy.getClass(), "components")));
                return null;
            });

            final CtClass ctSocketConnection = classPool.getCtClass("com.wurmonline.communication.SocketConnection");
            ctSocketConnection.getMethod("tickWriting", "(J)Z").insertBefore("net.ildar.wurm.Utils.blockingQueue.take();");
            ctSocketConnection.getMethod("tickWriting", "(J)Z").insertAfter("net.ildar.wurm.Utils.blockingQueue.put(new Integer(1));");
            ctSocketConnection.getMethod("getBuffer", "()Ljava/nio/ByteBuffer;").insertBefore("net.ildar.wurm.Utils.blockingQueue.take();");
            ctSocketConnection.getMethod("flush", "()V").insertAfter("net.ildar.wurm.Utils.blockingQueue.put(new Integer(1));");
            
            final CtClass ctGroundItemCellRenderable = classPool.getCtClass("com.wurmonline.client.renderer.cell.GroundItemCellRenderable");
            ctGroundItemCellRenderable.getMethod("initialize", "()V").insertBefore("net.ildar.wurm.bot.GroundItemGetterBot.processNewItem($0);");

            final CtClass ctWurmChat = classPool.getCtClass("com.wurmonline.client.renderer.gui.ChatPanelComponent");
            ctWurmChat.getMethod("addText", "(Ljava/lang/String;Ljava/util/List;Z)V").insertBefore("net.ildar.wurm.Chat.onMessage($1,$2,$3);");
            ctWurmChat.getMethod("addText", "(Ljava/lang/String;Ljava/lang/String;FFFZ)V").insertBefore("net.ildar.wurm.Chat.onMessage($1,$2,$6);");


            HookManager.getInstance().registerHook("com.wurmonline.client.startup.ServerBrowserDirectConnect", "loadOptions", "()V", () -> (proxy, method, args) -> {
                method.invoke(proxy, args);
                Properties properties = new Properties();
                properties.load(new FileInputStream("autorun.properties"));
                String defaultPassword = properties.getProperty("defaultPassword");
                String defaultIp= properties.getProperty("defaultIp");
                String defaultPort= properties.getProperty("defaultPort");
                ServerBrowserDirectConnect serverBrowserDirectConnect = (ServerBrowserDirectConnect) proxy;
                PasswordField passwordField = ReflectionUtil.getPrivateField(serverBrowserDirectConnect,
                        ReflectionUtil.getField(ServerBrowserDirectConnect.class, "passwordField"));
                TextField ipAddressField = ReflectionUtil.getPrivateField(serverBrowserDirectConnect,
                        ReflectionUtil.getField(ServerBrowserDirectConnect.class, "ipAddressField"));
                TextField portField = ReflectionUtil.getPrivateField(serverBrowserDirectConnect,
                        ReflectionUtil.getField(ServerBrowserDirectConnect.class, "portField"));
                if(defaultIp != null)
                    ipAddressField.setText(defaultIp);
                if(defaultPassword != null)
                    passwordField.setText(defaultPassword);
                if(defaultPort != null)
                    portField.setText(defaultPort);
                return null;
            });

            Chat.registerEventProcessor(message -> message.contains("You fail to relax"), () -> {
                try {
                    PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                            ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
                    if (pickableUnit != null)
                        Mod.hud.sendAction(new PlayerAction((short) 384, 65535), pickableUnit.getId());
                } catch (Exception e) {
                    Utils.consolePrint("Got exception at the start of meditation " + e.getMessage());
                    Utils.consolePrint(e.toString());
                }
            });

            logger.info("Loaded");
        }
        catch (Exception e) {
            if (Mod.logger != null) {
                Mod.logger.log(Level.SEVERE, "Error loading mod", e);
                Mod.logger.log(Level.SEVERE, e.toString());
            }
        }
    }

    public enum ConsoleCommand{
        unknown("", ""),
        sleep("timeout(milliseconds)", "Freezes the game for specified time."),
        look("{north|east|south|west}", "Precisely turns player in chosen direction."),
        combine("", "Combines selected items in your inventory."),
        move("(float)distance", "Moves your character in current direction for specified distance(in meters, 1 tile has 4 meters on each side)."),
        stabilize("", "Moves your character to the very center of the tile + turns the sight towards nearest cardinal direction."),
        bot("abbreviation", "Activates/configures the bot with provided abbreviation."),
        mts("item_name favor_level [coefficient]",
                "Move specified items to opened altar inventory. " +
                "The amount of moved items depends on specified favor(with coefficient) you want to get from these items when you sacrifice them."),
        info("command", "Shows the description of specified console command."),
        iteminfo("", "Prints information about selected items under mouse cursor.");

        private String usage;
        public String description;

        ConsoleCommand(String usage, String description) {
            this.usage = usage;
            this.description = description;
        }

        String getUsage() {
            return usage;
        }

        static ConsoleCommand getByName(String name) {
            try {
                return Enum.valueOf(ConsoleCommand.class, name);
            } catch(Exception e) {
                return ConsoleCommand.unknown;
            }
        }
    }

    interface ConsoleCommandHandler {
        void handle(String []input);
    }

    @SuppressWarnings("unused")
    private enum CardinalDirection {
        unknown(0),
        north(0),
        east(90),
        south(180),
        west(270);

        int angle;
        CardinalDirection(int angle) {
            this.angle = angle;
        }

        static CardinalDirection getByName(String name) {
            try {
                return Enum.valueOf(CardinalDirection.class, name);
            } catch(Exception e) {
                return CardinalDirection.unknown;
            }
        }
    }
}
