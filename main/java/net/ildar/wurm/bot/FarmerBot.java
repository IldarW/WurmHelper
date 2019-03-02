package net.ildar.wurm.bot;

import com.wurmonline.client.game.PlayerObj;
import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.FieldData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.*;
import java.util.stream.Collectors;

public class FarmerBot extends Bot {
    private float staminaThreshold;
    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private boolean farmTending;
    private InventoryMetaItem rakeItem;
    private boolean harvesting;
    private InventoryMetaItem scytheItem;
    private boolean planting;
    private String seedsName;
    private boolean cultivating;
    private InventoryMetaItem shovelItem;
    private boolean dropping;
    private boolean repairing = true;
    private List<String> dropNamesList;
    private int dropLimit;

    public static BotRegistration getRegistration() {
        return new BotRegistration(FarmerBot.class,
                "Tends the fields, plants the seeds, cultivates the ground, collects harvests", "f");
    }

    public FarmerBot() {
        registerInputHandler(FarmerBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(FarmerBot.InputKey.ft, input -> toggleFarmTending());
        registerInputHandler(FarmerBot.InputKey.h, input -> toggleHarvesting());
        registerInputHandler(FarmerBot.InputKey.p, this::togglePlanting);
        registerInputHandler(FarmerBot.InputKey.c, input -> toggleCultivating());
        registerInputHandler(FarmerBot.InputKey.d, input -> toggleDropping());
        registerInputHandler(FarmerBot.InputKey.and, this::addDropItemName);
        registerInputHandler(FarmerBot.InputKey.r, input -> toggleRepairing());
        registerInputHandler(FarmerBot.InputKey.dl, this::setDropLimit);
    }

    @Override
    protected void work() throws Exception {
        setStaminaThreshold(0.9f);
        setTimeout(500);
        int maxActions = Utils.getMaxActionNumber();
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        World world = Mod.hud.getWorld();
        PlayerObj player = world.getPlayer();
        Set<String> cultivatedTiles = new HashSet<>(Arrays.asList(
                Tiles.Tile.TILE_STEPPE.tilename, Tiles.Tile.TILE_MOSS.tilename, Tiles.Tile.TILE_DIRT_PACKED.tilename));
        while (isActive()) {
            waitOnPause();
            float stamina = player.getStamina();
            float damage = player.getDamage();
            float progress = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "progress"));
            if ((stamina + damage) > staminaThreshold && progress == 0f) {
                int checkedtiles[][] = Utils.getAreaCoordinates();
                int initiatedActions = 0;
                int tileIndex = -1;

                List<InventoryMetaItem> seeds = null;
                int usedSeeds = 0;
                if (planting)
                    seeds = Utils.getInventoryItems(seedsName);
                while(++tileIndex < checkedtiles.length && initiatedActions < maxActions) {
                    Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    if (cultivating) {
                        checkToolDamage(shovelItem);
                        if (!tileType.isTree() && !tileType.isBush() && (tileType.isGrass() || cultivatedTiles.contains(tileType.tilename))) {
                            world.getServerConnection().sendAction(shovelItem.getId(),
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.CULTIVATE);
                            initiatedActions++;
                            continue;
                        }
                    }
                    if (farmTending){
                        checkToolDamage(rakeItem);
                        if (tileType == com.wurmonline.mesh.Tiles.Tile.TILE_FIELD || tileType == com.wurmonline.mesh.Tiles.Tile.TILE_FIELD2)
                            if (!com.wurmonline.mesh.FieldData.isTended(tileData)) {
                                world.getServerConnection().sendAction(rakeItem.getId(),
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.FARM);
                                initiatedActions++;
                                continue;
                            }
                    }
                    if (harvesting) {
                        checkToolDamage(scytheItem);
                        if (tileType == com.wurmonline.mesh.Tiles.Tile.TILE_FIELD || tileType == com.wurmonline.mesh.Tiles.Tile.TILE_FIELD2)
                            if (FieldData.getAgeName(tileData).equals("ripe")) {
                                world.getServerConnection().sendAction(scytheItem.getId(),
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.HARVEST);
                                initiatedActions++;
                                continue;
                            }
                    }
                    if (planting) {
                        if (tileType == Tiles.Tile.TILE_DIRT) {
                            if (seeds == null || seeds.size() == 0)
                                Utils.consolePrint("The player don't have any seeds left to plant");
                            else {
                                if (usedSeeds > seeds.size() - 2)
                                    continue;
                                world.getServerConnection().sendAction(seeds.get(usedSeeds++).getId(),
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.SOW);
                                initiatedActions++;
                                continue;
                            }
                        }
                    }
                }
                if (initiatedActions == 0)
                    areaAssistant.areaNextPosition();
            }
            if (dropping) {
                List<InventoryMetaItem> droplist = new ArrayList<>();
                for(String dropName : dropNamesList)
                    droplist.addAll(Utils.getInventoryItems(dropName));
                if (droplist.size() > 0) {
                    if (dropLimit != 0) {
                        if (droplist.size() > dropLimit) {
                            droplist = droplist.subList(dropLimit, droplist.size());
                        } else
                            droplist = new ArrayList<>();
                    }
                    droplist = droplist.stream().filter(item -> item.getRarity() == 0).collect(Collectors.toList());
                    if (droplist.size() > 0)
                        Mod.hud.sendAction(PlayerAction.DROP, Utils.getItemIds(droplist));
                }
            }
            sleep(timeout);
        }
    }

    private void checkToolDamage(InventoryMetaItem toolItem) {
        if (repairing && toolItem.getDamage() > 10)
            Mod.hud.sendAction(PlayerAction.REPAIR, toolItem.getId());
    }

    private void addDropItemName(String []input) {
        if (!dropping) {
            Utils.consolePrint("The dropping is off. Can't add new item name to drop");
            return;
        }
        if (input == null || input.length == 0) {
            printInputKeyUsageString(FarmerBot.InputKey.and);
            return;
        }
        StringBuilder name = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++) {
            name.append(" ").append(input[i]);
        }
        dropNamesList.add(name.toString());
        Utils.consolePrint("New name of item to drop was added - \"" + name.toString() + "\"");
    }
    private void toggleDropping() {
        dropping = !dropping;
        if (dropping) {
            Utils.consolePrint("The dropping of harvested items is on");
            dropNamesList = new ArrayList<>();
        } else
            Utils.consolePrint("The dropping of harvested items is off");
    }

    private void toggleCultivating() {
        if (!cultivating) {
            shovelItem = Utils.getInventoryItem("shovel");
            if (shovelItem == null) {
                Utils.consolePrint("The player don't have a shovel!");
                return;
            }
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + shovelItem.getDisplayName() + " with QL:" + shovelItem.getQuality() + " DMG:" + shovelItem.getDamage());
            cultivating = true;
            Utils.consolePrint("The cultivation is on");
        } else {
            cultivating = false;
            Utils.consolePrint("The cultivation is off");
        }
    }

    private void togglePlanting(String []input) {
        if (!planting) {
            if (input == null || input.length == 0) {
                printInputKeyUsageString(FarmerBot.InputKey.p);
                return;
            }
            StringBuilder plantName = new StringBuilder(input[0]);
            for (int i = 1; i < input.length; i++)
                plantName.append(" ").append(input[i]);
            this.seedsName = plantName.toString();
            Utils.consolePrint(this.getClass().getSimpleName() + " will plant " + this.seedsName);
            planting = true;
        } else {
            planting = false;
            Utils.consolePrint("Planting is off");
        }
    }

    private void toggleHarvesting() {
        if (!harvesting) {
            scytheItem = Utils.getInventoryItem("scythe");
            if (scytheItem == null) {
                Utils.consolePrint("The player don't have a scythe!");
                return;
            }
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + scytheItem.getDisplayName() + " with QL:" + scytheItem.getQuality() + " DMG:" + scytheItem.getDamage());
            harvesting = true;
            Utils.consolePrint("The harvesting is on");
        } else {
            harvesting = false;
            Utils.consolePrint("The harvesting is off");
        }
    }

    private void toggleFarmTending() {
        if (!farmTending) {
            rakeItem = Utils.getInventoryItem("rake");
            if (rakeItem == null) {
                Utils.consolePrint("The player don't have a rake!");
                return;
            }
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + rakeItem.getDisplayName() + " with QL:" + rakeItem.getQuality() + " DMG:" + rakeItem.getDamage());
            farmTending = true;
            Utils.consolePrint("The farm tending is on");
        } else {
            farmTending = false;
            Utils.consolePrint("The farm tending is off");
        }
    }

    private void toggleRepairing() {
        repairing = !repairing;
        Utils.consolePrint("The tool repairing is " + (repairing?"on":"off"));
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(FarmerBot.InputKey.s);
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

    private void setDropLimit(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(FarmerBot.InputKey.dl);
            return;
        }
        try{
            dropLimit = Integer.parseInt(input[0]);
            Utils.consolePrint("New drop limit is " + dropLimit);
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong drop limit value!");
        }
    }

    enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        r("Toggle the tool repairing", ""),
        ft("Toggle the farm tending", ""),
        h("Toggle the harvesting", ""),
        p("Toggle the planting. Provide the name of the seeds to plant", "seeds_name"),
        c("Toggle the dirt cultivation", ""),
        and("Add new item name to drop on the ground", "itemName"),
        d("Toggle the dropping of harvested items. Add item names to drop by \"" + and.name() + "\" key", ""),
        dl("Set the drop limit, configured number of harvests won't be dropped", "number");

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
