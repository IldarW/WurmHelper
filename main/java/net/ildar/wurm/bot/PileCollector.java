package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.ItemListWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.*;
import java.util.stream.Collectors;

public class PileCollector extends Bot {
    private final float MAX_DISTANCE = 4;
    private Set<Long> openedPiles = new HashSet<>();
    private InventoryListComponent targetLc;
    private String containerName = "large crate";
    private int containerCapacity = 300;
    private String targetItemName = "dirt";

    public static BotRegistration getRegistration() {
        return new BotRegistration(PileCollector.class,
                "Collects piles of items to bulk containers. Default name for target items is \"dirt\"", "pc");
    }

    public PileCollector() {
        registerInputHandler(PileCollector.InputKey.stn, this::setTargetName);
        registerInputHandler(PileCollector.InputKey.st, this::setTargetInventoryName);
        registerInputHandler(PileCollector.InputKey.stcc, this::setContainerCapacity);
    }

    @Override
    protected void work() throws Exception {
        setTimeout(500);
        ServerConnectionListenerClass sscc = Mod.hud.getWorld().getServerConnection().getServerConnectionListener();
        while (isActive()) {
            waitOnPause();
            Map<Long, GroundItemCellRenderable> groundItemsMap = ReflectionUtil.getPrivateField(sscc,
                    ReflectionUtil.getField(sscc.getClass(), "groundItems"));
            List<GroundItemCellRenderable> groundItems = groundItemsMap.entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toList());
            float x = Mod.hud.getWorld().getPlayerPosX();
            float y = Mod.hud.getWorld().getPlayerPosY();
            if (groundItems.size() > 0 && targetLc != null) {
                try {
                    for (GroundItemCellRenderable groundItem : groundItems) {
                        GroundItemData groundItemData = ReflectionUtil.getPrivateField(groundItem,
                                ReflectionUtil.getField(groundItem.getClass(), "item"));
                        float itemX = groundItemData.getX();
                        float itemY = groundItemData.getY();
                        if ((Math.sqrt(Math.pow(itemX - x, 2) + Math.pow(itemY - y, 2)) <= MAX_DISTANCE)) {
                            if (groundItemData.getName().toLowerCase().contains("pile of ") && !openedPiles.contains(groundItemData.getId()))
                                Mod.hud.sendAction(PlayerAction.OPEN, groundItemData.getId());
                            else if (groundItemData.getName().contains(targetItemName))
                                Mod.hud.sendAction(PlayerAction.TAKE, groundItemData.getId());

                        }

                    }
                } catch (ConcurrentModificationException ignored) {}
                for(WurmComponent wurmComponent : Mod.getInstance().components) {
                    if (wurmComponent instanceof ItemListWindow) {
                        InventoryListComponent ilc = ReflectionUtil.getPrivateField(wurmComponent,
                                ReflectionUtil.getField(wurmComponent.getClass(), "component"));
                        if (ilc == null) continue;
                        InventoryMetaItem rootItem = Utils.getRootItem(ilc);
                        if (rootItem == null || !rootItem.getBaseName().toLowerCase().contains("pile of")) continue;
                        openedPiles.add(rootItem.getId());
                        List<InventoryMetaItem> targetItems = Utils.getInventoryItems(ilc, targetItemName);
                        moveToContainers(targetItems);
                    }
                }
                List<InventoryMetaItem> targetItems = Utils.getInventoryItems(targetItemName).stream().filter(item -> item.getBaseName().equals(targetItemName) && item.getRarity() == 0).collect(Collectors.toList());
                moveToContainers(targetItems);
            }
            sleep(timeout);
        }
    }

    private void moveToContainers(List<InventoryMetaItem> targetItems) {
        if (targetItems != null && targetItems.size() > 0) {
            List<InventoryMetaItem> containers = Utils.getInventoryItems(targetLc, containerName);
            if (containers == null || containers.size() == 0) {
                Utils.consolePrint("No target containers!");
                return;
            }
            for(InventoryMetaItem container : containers) {
                List<InventoryMetaItem> containerContents = container.getChildren();
                int itemsCount = 0;
                if (containerContents != null) {
                    for(InventoryMetaItem contentItem : containerContents) {
                        String customName = contentItem.getCustomName();
                        if (customName != null) {
                            try {
                                itemsCount += Integer.parseInt(customName.substring(0, customName.length() - 1));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                if (itemsCount < containerCapacity) {
                    Mod.hud.getWorld().getServerConnection().sendMoveSomeItems(container.getId(), Utils.getItemIds(targetItems));
                    return;
                }
            }
        }
    }

    private void setTargetName(String []input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(PileCollector.InputKey.stn);
            return;
        }
        StringBuilder targetName = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++) {
            targetName.append(" ").append(input[i]);
        }
        this.targetItemName = targetName.toString();
        Utils.consolePrint("New name for target items is \"" + this.targetItemName + "\"");
    }

    private void setTargetInventoryName(String []input) {
        WurmComponent wurmComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
        if (wurmComponent == null) {
            Utils.consolePrint("Can't find an inventory");
            return;
        }
        try {
            targetLc = ReflectionUtil.getPrivateField(wurmComponent,
                    ReflectionUtil.getField(wurmComponent.getClass(), "component"));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Utils.consolePrint("Error on configuring the target");
            return;
        }
        if (input != null && input.length != 0) {
            StringBuilder containerName = new StringBuilder(input[0]);
            for (int i = 1; i < input.length; i++) {
                containerName.append(" ").append(input[i]);
            }
            this.containerName = containerName.toString();
        }
        Utils.consolePrint("The target was set with container name - \"" + containerName + "\"");
    }

    private void setContainerCapacity(String []input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(PileCollector.InputKey.stcc);
            return;
        }
        try {
            containerCapacity = Integer.parseInt(input[0]);
            Utils.consolePrint("New container capacity is " + containerCapacity);
        }catch (NumberFormatException e) {
            Utils.consolePrint("Wrong value!");
        }
    }

    private enum InputKey implements Bot.InputKey {
        stn("Set the name for target items. Default name is \"dirt\"", "name"),
        st("Set the target bulk inventory to put items to. Provide an optional name of containers inside inventory. Default is \"large crate\"", "[name]"),
        stcc("Set the capacity for target container. Default value is 300", "capacity(integer value)");

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
}
