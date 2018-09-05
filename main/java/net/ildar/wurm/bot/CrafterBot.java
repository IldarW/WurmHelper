package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CrafterBot extends Bot {
    private float staminaThreshold;
    private boolean repairInstrument;
    private String targetName;
    private String sourceName;
    private Comparator<InventoryMetaItem> weightComparator = Comparator.comparingDouble(InventoryMetaItem::getWeight);
    private int targetX;
    private int targetY;
    private int sourceX;
    private int sourceY;
    private boolean noSort;
    private boolean combineTargets;
    private boolean combineSources;
    private long combineTimeout;
    private boolean craftUnfinishedItemMode;
    private boolean withoutActionsInUse;
    private long lastClick;

    public CrafterBot() {
        registerInputHandler(InputKey.r, input -> toggleRepairInstrument());
        registerInputHandler(InputKey.st, this::handleTargetNameChange);
        registerInputHandler(InputKey.stxy, input -> setTargetXY());
        registerInputHandler(InputKey.ss, this::handleSourceNameChange);
        registerInputHandler(InputKey.ssxy, input -> setSourceXY());
        registerInputHandler(InputKey.nosort, input -> toggleSorting());
        registerInputHandler(InputKey.ct, input -> toggleTargetsCombining());
        registerInputHandler(InputKey.cs, input -> toggleSourcesCombining());
        registerInputHandler(InputKey.ctimeout, this::handleCombineTimeoutChange);
        registerInputHandler(InputKey.s, this::handleStaminaThresholdChange);
        registerInputHandler(InputKey.u, input -> toggleUnfinishedMode());
        registerInputHandler(InputKey.ssid, this::handleSourceItemIdChange);
        registerInputHandler(InputKey.an, this::handleActionNumberChange);
        registerInputHandler(InputKey.noan, input -> toggleActionNumberChecks());
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public void work() throws Exception{
        setStaminaThreshold(0.96f);
        long lastCombineTime = System.currentTimeMillis();

        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Method sendCreateAction = ReflectionUtil.getMethod(CreationWindow.class, "sendCreateAction");
        sendCreateAction.setAccessible(true);
        Method requestCreationList = ReflectionUtil.getMethod(creationWindow.getClass(), "requestCreationList");
        requestCreationList.setAccessible(true);
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow,
                ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        CreationFrame source = ReflectionUtil.getPrivateField(creationWindow,
                ReflectionUtil.getField(creationWindow.getClass(), "source"));
        CreationFrame target = ReflectionUtil.getPrivateField(creationWindow,
                ReflectionUtil.getField(creationWindow.getClass(), "target"));

        while (isActive()) {
            float stamina = Mod.hud.getWorld().getPlayer().getStamina();
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            float progress = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "progress"));

            if (repairInstrument) {
                @SuppressWarnings("unchecked")
                List<InventoryMetaItem> sourceItems = new ArrayList(ReflectionUtil.getPrivateField(source,
                        ReflectionUtil.getField(source.getClass(), "itemList")));
                if (sourceItems != null && sourceItems.size() > 0 && sourceItems.get(0).getDamage() > 10)
                    Mod.hud.sendAction(PlayerAction.REPAIR, sourceItems.get(0).getId());
            }

            if (craftUnfinishedItemMode) {
                WurmTreeList<CreationItemTreeLisItem> unfinishedItemList = ReflectionUtil.getPrivateField(creationWindow,
                        ReflectionUtil.getField(creationWindow.getClass(), "unfinishedItemList"));
                if (unfinishedItemList != null) {
                    List lines = ReflectionUtil.getPrivateField(unfinishedItemList,
                            ReflectionUtil.getField(unfinishedItemList.getClass(), "lines"));
                    if (lines != null) {
                        targetName = null;
                        //noinspection ForLoopReplaceableByForEach
                        for (int i = 0; i < lines.size(); i++) {
                            CreationItemTreeLisItem listItem = ReflectionUtil.getPrivateField(lines.get(i),
                                    ReflectionUtil.getField(lines.get(i).getClass(), "item"));
                            String chance = ReflectionUtil.getPrivateField(listItem,
                                    ReflectionUtil.getField(listItem.getClass(), "chance"));
                            if (chance != null && !chance.equals("") && !chance.contains("%")) {
                                targetName = ReflectionUtil.getPrivateField(listItem,
                                        ReflectionUtil.getField(listItem.getClass(), "name"));
                                break;
                            }
                        }
                    } else {
                        requestCreationList.invoke(creationWindow);
                    }
                }
            }
            if (targetName != null && targetName.length() > 0) {
                List<InventoryMetaItem> targetItems = Utils.getInventoryItems(targetName);
                if (!noSort)
                    targetItems.sort(weightComparator);
                ReflectionUtil.setPrivateField(target,
                        ReflectionUtil.getField(target.getClass(), "itemList"), targetItems);
            }
            if (sourceName != null && sourceName.length() > 0) {
                List<InventoryMetaItem> sourceItems = Utils.getInventoryItems(sourceName);
                if (!noSort)
                    sourceItems.sort(weightComparator);
                ReflectionUtil.setPrivateField(source,
                        ReflectionUtil.getField(source.getClass(), "itemList"), sourceItems);
            }

            if (targetX != 0 && targetY != 0) {
                List<InventoryMetaItem> items = Utils.getInventoryItemsAtPoint(targetX, targetY);
                if (items != null && items.size() > 0)
                    ReflectionUtil.setPrivateField(target,
                            ReflectionUtil.getField(target.getClass(), "itemList"), items);
            }

            if (sourceX != 0 && sourceY != 0) {
                List<InventoryMetaItem> items = Utils.getInventoryItemsAtPoint(sourceX, sourceY);
                if (items != null && items.size() > 0)
                    ReflectionUtil.setPrivateField(source,
                            ReflectionUtil.getField(source.getClass(), "itemList"), items);
            }

            if (combineTargets && (Math.abs(lastCombineTime - System.currentTimeMillis()) > combineTimeout)) {
                lastCombineTime = System.currentTimeMillis();
                List<InventoryMetaItem> targetItems = ReflectionUtil.getPrivateField(target,
                        ReflectionUtil.getField(target.getClass(), "itemList"));
                long[] targets = new long[targetItems.size()];
                for (int i = 0; i < targetItems.size(); i++)
                    targets[i] = targetItems.get(i).getId();
                creationWindow.sendCombineAction(targets[0], targets, target);
            }

            if (source != null && target != null && (stamina+damage) > staminaThreshold && (creationWindow.getActionInUse() == 0 || withoutActionsInUse) && progress == 0f) {
                sendCreateAction.invoke(creationWindow);
                lastClick = System.currentTimeMillis();
            }
            if (source != null && target != null
                    && (stamina+damage) > staminaThreshold
                    && Math.abs(lastClick - System.currentTimeMillis()) > 20000){
                requestCreationList.invoke(creationWindow);
                while (creationWindow.getActionInUse() > 0)
                    creationWindow.decreaseActionInUse();
                lastClick = System.currentTimeMillis();
            }
            sleep(timeout);
        }
    }

    private void toggleActionNumberChecks() {
        withoutActionsInUse = !withoutActionsInUse;
        if (!withoutActionsInUse) {
            Utils.consolePrint(this.getClass().getSimpleName() + " will NOT check action queue");
        } else {
            Utils.consolePrint(this.getClass().getSimpleName() + " will check action queue");

        }
    }

    private void handleActionNumberChange(String input[]) {
        if(input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.an);
            return;
        }

        try {
            int num = Integer.parseInt(input[0]);
            CreationWindow creationWindow = Mod.hud.getCreationWindow();
            ReflectionUtil.setPrivateField(creationWindow,
                    ReflectionUtil.getField(creationWindow.getClass(), "selectedActions"), num);
        } catch (Exception e) {
            Utils.consolePrint("Can't set an action number");
        }
    }

    private void handleSourceItemIdChange(String input[]) {
        if(input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.ssid);
            return;
        }
        try {
            long id = Long.parseLong(input[0]);
            InventoryListComponent ilc = Mod.hud.getInventoryWindow().getInventoryListComponent();
            List <InventoryMetaItem> allItems = Utils.getSelectedItems(ilc, true, true);
            @SuppressWarnings("ConstantConditions")
            InventoryMetaItem sourceItem = allItems.stream().filter(item->item.getId() == id).findAny().get();
            CreationWindow creationWindow = Mod.hud.getCreationWindow();
            CreationFrame source = ReflectionUtil.getPrivateField(creationWindow,
                    ReflectionUtil.getField(creationWindow.getClass(), "source"));
            List<InventoryMetaItem> newSourceList = new ArrayList<>();
            newSourceList.add(sourceItem);
            ReflectionUtil.setPrivateField(source,
                    ReflectionUtil.getField(source.getClass(), "itemList"), newSourceList);
            Method requestCreationList = ReflectionUtil.getMethod(creationWindow.getClass(), "requestCreationList");
            requestCreationList.setAccessible(true);
            requestCreationList.invoke(creationWindow);

        } catch (Exception e) {
            Utils.consolePrint("Can't set new source item with provided iconId");
        }
    }

    private void toggleUnfinishedMode() {
        if (!craftUnfinishedItemMode) {
            targetName = null;
            targetX = targetY = 0;
            craftUnfinishedItemMode = true;
            Utils.consolePrint("The unfinished item crafting mode is on!");
        } else {
            craftUnfinishedItemMode = false;
            Utils.consolePrint("The unfinished item crafting mode is off!");
        }
    }

    private void handleStaminaThresholdChange(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(InputKey.s);
        else {
            try {
                float threshold = Float.parseFloat(input[0]);
                setStaminaThreshold(threshold);
            } catch (Exception e) {
                Utils.consolePrint("Wrong threshold value!");
            }
        }
    }

    private void setStaminaThreshold(float s) {
        staminaThreshold = s;
        Utils.consolePrint("Current threshold for stamina is " + staminaThreshold);
    }

    private void handleCombineTimeoutChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.ctimeout);
            return;
        }
        try {
            setCombineTimeout(Long.parseLong(input[0]));
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong timeout value");
        }
    }

    private void setCombineTimeout(long timeout) {
        this.combineTimeout = timeout;
        Utils.consolePrint("Timeout for item combining is " + combineTimeout);
    }

    private void toggleSourcesCombining() {
        combineSources = !combineSources;
        if (combineSources) {
            Utils.consolePrint("Source combining is on!");
            if (combineTimeout == 0)
                setCombineTimeout(10000);
        } else
            Utils.consolePrint("Source combining is off!");
    }

    private void toggleTargetsCombining() {
        combineTargets = !combineTargets;
        if (combineTargets) {
            Utils.consolePrint("Target combining is on!");
            if (combineTimeout == 0)
                setCombineTimeout(10000);
        } else
            Utils.consolePrint("Target combining is off!");
    }

    private void toggleSorting() {
        noSort = !noSort;
        if (noSort)
            Utils.consolePrint(this.getClass().getSimpleName() + " will NOT sort the targets and sources by weight");
        else
            Utils.consolePrint(this.getClass().getSimpleName() + " will sort the targets and sources by weight");
    }

    private void handleTargetNameChange(String input[]) {
        if(input == null || input.length == 0) {
            printInputKeyUsageString(InputKey.st);
            return;
        }
        StringBuilder target = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++)
            target.append(" ").append(input[i]);
        setTargetName(target.toString());
    }

    private void handleSourceNameChange(String input[]) {
        if(input == null || input.length == 0) {
            printInputKeyUsageString(InputKey.ss);
            return;
        }
        StringBuilder source = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++)
            source.append(" ").append(input[i]);
        setSourceName(source.toString());
    }

    private void toggleRepairInstrument(){
        repairInstrument = !repairInstrument;
        if (repairInstrument)
            Utils.consolePrint("Instrument auto repairing is on!");
        else
            Utils.consolePrint("Instrument auto repairing is off!");
    }

    private void setTargetName(String t) {
        if (t != null && t.length() > 0) {
            Utils.consolePrint("New target item name is - " + t);
            targetName = t;
        } else
            Utils.consolePrint("Can't set empty target item name");
    }

    private void setSourceName(String t) {
        if (t != null && t.length() > 0) {
            Utils.consolePrint("New source item name is - " + t);
            sourceName = t;
        } else
            Utils.consolePrint("Can't set empty source item name");
    }

    private void setTargetXY() {
        targetX = Mod.hud.getWorld().getClient().getXMouse();
        targetY = Mod.hud.getWorld().getClient().getYMouse();
        targetName = null;
        Utils.consolePrint("The target was set to X - " + targetX + " Y - " + targetY);
    }

    private void setSourceXY() {
        sourceX = Mod.hud.getWorld().getClient().getXMouse();
        sourceY = Mod.hud.getWorld().getClient().getYMouse();
        sourceName = null;
        Utils.consolePrint("The source was set to X - " + sourceX + " Y - " + sourceY);
    }

    private enum InputKey {
        r("Toggle the source item repairing(on the left side of crafting window). " +
                "Usually it is an instrument. When the source item gets 10% damage player will repair it automatically", ""),
        st("Set the target item name. " + CrafterBot.class.getSimpleName()+ " will place item with provided name from your inventory to the target slot(on the right side of crafting window)",
                "target_name"),
        stxy("Set the target item fixed point. " + CrafterBot.class.getSimpleName()+ " will place item from that fixed point of screen to the target item slot(on the right side of crafting window)", ""),
        ss("Set the source item name. " + CrafterBot.class.getSimpleName()+ " will place item with provided name from your inventory to the source slot(on the left side of crafting window)",
                "source_name"),
        ssxy("Set the source item fixed point. " + CrafterBot.class.getSimpleName()+ " will place item from that fixed point of screen to the source item slot(on the left side of crafting window)", ""),
        nosort("Sorting of source and target items is enabled by default. This key toggles sorting on and off", ""),
        cs("Combine source items(on the left side of crafting window)", ""),
        ct("Combine target items(on the right side of crafting window)", ""),
        ctimeout("Set the timeout for item combining", "timeout(in milliseconds)"),
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        u("Toggle the special mode in which " + CrafterBot.class.getSimpleName() + " will place an item to the target item slot which is at the top of \"Needed items\" list", ""),
        ssid("Set an item with provided iconId to the source slot(on the left side of crafting window)", "iconId"),
        an("Set an action number. The number of crafting operations the player will do on each click on continue/create button", "number"),
        noan("Toggles the check for action queue state before the start of each crafting operation. " +
                "By default " + CrafterBot.class.getSimpleName() + " will check action queue and start crafting operations only when it is empty", "");

        public String description;
        public String usage;
        InputKey(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }
    }
}
