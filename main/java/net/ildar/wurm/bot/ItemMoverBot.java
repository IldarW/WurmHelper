package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.*;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.*;

public class ItemMoverBot extends Bot {
    private Set <String> itemNames;
    private TargetType targetType;
    private Map <String, Float> itemMaximumWeights;
    private long target;
    private InventoryListComponent targetComponent;
    private String containerName;
    private int containerVolume = 100;
    private boolean notMoveRares = true;
    private String lastItemName;
    private boolean onlyFirstLevelItems = true;

    public static BotRegistration getRegistration() {
        return new BotRegistration(ItemMoverBot.class,
                "Moves items from your inventory to the target destination.", "im");
    }

    @Override
    public void work() throws Exception{
        setTimeout(15000);
        while (isActive()) {
            waitOnPause();
            if (itemNames != null && itemNames.size() > 0 && (target != 0 || targetComponent != null)) {
                List<InventoryMetaItem> invItems;
                if (onlyFirstLevelItems)
                    invItems = Utils.getFirstLevelItems();
                else
                    invItems = Utils.getSelectedItems(Mod.hud.getInventoryWindow().getInventoryListComponent(), true, true);
                List<InventoryMetaItem> itemsToMove = new ArrayList<>();
                for (InventoryMetaItem invItem : invItems) {
                    boolean notRare = invItem.getRarity() == 0;
                    for (String itemName : itemNames) {
                        float maxWeight = itemMaximumWeights.get(itemName);
                        if (invItem.getBaseName().contains(itemName)
                                && (maxWeight == 0 || invItem.getWeight() <= maxWeight)
                                && (!notMoveRares || notRare))
                            itemsToMove.add(invItem);
                    }
                }
                if (itemsToMove.size() > 0) {
                    long [] sources = Utils.getItemIds(itemsToMove);
                    switch (targetType) {
                        case Item:
                            Mod.hud.getWorld().getServerConnection().sendMoveSomeItems(target, sources);
                            break;
                        case ContainerRoot:
                            InventoryMetaItem rootItem = Utils.getRootItem(targetComponent);
                            if (rootItem != null)
                                Mod.hud.getWorld().getServerConnection().sendMoveSomeItems(rootItem.getId(), sources);
                            else
                                Utils.consolePrint("Unable to move items to the target container");
                            break;
                        case Containers:
                            List<InventoryMetaItem> containers = Utils.getInventoryItems(targetComponent, containerName);
                            if (containers != null && containers.size() > 0) {
                                for (InventoryMetaItem container : containers)
                                    if (container.getChildren().size() < containerVolume) {
                                        int quantityToMove = Math.min(containerVolume - container.getChildren().size(), sources.length);
                                        Mod.hud.getWorld().getServerConnection().sendMoveSomeItems(
                                                container.getId(), Arrays.copyOfRange(sources, 0, quantityToMove));
                                        sources = Arrays.copyOfRange(sources, quantityToMove, sources.length);
                                        if (sources.length == 0)
                                            break;
                                    }
                                if (sources.length > 0)
                                    Utils.consolePrint("All containers are full!");
                            } else
                                Utils.consolePrint("Didn't find any \"" + containerName + "\" containers inside target container");
                            break;
                    }
                }
                sleep(timeout);
            } else
                sleep(1000);
        }
    }

    public ItemMoverBot() {
        registerInputHandler(ItemMoverBot.InputKey.clear, input -> clearItemList());
        registerInputHandler(ItemMoverBot.InputKey.st, input -> setTargetItem());
        registerInputHandler(ItemMoverBot.InputKey.stid, this::setTargetById);
        registerInputHandler(ItemMoverBot.InputKey.str, input -> setTargetContainerRoot());
        registerInputHandler(ItemMoverBot.InputKey.stc, this::setTargetAsContainer);
        registerInputHandler(ItemMoverBot.InputKey.stcn, this::setTargetContainerVolume);
        registerInputHandler(ItemMoverBot.InputKey.a, this::addNewItemName);
        registerInputHandler(ItemMoverBot.InputKey.sw, this::setMaximumItemWeight);
        registerInputHandler(ItemMoverBot.InputKey.r, input -> toggleRareItemsMoving());
        registerInputHandler(ItemMoverBot.InputKey.fl, input -> toggleFirstLevelItemMoving());
    }

    private void toggleFirstLevelItemMoving() {
        onlyFirstLevelItems = !onlyFirstLevelItems;
        if (onlyFirstLevelItems)
            Utils.consolePrint("Only first level items of your inventory can be moved");
        else
            Utils.consolePrint("All items from your inventory that match added keywords can be moved");
    }

    private void setTargetById(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ItemMoverBot.InputKey.stid);
            return;
        }
        try {
            target = Long.parseLong(input[0]);
            targetType = TargetType.Item;
            Utils.consolePrint("New target is " + target);
        } catch (Exception e) {
            Utils.consolePrint("Can't set target item with provided id");
        }
    }

    private void toggleRareItemsMoving() {
        notMoveRares = !notMoveRares;
        if (notMoveRares)
            Utils.consolePrint("Rare+ items will not be moved");
        else
            Utils.consolePrint("Rare+ items will be moved too");
    }

    private void setMaximumItemWeight(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ItemMoverBot.InputKey.sw);
            return;
        }

        if (lastItemName != null) {
            if (itemMaximumWeights == null) {
                Utils.consolePrint("Internal error. Item weights not initialized.");
                return;
            }
            try {
                itemMaximumWeights.put(lastItemName, Float.parseFloat(input[0]));
                Utils.consolePrint("Item - " + lastItemName + " will  be moved only with weight below " + input[0]);
            } catch (NumberFormatException e) {
                Utils.consolePrint("Wrong item weight value!");
            }
        }
        else {
            Utils.consolePrint("Add an item first!");
        }
    }

    private void addNewItemName(String []input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(ItemMoverBot.InputKey.a);
            return;
        }
        StringBuilder newItem = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++)
            newItem.append(" ").append(input[i]);

        addItem(newItem.toString());
    }

    private void setTargetContainerVolume(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ItemMoverBot.InputKey.stcn);
            return;
        }
        try {
            containerVolume = Integer.parseInt(input[0]);
            Utils.consolePrint("Maximum number of items inside containers was set to " + containerVolume);
        } catch (Exception e) {
            Utils.consolePrint("Wrong item number value!");
        }
    }

    private void setTargetAsContainer(String []input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(ItemMoverBot.InputKey.stc);
            return;
        }
        StringBuilder newContainer = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++)
            newContainer.append(" ").append(input[i]);

        WurmComponent wurmComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (wurmComponent == null) {
            Utils.consolePrint("Didn't find an opened container");
            return;
        }
        try {
            targetComponent = ReflectionUtil.getPrivateField(wurmComponent,
                    ReflectionUtil.getField(wurmComponent.getClass(), "component"));
            this.containerName = newContainer.toString();
            targetType = TargetType.Containers;
            Utils.consolePrint("New target component was set with container \"" + containerName + "\"");
        } catch(Exception e) {
            Utils.consolePrint("Error on getting container information");
            e.printStackTrace();
        }
    }

    private void setTargetContainerRoot() {
        WurmComponent inventoryComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (inventoryComponent == null) {
            Utils.consolePrint("Didn't find an opened container");
            return;
        }
        InventoryListComponent ilc;
        try {
            ilc = ReflectionUtil.getPrivateField(inventoryComponent,
                    ReflectionUtil.getField(inventoryComponent.getClass(), "component"));
        } catch(Exception e) {
            Utils.consolePrint("Error on getting container information");
            e.printStackTrace();
            return;
        }
        InventoryMetaItem rootItem = Utils.getRootItem(ilc);
        if (rootItem!=null) {
            Utils.consolePrint("Items will go to the \"" + rootItem.getBaseName() + "\"");
            targetType = TargetType.ContainerRoot;
            targetComponent = ilc;
        } else {
            Utils.consolePrint("Failed on configuring the target container");
        }
    }
    private void setTargetItem() {
        try {
            int x = Mod.hud.getWorld().getClient().getXMouse();
            int y = Mod.hud.getWorld().getClient().getYMouse();
            long [] targets = Mod.hud.getCommandTargetsFrom(x,y);
            if (targets != null && targets.length > 0) {
                target = targets[0];
                targetType = TargetType.Item;
                Utils.consolePrint("New target is " + target);
            } else
                Utils.consolePrint("Can't find the target");
        }catch (Exception e) {
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error while setting target - " + e.getMessage());
            Utils.consolePrint( e.toString());
        }
    }

    private void addItem(String item) {
        String matchList = "(\\s*,\\s*)(?=(?:(?:[^']*'){2})*[^']*$)";
        if (itemNames == null)
            itemNames = new HashSet<>();
        if (itemMaximumWeights == null)
            itemMaximumWeights = new HashMap<>();
        if(item.contains(",")){
            String[] items = item.split(matchList);
            for(String it: items){
                if(!it.equals("")){
                    itemNames.add(it);
                    itemMaximumWeights.put(it, 0f);
                    lastItemName = it;
                }
            }
        }else{
            itemNames.add(item);
            itemMaximumWeights.put(item, 0f);
            lastItemName = item;
        }
        Utils.consolePrint("Current item set - " + itemNames.toString());
    }

    private void clearItemList(){
        itemNames.clear();
        itemMaximumWeights.clear();
        lastItemName="";
        Utils.consolePrint("Current item set - " + itemNames.toString());
    }

    private enum InputKey implements Bot.InputKey {
        clear("Clear item list",""),
        st("Set the target item(under mouse pointer). Items from your inventory will be moved inside this item if it is a container or next to it otherwise.", ""),
        stid("Set the id of target item. Items from your inventory will be moved inside this item if it is a container or next to it otherwise.", "id"),
        str("Set the target container(under mouse pointer). Items from your inventory will be moved to the root directory of that container.", ""),
        stcn("Set the number of items to put inside each container. Use with \"stc\" key", "number"),
        stc("Set the target container(under mouse pointer) with another containers inside. " +
                "Items from your inventory will be moved to containers with provided name. " +
                "Bot will try to put 100 items inside each container. But you can change this value using \"" + stcn.name() + "\" key.", "container_name"),
        sw("Set the maximum weight for item to be moved. Affects the last added item name.", "weight(float number)"),
        a("Add new item name to move to the targets. " +
                "The maximum weight of moved item can be configured with \"" + sw.name() + "\" key", "name"),
        r("Toggle the moving of rare items. Disabled by default.", ""),
        fl("Toggle the moving of only first level items of your inventory. " +
                "Items that match added keywords but lying inside a group or a container will not be touched. " +
                "Enabled by default", "");

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

    enum TargetType {
        Item,
        ContainerRoot,
        Containers
    }
}