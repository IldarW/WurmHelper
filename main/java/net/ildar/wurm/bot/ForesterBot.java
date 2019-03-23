package net.ildar.wurm.bot;

import com.wurmonline.client.game.PlayerObj;
import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.TreeData;
import com.wurmonline.shared.constants.PlayerAction;
import javafx.util.Pair;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ForesterBot extends Bot {
    static String DEFAULT_CONTAINER_NAME = "backpack";
    private float staminaThreshold;
    private int maxActions;
    private AreaAssistant areaAssistant = new AreaAssistant(this);

    private long hatchetId;
    private List<Pair<Integer, Integer>> queuedTiles = new ArrayList<>();

    private long lastActionFinishedTime;

    private String containerName = DEFAULT_CONTAINER_NAME;
    private List<String> itemNamesToMove= new ArrayList<>();

    private boolean cutAllSprouts;
    private boolean harvesting;
    private boolean planting;
    private boolean shriveledTreesChopping;
    private boolean deforesting;
    private static int toHarvest;

    public static BotRegistration getRegistration() {
        return new BotRegistration(ForesterBot.class,
                "A forester bot. Can pick and plant sprouts, cut trees/bushes and gather the harvest in 3x3 area around player. " +
                        "Bot can be configured to process rectangular area of any size. " +
                        "Sprouts, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. " +
                        "Default container name is \"" + ForesterBot.DEFAULT_CONTAINER_NAME + "\". Containers only in root directory of player's inventory will be taken into account. " +
                        "New item names can be added(harvested fruits for example) to be moved to containers too. " +
                        "Steppe and moss tiles will be cultivated if planting is enabled and player have shovel in his inventory. ",
                "fr");
    }

    public ForesterBot() {
        registerInputHandler(ForesterBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(ForesterBot.InputKey.ca, input -> toggleAllTreesCutting());
        registerInputHandler(ForesterBot.InputKey.cs, input -> toggleShriveledTreesChopping());
        registerInputHandler(ForesterBot.InputKey.df, input -> toggleDeforestation());
        registerInputHandler(ForesterBot.InputKey.h, input -> toggleHarvesting());
        registerInputHandler(ForesterBot.InputKey.p, input -> togglePlanting());
        registerInputHandler(ForesterBot.InputKey.scn, this::setContainerName);
        registerInputHandler(ForesterBot.InputKey.na, this::setMaxActions);
        registerInputHandler(ForesterBot.InputKey.aim, this::addItemToMove);
    }

    @Override
    public void work() throws Exception {
        setStaminaThreshold(0.95f);
        setTimeout(300);
        World world = Mod.hud.getWorld();
        PlayerObj player = world.getPlayer();
        maxActions = Utils.getMaxActionNumber();
        InventoryMetaItem sickle = Utils.getInventoryItem("sickle");
        InventoryMetaItem bucket = Utils.getInventoryItem("bucket");
        lastActionFinishedTime = System.currentTimeMillis();
        long sickleId;
        if (sickle == null) {
            Utils.consolePrint("You don't have a sickle! " + this.getClass().getSimpleName() + " won't start");
            deactivate();
            return;
        } else {
            sickleId = sickle.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + sickle.getDisplayName() + " with QL:" + sickle.getQuality() + " DMG:" + sickle.getDamage());
        }
        if (bucket != null)
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + bucket.getDisplayName() + " with QL:" + bucket.getQuality() + " DMG:" + bucket.getDamage());
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            float stamina = player.getStamina();
            float damage = player.getDamage();
            if (Math.abs(lastActionFinishedTime - System.currentTimeMillis()) > 10000 && (stamina + damage) > staminaThreshold)
                queuedTiles.clear();
            if (Math.abs(lastActionFinishedTime - System.currentTimeMillis()) > 20000 && (stamina + damage) > staminaThreshold)
                toHarvest = 0;

            if ((stamina + damage) > staminaThreshold && queuedTiles.size() == 0 && toHarvest == 0) {
                int checkedtiles[][] = Utils.getAreaCoordinates();
                int tileIndex = -1;
                Set<Long> usedSprouts = new HashSet<>();
                while (++tileIndex < 9 && queuedTiles.size() + toHarvest < maxActions && toHarvest <= maxActions) {
                    Pair<Integer, Integer> coordsPair = new Pair<>(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    if (queuedTiles.contains(coordsPair))
                        continue;
                    Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    if (tileType.isTree() || tileType.isBush()) {
                        FoliageAge fage = FoliageAge.getFoliageAge(tileData);
                        if (harvesting && fage.getAgeId() > FoliageAge.YOUNG_FOUR.getAgeId()
                                && fage.getAgeId() < FoliageAge.OVERAGED.getAgeId()
                                && tileType.usesNewData()  && (tileData & 0x8) > 0) {
                            if(tileType.getTreeType(tileData) == TreeData.TreeType.MAPLE && bucket!=null)
                                world.getServerConnection().sendAction(bucket.getId(),
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.HARVEST);
                            else
                                world.getServerConnection().sendAction(sickleId,
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.HARVEST);
                            increaseHarvests(fage);
                            lastActionFinishedTime = System.currentTimeMillis();
                        } else if (fage.getAgeName().contains("overaged")) {
                            if (!deforesting)
                                world.getServerConnection().sendAction(sickleId,
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.PRUNE);
                            else
                                world.getServerConnection().sendAction(hatchetId,
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.CUT_DOWN);
                            queuedTiles.add(coordsPair);
                            lastActionFinishedTime = System.currentTimeMillis();
                        } else if (fage.getAgeName().contains("sprouting") && (cutAllSprouts || fage.getAgeName().contains("very old"))) {
                            world.getServerConnection().sendAction(sickleId,
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.PICK_SPROUT);
                            queuedTiles.add(coordsPair);
                            lastActionFinishedTime = System.currentTimeMillis();
                        } else if (deforesting || shriveledTreesChopping && fage.getAgeName().contains("shriveled")) {
                            world.getServerConnection().sendAction(hatchetId,
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.CUT_DOWN);
                            queuedTiles.add(coordsPair);
                            lastActionFinishedTime = System.currentTimeMillis();
                        }
                    }
                    if (planting && (tileType.isGrass() || tileType.tilename.equals("Dirt"))) {
                        List<InventoryMetaItem> sprouts = Utils.getInventoryItems("sprout")
                                .stream()
                                .filter(item -> (item.getRarity() == 0))
                                .collect(Collectors.toList());
                        if (sprouts != null && sprouts.size() > 0) {
                            for (InventoryMetaItem sprout : sprouts) {
                                if (!usedSprouts.contains(sprout.getId())) {
                                    world.getServerConnection().sendAction(sprout.getId(),
                                            new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                            PlayerAction.PLANT_CENTER);
                                    usedSprouts.add(sprout.getId());
                                    queuedTiles.add(coordsPair);
                                    lastActionFinishedTime = System.currentTimeMillis();
                                    break;
                                }
                            }
                        }
                    }
                    if (planting && (tileType.tilename.equals("Steppe")||tileType.tilename.equals("Moss"))) {
                        InventoryMetaItem shovel = Utils.getInventoryItem("shovel");
                        if (shovel != null) {
                            world.getServerConnection().sendAction(shovel.getId(),
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.CULTIVATE);
                            lastActionFinishedTime = System.currentTimeMillis();
                            queuedTiles.add(coordsPair);
                        }
                    }
                }
                if (queuedTiles.size() == 0 && toHarvest == 0 && areaAssistant.areaTourActivated())
                    areaAssistant.areaNextPosition();

                List<InventoryMetaItem> sprouts =
                        Utils.getFirstLevelItems()
                        .stream()
                        .filter(this::itemShouldBeMoved)
                        .collect(Collectors.toList());
                if (sprouts != null && sprouts.size() > 0) {
                    List<InventoryMetaItem> containers = Utils.getFirstLevelItems().stream()
                            .filter(item->item.getBaseName().contains(containerName))
                            .collect(Collectors.toList());
                    if (containers != null && containers.size() > 0)
                        for (InventoryMetaItem container : containers)
                            if (container.getChildren() != null && container.getChildren().size() < 100) {
                                long[] sproutIds = new long[sprouts.size()];
                                for (tileIndex = 0; tileIndex < sprouts.size(); tileIndex++)
                                    sproutIds[tileIndex] = sprouts.get(tileIndex).getId();
                                Mod.hud.getWorld().getServerConnection().sendMoveSomeItems(
                                        container.getId(), sproutIds);
                                break;
                            }
                }
            }
            sleep(timeout);
        }
    }

    private boolean itemShouldBeMoved(InventoryMetaItem item) {
        if (item.getBaseName().contains("sprout"))
            return true;
        for(String itemName : itemNamesToMove) {
            if (item.getBaseName().contains(itemName))
                return true;
        }
        return false;
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You are too far away") ,
                this::actionNotQueued);
        registerEventProcessor(message -> message.contains("You make a lot of errors and need to take a break"),
                this::actionFinished);
        registerEventProcessor(message -> (message.contains("You cut a sprout")
                        || message.contains("It does not make sense to prune")
                        || message.contains("You prune the ") || message.contains("You stop pruning")
                        || message.contains("You stop picking") || message.contains("has no sprout to pick")
                        || message.contains("has no sprout to pick") || message.contains("You stop cutting down.")
                        || message.contains("You cut down the ") || message.contains("You plant the sprout.")
                        || message.contains("You chip away some wood")
                        || message.contains("The ground is cultivated and ready to sow now.")),
                this::actionFinished);
        registerEventProcessor(message -> message.contains("You harvest "),
                this::harvestedSomething);
    }

    private void increaseHarvests(FoliageAge fage) {
        float f = Mod.hud.getWorld().getPlayer().getSkillSet().getSkillValue("forestry");
        int maxHarvest = 1;
        if (f > 80)
            maxHarvest = 4;
        else if (f > 53)
            maxHarvest = 3;
        else if (f > 26)
            maxHarvest = 2;
        String age = fage.getAgeName();
        if (age.contains("very old")) {
            toHarvest += maxHarvest;
        }
        else if (fage.getAgeId() == FoliageAge.OLD_ONE.getAgeId() || fage.getAgeId() == FoliageAge.OLD_ONE_SPROUTING.getAgeId()) {
            toHarvest += Math.max(1, maxHarvest - 2);
        }
        else if (age.contains("old")) {
            toHarvest += Math.max(1, maxHarvest - 1);
        }
        else if (age.contains("mature")) {
            toHarvest++;
        }
    }

    private void harvestedSomething() {
        if (--toHarvest < 0)
            toHarvest = 0;
        lastActionFinishedTime = System.currentTimeMillis();
    }

    private void addItemToMove(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ForesterBot.InputKey.aim);
            return;
        }
        itemNamesToMove.add(input[0]);
        Utils.consolePrint("Items with name \"" + input[0] + "\" will be moved to containers");
    }

    private void setMaxActions(String [] input) {
        if (input.length != 1 ){
            printInputKeyUsageString(ForesterBot.InputKey.na);
            return;
        }
        try {
            maxActions = Integer.parseInt(input[0]);
            Utils.consolePrint("Maximum actions was set " + maxActions);
        } catch (Exception e) {
            Utils.consolePrint("Wrong max actions value!");
        }
    }

    private void setContainerName(String []input) {
        if (input.length != 1 ){
            printInputKeyUsageString(ForesterBot.InputKey.scn);
            return;
        }
        containerName = input[0];
        Utils.consolePrint("Container name was set to \"" + containerName + "\"");
    }

    private void togglePlanting() {
        planting = !planting;
        if (planting)
            Utils.consolePrint("Planting is on!");
        else
            Utils.consolePrint("Planting is off!");
    }

    private void toggleHarvesting() {
        harvesting = !harvesting;
        if (harvesting)
            Utils.consolePrint("Harvesting is on!");
        else
            Utils.consolePrint("Harvesting is off!");
    }

    private void toggleAllTreesCutting() {
        cutAllSprouts = !cutAllSprouts;
        if (cutAllSprouts)
            Utils.consolePrint(this.getClass().getSimpleName() + " will cut sprouts from trees and bushes of any age");
        else
            Utils.consolePrint(this.getClass().getSimpleName() + " will cut sprouts only from very old trees and bushes");
    }

    private void actionFinished() {
        if (queuedTiles.size() > 0) {
            queuedTiles.remove(0);
            lastActionFinishedTime = System.currentTimeMillis();
        }
    }

    private void actionNotQueued() {
        if (queuedTiles.size() > 0) {
            queuedTiles.remove(queuedTiles.size()  - 1);
            lastActionFinishedTime = System.currentTimeMillis();
        }
        toHarvest = 0;
    }

    private void toggleShriveledTreesChopping() {
        if (!shriveledTreesChopping) {
            InventoryMetaItem hatchet = Utils.getInventoryItem("hatchet");
            if (hatchet == null) {
                Utils.consolePrint("You don't have a hatchet!");
            } else {
                shriveledTreesChopping = true;
                hatchetId = hatchet.getId();
                Utils.consolePrint(this.getClass().getSimpleName() + " will use " + hatchet.getDisplayName() + " to chop shriveled trees.");
                Utils.consolePrint("QL:" + hatchet.getQuality() + " DMG:" + hatchet.getDamage());
            }
        } else {
            shriveledTreesChopping = false;
            Utils.consolePrint("Auto chopping shriveled trees is off");
        }
    }

    private void toggleDeforestation() {
        deforesting = !deforesting;
        if (deforesting) {
            InventoryMetaItem hatchet = Utils.getInventoryItem("hatchet");
            if (hatchet == null) {
                Utils.consolePrint("You don't have a hatchet!");
                deforesting = false;
            } else {
                hatchetId = hatchet.getId();
                Utils.consolePrint(this.getClass().getSimpleName() + " will use " + hatchet.getDisplayName() + " to chop trees.");
                Utils.consolePrint("QL:" + hatchet.getQuality() + " DMG:" + hatchet.getDamage());
                Utils.consolePrint("Deforesting is on!");
                if (planting) {
                    planting = false;
                    Utils.consolePrint("Planting is off!");
                }
            }
        }
        else
            Utils.consolePrint("Deforesting is off!");
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ForesterBot.InputKey.s);
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

    enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        ca("Toggle the cutting of sprouts from all trees", ""),
        cs("Toggle the cutting of shriveled trees", ""),
        df("Toggle the cutting of all trees (deforestation)", ""),
        h("Toggle the harvesting", ""),
        p("Toggle the planting", ""),
        scn("Set the new name for containers to put sprouts/harvest", "container_name"),
        na("Set the number of actions bot will do each time", "number"),
        aim("Add new item name for moving into containers", "item_name");

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
