package net.ildar.wurm.bot;

import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;

import java.util.ArrayList;
import java.util.List;

public class BulkItemGetterBot extends Bot {
    public static boolean closeBMLWindow;
    private List<SourceItem> sources = new ArrayList<>();
    private List<Long> targets = new ArrayList<>();

    public static BotRegistration getRegistration() {
        return new BotRegistration(BulkItemGetterBot.class,
                "Automatically transfers items to player's inventory from configured bulk storages. " +
                        "The n-th  source item will be transferred to the n-th target item",
                "big");
    }

    @Override
    public void work() throws Exception{
        closeBMLWindow = false;
        setTimeout(15000);
        registerEventProcessor(message -> message.contains("That item is already busy"),
                () -> closeBMLWindow = false);
        while (isActive()) {
            waitOnPause();
            if (sources.size() > 0 && targets.size() > 0) {
                int moves = Math.min(sources.size(), targets.size());
                for(int i = 0; i < moves; i++) {
                    SourceItem sourceItem = sources.get(i);
                    if (sourceItem.fixedPoint) {
                        long[] items = Mod.hud.getCommandTargetsFrom(sourceItem.x, sourceItem.y);
                        if (items != null && items.length > 0) {
                            sourceItem.id = items[0];
                        } else {
                            Utils.consolePrint("Can't get an item from point (" + sourceItem.x + ", " + sourceItem.y + ")");
                            continue;
                        }
                    }
                    closeBMLWindow = true;
                    //Utils.consolePrint(i + " - moving " + sources.get(i) + " to " + targets.get(i));
                    Mod.hud.getWorld().getServerConnection().sendMoveSomeItems(targets.get(i), new long[]{sourceItem.id});
                    int counter = 0;
                    while(closeBMLWindow && counter++ < 50)
                        sleep(100);
                }
                sleep(timeout);
            }
            else
                sleep(1000);
        }
    }

    public BulkItemGetterBot() {
        registerInputHandler(BulkItemGetterBot.InputKey.as, input -> addSource());
        registerInputHandler(BulkItemGetterBot.InputKey.at, input -> addTarget());
        registerInputHandler(BulkItemGetterBot.InputKey.asid, this::addSourceById);
        registerInputHandler(BulkItemGetterBot.InputKey.atid, this::addTargetById);
        registerInputHandler(BulkItemGetterBot.InputKey.ssxy, input -> addFixedPointSource());
    }

    private void addSourceById(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(BulkItemGetterBot.InputKey.asid);
            return;
        }
        try {
            SourceItem sourceItem = new SourceItem();
            sourceItem.id = Long.parseLong(input[0]);
            sources.add(sourceItem);
            Utils.consolePrint("New source is added with id " + sourceItem.id);
        } catch (Exception e) {
            Utils.consolePrint("Can't set source item");
        }
    }

    private void addTargetById(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(BulkItemGetterBot.InputKey.atid);
            return;
        }
        try {
            long id = Long.parseLong(input[0]);
            targets.add(id);
            Utils.consolePrint("New target is added with id " + id);
        } catch (Exception e) {
            Utils.consolePrint("Can't set target item");
        }
    }

    private void addSource() {
        try {
            int x = Mod.hud.getWorld().getClient().getXMouse();
            int y = Mod.hud.getWorld().getClient().getYMouse();
            long [] items = Mod.hud.getCommandTargetsFrom(x, y);
            if (items == null || items.length == 0)
                Utils.consolePrint(this.getClass().getSimpleName() + " is unable to set a source item");
            else {
                SourceItem sourceItem = new SourceItem();
                sourceItem.id = items[0];
                sources.add(sourceItem);
                Utils.consolePrint("Added new source item with id " + sourceItem.id);
            }

        } catch (Exception e) {
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error  while setting source - " + e.getMessage());
            Utils.consolePrint( e.toString());
        }
    }

    private void addTarget() {
        try {
            int x = Mod.hud.getWorld().getClient().getXMouse();
            int y = Mod.hud.getWorld().getClient().getYMouse();
            long []target = Mod.hud.getCommandTargetsFrom(x,y);
            if (target != null && target.length > 0) {
                targets.add(target[0]);
                Utils.consolePrint("New target is " + target[0]);
            } else
                Utils.consolePrint("Couldn't find the target for " + this.getClass().getSimpleName());
        }catch (Exception e) {
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error while setting target - " + e.getMessage());
            Utils.consolePrint( e.toString());
        }
    }

    private void addFixedPointSource() {
        int x = Mod.hud.getWorld().getClient().getXMouse();
        int y = Mod.hud.getWorld().getClient().getYMouse();
        SourceItem sourceItem = new SourceItem();
        sourceItem.x = x;
        sourceItem.y = y;
        sourceItem.fixedPoint = true;
        sources.add(sourceItem);
        Utils.consolePrint("Added new source from the point (" + sourceItem.x + ", " + sourceItem.y + ")");
    }

    private enum InputKey implements Bot.InputKey {
        as("Add the source(item in bulk storage) the user is currenly pointing to", ""),
        at("Add the target item the user is currently pointing to", ""),
        asid("Add the source(item in bulk storage) with provided id", "id"),
        atid("Add the target item with provided id", "id"),
        ssxy("Add source item from fixed point on screen", ""),;
        private String description;
        private String usage;
        InputKey(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }

        @Override
        public String getName() {
            return name();
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getUsage() {
            return usage;
        }
    }

    static class SourceItem {
        long id;
        int x;
        int y;
        boolean fixedPoint = false;
    }
}