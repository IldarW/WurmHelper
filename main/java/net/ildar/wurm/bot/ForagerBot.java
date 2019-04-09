package net.ildar.wurm.bot;

import com.wurmonline.client.game.PlayerObj;
import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.mesh.GrassData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import javafx.util.Pair;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class ForagerBot extends Bot {
    static String DEFAULT_CONTAINER_NAME = "backpack";
    private static Set<String> forageSet = new HashSet<>(Arrays.asList(
            "oregano","rosemary","lingonberry","pumpkin",
            "thyme","tomato","lovage","fennel plant",
            "acorn","cumin","wemp plants","corn",
            "potato","belladonna","mixed grass","cotton",
            "cabbage","ginger","raspberries","cocoa bean",
            "sage","blueberry","carrot","garlic",
            "rye","sassafras","strawberries","egg",
            "nettles","pea pod","parsley","wheat",
            "barley","onion","turmeric","basil",
            "mint","sugar beet","rice","cucumber",
            "lettuce","branch","woad","oat",
            "paprika", "nutmeg", "rock"));
    private static Set<String> forageSetKeywords = new HashSet<>(Arrays.asList(
            "fresh","seedling","sprout","mushroom","bouquet"));

    private float staminaThreshold;
    private Comparator<InventoryMetaItem> weightComparator = Comparator.comparingDouble(InventoryMetaItem::getWeight);
    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private long sickleId;
    private int maxActions;
    private final List<Pair<Integer, Integer>> queuedTiles = new ArrayList<>();
    private List <Pair<Integer, Integer>> forageTilesInProcess = new ArrayList<>();
    private List <Pair<Integer, Integer>> botanizeTilesInProcess = new ArrayList<>();
    private List <Pair<Integer, Integer>> foragedTiles = new ArrayList<>();
    private List <Pair<Integer, Integer>> botanizedTiles = new ArrayList<>();
    private String containerName = DEFAULT_CONTAINER_NAME;
    private ForageType forageType = ForageType.Default;
    private BotanizeType botanizeType = BotanizeType.Default;
    private long lastActionFinishedTime;

    private boolean grassGathering = false;
    private boolean foraging = true;
    private boolean botanizing = true;
    private boolean dropping = false;
    private boolean dropWhenFull = false;
    private boolean verbose = false;
    private List<String> filterItemNames = new ArrayList<>();

    public static BotRegistration getRegistration() {
        return new BotRegistration(ForagerBot.class,
                "Can forage, botanize, collect grass and flowers in an area surrounding player. " +
                        "Bot can be configured to process rectangular area of any size. " +
                        "Picked items, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. " +
                        "Default container name is \"" + ForagerBot.DEFAULT_CONTAINER_NAME + "\". Containers only in root directory of player's inventory will be taken into account. " +
                        "Bot can be configured to drop picked items on the floor. ",
                "fg");
    }

    public ForagerBot() {
        registerInputHandler(ForagerBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(ForagerBot.InputKey.g, input -> toggleGrassGathering());
        registerInputHandler(ForagerBot.InputKey.f, input -> toggleForaging());
        registerInputHandler(ForagerBot.InputKey.ftl, input -> showForagingTypes());
        registerInputHandler(ForagerBot.InputKey.ft, this::setForagingType);
        registerInputHandler(ForagerBot.InputKey.b, input -> toggleBotanizing());
        registerInputHandler(ForagerBot.InputKey.btl, input -> showBotanizingTypes());
        registerInputHandler(ForagerBot.InputKey.bt, this::setBotanizingType);
        registerInputHandler(ForagerBot.InputKey.d, input -> toggleDropping());
        registerInputHandler(ForagerBot.InputKey.dwf, input -> toggleDroppingWhenFull());
        registerInputHandler(ForagerBot.InputKey.dfa, this::addItemToFilter);
        registerInputHandler(ForagerBot.InputKey.dfc, input -> clearFilter());
        registerInputHandler(ForagerBot.InputKey.v, input -> toggleVerboseMode());
        registerInputHandler(ForagerBot.InputKey.scn, this::setContainerName);
        registerInputHandler(ForagerBot.InputKey.na, this::setMaxActions);
    }

    @Override
    public void work() throws Exception{
        setStaminaThreshold(0.9f);
        setTimeout(300);

        World world = Mod.hud.getWorld();
        PlayerObj player = world.getPlayer();
        maxActions = Utils.getMaxActionNumber();
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            float stamina = player.getStamina();
            float damage = player.getDamage();
            float forageSkill = player.getSkillSet().getSkillValue("foraging");
            float botanizeSkill = player.getSkillSet().getSkillValue("botanizing");

            if (Math.abs(lastActionFinishedTime - System.currentTimeMillis()) > 30000 && (stamina + damage) > staminaThreshold && queuedTiles.size() > 0) {
                if (verbose)
                    queuedTiles.forEach(tile -> Utils.consolePrint("Removing tile from queue - " + tile.getKey() + " " + tile.getValue()));
                queuedTiles.clear();
                forageTilesInProcess.clear();
                botanizeTilesInProcess.clear();
                if (verbose)
                    Utils.consolePrint(getClass().getSimpleName() + " queue cleared");
            }
            if ((stamina + damage) > staminaThreshold && queuedTiles.size() == 0) {
                int[][] checkedtiles = Utils.getAreaCoordinates();
                int tileIndex = -1;
                synchronized (queuedTiles) {
                    while (++tileIndex < 9 && queuedTiles.size() < maxActions) {
                        Pair<Integer, Integer> coordsPair = new Pair<>(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                        if (queuedTiles.contains(coordsPair))
                            continue;
                        Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                        byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                        if (tileType.isGrass() || tileType.isTree() || tileType.isBush()
                                || tileType.tilename.equals("Marsh")
                                || tileType.tilename.equals("Moss")
                                || tileType.tilename.equals("Steppe")) {
                            if (botanizing && !botanizedTiles.contains(coordsPair) && !botanizeTilesInProcess.contains(coordsPair) && queuedTiles.size() < maxActions
                                    && (!tileType.tilename.equals("Marsh") || botanizeSkill > 27)
                                    && (!tileType.tilename.equals("Moss") || botanizeSkill > 35)) {
                                if (verbose)
                                    Utils.consolePrint("Start botanizing at tile - " + checkedtiles[tileIndex][0] + " " + checkedtiles[tileIndex][1]);
                                Mod.hud.sendAction(botanizeType.action, Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0));
                                queuedTiles.add(coordsPair);
                                botanizeTilesInProcess.add(coordsPair);
                                if (tileType.isGrass()){
                                    if (botanizeSkill > 80)
                                        botanizeTilesInProcess.add(coordsPair);
                                    if (botanizeSkill > 53)
                                        botanizeTilesInProcess.add(coordsPair);
                                    if (botanizeSkill > 26)
                                        botanizeTilesInProcess.add(coordsPair);
                                }
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                        }
                        if (tileType.isGrass() || tileType.isTree() || tileType.isBush()
                                || tileType.tilename.equals("Steppe")
                                || tileType.tilename.equals("Tundra")
                                || tileType.tilename.equals("Marsh")) {
                            if (foraging && !foragedTiles.contains(coordsPair) && !forageTilesInProcess.contains(coordsPair) && queuedTiles.size() < maxActions
                                    && (!tileType.tilename.equals("Steppe") || botanizeSkill > 23)
                                    && (!tileType.tilename.equals("Tundra") || botanizeSkill > 33)
                                    && (!tileType.tilename.equals("Marsh") || botanizeSkill > 43)) {
                                if (verbose)
                                    Utils.consolePrint("Start foraging at tile - " + checkedtiles[tileIndex][0] + " " + checkedtiles[tileIndex][1]);
                                Mod.hud.sendAction(forageType.action, Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0));
                                queuedTiles.add(coordsPair);
                                forageTilesInProcess.add(coordsPair);
                                if (tileType.isGrass()) {
                                    if (forageSkill > 80)
                                        forageTilesInProcess.add(coordsPair);
                                    if (forageSkill > 53)
                                        forageTilesInProcess.add(coordsPair);
                                    if (forageSkill > 26)
                                        forageTilesInProcess.add(coordsPair);
                                }
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                        }
                        if (grassGathering && (tileType.isGrass() || tileType.isTree() || tileType.isBush())) {
                            if (GrassData.getFlowerTypeName(tileData).contains("flowers") && !tileType.isTree() && !tileType.isBush() && queuedTiles.size() < maxActions) {
                                Mod.hud.getWorld().getServerConnection().sendAction(sickleId,
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        new PlayerAction("",(short) 187, PlayerAction.ANYTHING));
                                queuedTiles.add(coordsPair);
                                if (verbose)
                                    Utils.consolePrint("Start cutting flowers at tile - " + checkedtiles[tileIndex][0] + " " + checkedtiles[tileIndex][1]);
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                            if (grassGathering && ((tileType.isGrass() && GrassData.GrowthStage.decodeTileData(tileData) != GrassData.GrowthStage.SHORT) ||
                                    ((tileType.isTree() || tileType.isBush()) && GrassData.GrowthTreeStage.decodeTileData(tileData) != GrassData.GrowthTreeStage.LAWN
                                            && GrassData.GrowthTreeStage.decodeTileData(tileData) != GrassData.GrowthTreeStage.SHORT)) && queuedTiles.size() < maxActions) {
                                Mod.hud.getWorld().getServerConnection().sendAction(sickleId,
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.GATHER);
                                queuedTiles.add(coordsPair);
                                if (verbose)
                                    Utils.consolePrint("Start cutting grass at tile - " + checkedtiles[tileIndex][0] + " " + checkedtiles[tileIndex][1]);
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                        }
                    }
                }
                if (queuedTiles.size() == 0 && areaAssistant.areaTourActivated())
                    areaAssistant.areaNextPosition();

                if(grassGathering) {
                    List <InventoryMetaItem> grass = Utils.getInventoryItems("mixed grass");
                    List <InventoryMetaItem> forCombining = new ArrayList<>();
                    grass.sort(weightComparator);
                    if(grass != null && grass.size() > 0) {
                        float totalWeight = 0;
                        for (InventoryMetaItem grassItem : grass)
                            if (grassItem.getWeight()+totalWeight < 3.2) {
                                forCombining.add(grassItem);
                                totalWeight += grassItem.getWeight();
                                if (totalWeight > 3.2) break;
                            }
                        if (forCombining.size() > 1) {
                            long[] targetIds = new long[forCombining.size()];
                            for(tileIndex = 0; tileIndex < Math.min(forCombining.size(), 64); tileIndex++)
                                targetIds[tileIndex] = forCombining.get(tileIndex).getId();
                            Mod.hud.getWorld().getServerConnection().sendAction(
                                    targetIds[0], targetIds, PlayerAction.COMBINE);

                        }
                    }
                }

                List<InventoryMetaItem> firstLevelItems = Utils.getFirstLevelItems();
                if (!dropping) {
                    List<InventoryMetaItem> foragables = firstLevelItems.stream()
                            .filter(ForagerBot::isForagable)
                            .collect(Collectors.toList());
                    List<InventoryMetaItem> containers =  firstLevelItems.stream()
                            .filter(item->item.getBaseName().contains(containerName))
                            .collect(Collectors.toList());
                    long[] foragablesIds = Utils.getItemIds(foragables);
                    if (foragablesIds != null && foragables.size() > 20) {
                        for (InventoryMetaItem container : containers) {
                            if (container.getChildren().size() < 100) {
                                Mod.hud.getWorld().getServerConnection().sendMoveSomeItems(
                                        container.getId(), foragablesIds);
                                break;
                            }
                        }
                    }
                }
                else if(!dropWhenFull) {
                    dropItems();
                }
            }
            sleep(timeout);
        }
    }

    public void dropItems(){
        if (dropping) {
            List<InventoryMetaItem> firstLevelItems = Utils.getFirstLevelItems();
            List<InventoryMetaItem> foragables = firstLevelItems.stream()
                    .filter(ForagerBot::isForagable)
                    .filter(item -> item.getRarity() == 0)
                    .collect(Collectors.toList());
            if (!filterItemNames.isEmpty()) {
                Iterator<InventoryMetaItem> iter = foragables.iterator();
                while (iter.hasNext()) {
                    InventoryMetaItem item = iter.next();
                    for (String name : filterItemNames) {
                        if (item.getBaseName().contains(name)) {
                            iter.remove();
                        }
                    }
                }
            }
            long[] foragablesIds = Utils.getItemIds(foragables);
            if (foragablesIds != null)
                Mod.hud.sendAction(PlayerAction.DROP, foragablesIds);
        }
    }

    public static boolean isForagable(InventoryMetaItem item) {
        return item != null && (forageSet.contains(item.getBaseName()) || forageSetKeywords.stream().anyMatch(keyword -> item.getBaseName().contains(keyword)));
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You are too far away"),
                this::actionNotQueued);
        registerEventProcessor(message -> message.contains("You're too busy"),
                this::actionNotQueued);
        registerEventProcessor(message -> (message.contains("You gather") && message.contains("mixed grass")
                        || message.contains("You pick some flowers")
                        || message.contains("You try to cut some short grass but you fail to get any significant amount.")),
                this::actionFinished);
        registerEventProcessor(message -> (message.contains("You find")
                        || message.contains("This area looks picked clean.")
                        || message.contains("You fail to find")),
                this::fbFinished);
        registerEventProcessor(message -> (message.contains("inventory is full") && dropWhenFull),
                this::dropItems);
    }
    private void showForagingTypes() {
        StringBuilder foragingTypes = new StringBuilder();
        foragingTypes.append("Available foraging types - ");
        for(ForageType forageType : ForageType.values())
            foragingTypes.append(forageType.abbreviation)
                    .append("(").append(forageType.name()).append("), ");
        foragingTypes.deleteCharAt(foragingTypes.length() - 1);
        foragingTypes.deleteCharAt(foragingTypes.length() - 1);
        Utils.consolePrint(foragingTypes.toString());
    }

    private void setForagingType(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ForagerBot.InputKey.ft);
            return;
        }
        ForageType forageType = ForageType.getByAbbreviation(input[0]);
        if (forageType == ForageType.Unknown) {
            Utils.consolePrint("Unknown foraging type. Use " +
                    "\"" + ForagerBot.InputKey.ftl.name() + "\" key to see available types");
            return;
        }
        this.forageType = forageType;
    }

    private void showBotanizingTypes() {
        StringBuilder botanizingTypes = new StringBuilder();
        botanizingTypes.append("Available botanizing types - ");
        for(BotanizeType botanizingType : BotanizeType.values())
            botanizingTypes.append(botanizingType.abbreviation)
                    .append("(").append(botanizingType.name()).append("), ");
        botanizingTypes.deleteCharAt(botanizingTypes.length() - 1);
        botanizingTypes.deleteCharAt(botanizingTypes.length() - 1);
        Utils.consolePrint(botanizingTypes.toString());
    }

    private void setBotanizingType(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ForagerBot.InputKey.bt);
            return;
        }
        BotanizeType botanizeType = BotanizeType.getByAbbreviation(input[0]);
        if (botanizeType == BotanizeType.Unknown) {
            Utils.consolePrint("Unknown botanizing type. Use " +
                    "\"" + ForagerBot.InputKey.ftl.name() + "\" key to see available types");
            return;
        }
        this.botanizeType = botanizeType;
    }

    private void setMaxActions(String [] input) {
        if (input.length != 1 ){
            printInputKeyUsageString(ForagerBot.InputKey.na);
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
        if (input.length < 1 ){
            printInputKeyUsageString(ForagerBot.InputKey.scn);
            return;
        }
        StringBuilder containerNameBuilder = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++)
            containerNameBuilder.append(" ").append(input[i]);
        containerName = containerNameBuilder.toString();
        Utils.consolePrint("Container name was set to \"" + containerName + "\"");
    }

    private void toggleVerboseMode() {
        verbose = !verbose;
        if (verbose)
            Utils.consolePrint("Verbose mode is on!");
        else
            Utils.consolePrint("Verbose mode is off!");
    }

    private void toggleDropping() {
        dropping = !dropping;
        if (dropping)
            Utils.consolePrint("Dropping is on!");
        else
            Utils.consolePrint("Dropping is off!");
    }
    private void toggleDroppingWhenFull() {
        dropWhenFull = !dropWhenFull;
        if (dropWhenFull)
            Utils.consolePrint("Drop when inventory full.");
        else
            Utils.consolePrint("Drop when action done.");
    }

    private void addItemToFilter(String[] input) {
        if (input.length < 1 ){
            printInputKeyUsageString(InputKey.dfa);
            return;
        }
        String itemName = Arrays.stream(input).collect(Collectors.joining(" "));
        filterItemNames.add(itemName);
        Utils.consolePrint("Added " + itemName + " to filter list. Current filter: [" + String.join(",", filterItemNames) + "]");
    }

    private void clearFilter() {
        Utils.consolePrint("Cleared drop filter.");
        filterItemNames.clear();
    }

    private void toggleBotanizing() {
        botanizing = !botanizing;
        if (botanizing)
            Utils.consolePrint("Botanizing is on!");
        else
            Utils.consolePrint("Botanizing is off!");
    }

    private void toggleForaging() {
        foraging = !foraging;
        if (foraging)
            Utils.consolePrint("Foraging is on!");
        else
            Utils.consolePrint("Foraging is off!");
    }

    private void toggleGrassGathering() {
        grassGathering = !grassGathering;
        if (grassGathering) {
            InventoryMetaItem sickle = Utils.getInventoryItem("sickle");
            if (sickle == null) {
                Utils.consolePrint("You don't have a sickle! " + this.getClass().getSimpleName() + " won't start");
                grassGathering = false;
                return;
            }
            sickleId = sickle.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + sickle.getDisplayName() + " with QL:" + sickle.getQuality() + " DMG:" + sickle.getDamage());
            Utils.consolePrint("Grass gathering is on!");
        } else
            Utils.consolePrint("Grass gathering is off!");
    }

    private void actionFinished() {
        synchronized (queuedTiles) {
            if (queuedTiles.size() > 0) {
                Pair<Integer, Integer> tile = queuedTiles.get(0);
                if (verbose)
                    Utils.consolePrint("Finish gathering grass at tile - " + tile.getKey() + " " + tile.getValue());
                queuedTiles.remove(0);
                lastActionFinishedTime = System.currentTimeMillis();
            }
        }
    }

    private void actionNotQueued() {
        synchronized (queuedTiles) {
            if (queuedTiles.size() > 0) {
                Pair<Integer, Integer> tile = queuedTiles.get(queuedTiles.size() - 1);
                float forageSkill = Mod.hud.getWorld().getPlayer().getSkillSet().getSkillValue("foraging");
                float botanizeSkill = Mod.hud.getWorld().getPlayer().getSkillSet().getSkillValue("botanizing");
                if (forageTilesInProcess.contains(tile)) {
                    forageTilesInProcess.remove(tile);
                    if (forageSkill > 80)
                        forageTilesInProcess.remove(tile);
                    if (forageSkill > 53)
                        forageTilesInProcess.remove(tile);
                    if(forageSkill > 26)
                        forageTilesInProcess.remove(tile);
                } else if (botanizeTilesInProcess.contains(tile)) {
                    botanizeTilesInProcess.remove(tile);
                    if (botanizeSkill > 80)
                        botanizeTilesInProcess.remove(tile);
                    if (botanizeSkill > 53)
                        botanizeTilesInProcess.remove(tile);
                    if(botanizeSkill > 26)
                        botanizeTilesInProcess.remove(tile);
                }
                if (verbose)
                    Utils.consolePrint("Too busy to queue tile - " + tile.getKey() + " " + tile.getValue());
                queuedTiles.remove(queuedTiles.size() - 1);
                lastActionFinishedTime = System.currentTimeMillis();
            }
        }
    }

    private void fbFinished() {
        synchronized (queuedTiles) {
            if (queuedTiles.size() > 0) {
                Pair<Integer, Integer> tile = queuedTiles.get(0);
                if (forageTilesInProcess.contains(tile)) {
                    if (verbose)
                        Utils.consolePrint("Finish foraging at tile - " + tile.getKey() + " " + tile.getValue());
                    foragedTiles.add(tile);
                    forageTilesInProcess.remove(tile);
                } else if (botanizeTilesInProcess.contains(tile)) {
                    if (verbose)
                        Utils.consolePrint("Finish botanizing at tile - " + tile.getKey() + " " + tile.getValue());
                    botanizedTiles.add(tile);
                    botanizeTilesInProcess.remove(tile);
                } else {
                    if (verbose)
                        Utils.consolePrint("found unchecked fb tile!");
                }
                queuedTiles.remove(0);
                lastActionFinishedTime = System.currentTimeMillis();
            }
        }
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ForagerBot.InputKey.s);
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
        g("Toggle the grass gathering", ""),
        f("Toggle the foraging", ""),
        ftl("Show the list of foraging types", ""),
        ft("Set the foraging type", "type"),
        b("Toggle the botanizing", ""),
        btl("Show the list of botanizing types", ""),
        bt("Set the botanizing type", "type"),
        d("Toggle the dropping of collected items to the ground", ""),
        dwf("Change drop mode between drop when full inventory or drop after every action", ""),
        dfa("Add item to drop filter. Drop filter items won't be dropped", "name(string)"),
        dfc("Clear filter", ""),
        v("Toggle the verbose mode. " +
                "Additional information will be shown in console during the work of the bot in verbose mode", ""),
        scn("Set the new name for containers to put sprouts/harvest", "container_name"),
        na("Set the number of actions bot will do each time", "number");

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

    enum ForageType{
        Unknown(null, ""),
        Default(PlayerAction.FORAGE, "*"),
        Resources(PlayerAction.FORAGE_RESOURCE, "r"),
        Vegetables(PlayerAction.FORAGE_VEG, "v"),
        Berries(PlayerAction.FORAGE_BERRIES, "b");

        PlayerAction action;
        String abbreviation;
        ForageType(PlayerAction action, String abbreviation) {
            this.action = action;
            this.abbreviation = abbreviation;
        }
        static ForageType getByAbbreviation(String abbreviation) {
            for (ForageType forageType : values())
                if (forageType.abbreviation.equals(abbreviation))
                    return forageType;
            return Unknown;
        }
    }

    @SuppressWarnings("unused")
    enum BotanizeType{
        Unknown(null, ""),
        Default(PlayerAction.BOTANIZE, "*"),
        Herbs(PlayerAction.BOTANIZE_HERBS, "h"),
        Plants(PlayerAction.BOTANIZE_PLANTS, "p"),
        Resources(PlayerAction.BOTANIZE_RESOURCE, "r"),
        Seeds(PlayerAction.BOTANIZE_SEEDS, "se"),
        Spices(PlayerAction.BOTANIZE_SPICES, "sp");

        PlayerAction action;
        String abbreviation;
        BotanizeType(PlayerAction action, String abbreviation) {
            this.action = action;
            this.abbreviation = abbreviation;
        }
        static BotanizeType getByAbbreviation(String abbreviation) {
            for (BotanizeType botanizeType : values())
                if (botanizeType.abbreviation.equals(abbreviation))
                    return botanizeType;
            return Unknown;
        }
    }
}
