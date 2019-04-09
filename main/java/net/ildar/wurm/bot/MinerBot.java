package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import javafx.util.Pair;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.*;
import java.util.stream.Collectors;

public class MinerBot extends Bot {
    private SmeltingOptions smeltingOptions = new SmeltingOptions();
    private MiningMode miningMode = MiningMode.Unknown;
    private float staminaThreshold;
    private InventoryMetaItem pickaxe;
    private long fixedTileId;
    private static int[] lastTile;
    private static Set<Pair<Integer, Integer>> errorTiles = new HashSet<>();
    private static long lastMining;
    private int clicks = 2;
    private boolean shardsCombining;
    private String shards = "rock shards";
    private String  fuel = "kindling";
    private long fuellingTimeout = 300000;
    private long lastFuelling;
    private boolean moving;
    private int movingForwardBias;
    private boolean smelting = false;
    private boolean verbose = false;
    private boolean noOre;
    private Random random = new Random();
    private Direction direction = Direction.FORWARD;

    public static BotRegistration getRegistration() {
        return new BotRegistration(MinerBot.class,
                "Mines rocks and smelts ores.", "m");
    }

    public MinerBot() {
        registerInputHandler(MinerBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(MinerBot.InputKey.c, this::setClicksNumber);
        registerInputHandler(MinerBot.InputKey.sc, input -> toggleShardsCombining());
        registerInputHandler(MinerBot.InputKey.scn, this::setCombiningShardsName);
        registerInputHandler(MinerBot.InputKey.fixed, input -> setFixedMiningMode());
        registerInputHandler(MinerBot.InputKey.st, input -> setSelectedTileMiningMode());
        registerInputHandler(MinerBot.InputKey.area, input -> setAreaMiningMode());
        registerInputHandler(MinerBot.InputKey.ft, input -> setFrontTileMiningMode());
        registerInputHandler(MinerBot.InputKey.o, input -> toggleOreMining());
        registerInputHandler(MinerBot.InputKey.m, input -> toggleMoving());
        registerInputHandler(MinerBot.InputKey.sm, input -> toggleSmelting());
        registerInputHandler(MinerBot.InputKey.at, this::addTarget);
        registerInputHandler(MinerBot.InputKey.ati, this::addTargetInventory);
        registerInputHandler(MinerBot.InputKey.atid, this::addTargetById);
        registerInputHandler(MinerBot.InputKey.sp, input -> setPile());
        registerInputHandler(MinerBot.InputKey.ssm, input -> setSmelter());
        registerInputHandler(MinerBot.InputKey.sft, this::setFuellingTimeout);
        registerInputHandler(MinerBot.InputKey.sfn, this::setFuelName);
        registerInputHandler(MinerBot.InputKey.v, input -> toggleVerboseMode());
        registerInputHandler(MinerBot.InputKey.dir, this::handleDirectionChange);
    }

    @Override
    public void work() throws Exception{
        staminaThreshold = 0.96f;
        pickaxe = Utils.getInventoryItem("pickaxe");
        if (pickaxe == null) {
            Utils.consolePrint("You don't have a pickaxe!");
            deactivate();
            return;
        }
        lastMining = System.currentTimeMillis();
        Utils.consolePrint(this.getClass().getSimpleName()
                + " will use " + pickaxe.getDisplayName()
                + " with QL:" + pickaxe.getQuality()
                + " DMG:" + pickaxe.getDamage());
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            if (shardsCombining) {
                List<ItemListWindow> piles = new ArrayList<>();
                for (WurmComponent wurmComponent : Mod.getInstance().components)
                    if (wurmComponent instanceof ItemListWindow
                            && !(wurmComponent instanceof InventoryWindow)) {
                        if (Utils.getRootItem(ReflectionUtil.getPrivateField(wurmComponent,
                                ReflectionUtil.getField(wurmComponent.getClass(), "component"))).getBaseName().toLowerCase().contains("pile of"))
                            piles.add((ItemListWindow) wurmComponent);
                    }

                ServerConnectionListenerClass sscc = Mod.hud.getWorld().getServerConnection().getServerConnectionListener();
                Map<Long, GroundItemCellRenderable> groundItems = ReflectionUtil.getPrivateField(sscc,
                        ReflectionUtil.getField(sscc.getClass(), "groundItems"));
                int tileX = Mod.hud.getWorld().getPlayerCurrentTileX();
                int tileY = Mod.hud.getWorld().getPlayerCurrentTileY();
                List<Long> closePileIds = new ArrayList<>();
                for (Map.Entry<Long, GroundItemCellRenderable> entry : new HashSet<>(groundItems.entrySet())) {
                    GroundItemCellRenderable groundItem = entry.getValue();
                    GroundItemData groundItemData = ReflectionUtil.getPrivateField(groundItem,
                            ReflectionUtil.getField(groundItem.getClass(), "item"));
                    int itemX = (int) (groundItemData.getX()/4);
                    int itemY = (int) (groundItemData.getY()/4);
                    if (itemX == tileX && itemY == tileY && groundItem.getHoverName().toLowerCase().contains("pile of ")) {
                        closePileIds.add(groundItem.getId());
                        if (piles.stream().noneMatch(pile -> {
                            try {
                                InventoryListComponent ilc = ReflectionUtil.getPrivateField(pile,
                                        ReflectionUtil.getField(pile.getClass(), "component"));
                                InventoryMetaItem rootItem = Utils.getRootItem(ilc);
                                if (rootItem != null)
                                    return rootItem.getId() == groundItem.getId();
                            } catch (IllegalAccessException | NoSuchFieldException e) {
                                e.printStackTrace();
                            }
                            return false;
                        })) {
                            if (verbose)
                                Utils.consolePrint("Opening " + groundItem.getHoverName() + " " + groundItem.getId());
                            Mod.hud.sendAction(PlayerAction.OPEN, groundItem.getId());
                        }
                    }
                }
                float freeSpace = Utils.getMaxWeight() - Utils.getTotalWeight();
                List<InventoryMetaItem> itemsToTake = new ArrayList<>();
                for (ItemListWindow wurmComponent : piles) {
                    InventoryListComponent ilc = ReflectionUtil.getPrivateField(wurmComponent,
                            ReflectionUtil.getField(wurmComponent.getClass(), "component"));
                    InventoryMetaItem rootItem = Utils.getRootItem(ilc);
                    if (!closePileIds.contains(rootItem.getId())) {
                        Mod.hud.sendAction(PlayerAction.CLOSE, rootItem.getId());
                        continue;
                    }
                    List<InventoryMetaItem> componentItems = Utils.getInventoryItems(ilc, shards);
                    if (componentItems != null && componentItems.size() > 0)
                        for (InventoryMetaItem item : componentItems)
                            if (item.getRarity() == 0
                                    && (item.getWeight() < freeSpace - 20
                                    || (itemsToTake.size() > 0 && item.getWeight() < freeSpace))) {
                                itemsToTake.add(item);
                                freeSpace -= item.getWeight();
                                if (freeSpace < 20) break;
                            }
                    if (freeSpace < 20) break;

                }
                if (itemsToTake.size() > 1 && freeSpace < 20) {
                    if (verbose) Utils.consolePrint("Taking " + itemsToTake.stream().map(InventoryMetaItem::getId).collect(Collectors.toList()));
                    for (InventoryMetaItem item : itemsToTake)
                        Mod.hud.sendAction(PlayerAction.TAKE, item.getId());
                }
                List<InventoryMetaItem> invShards = Utils.getInventoryItems(shards);
                if (invShards.size() > 1) {
                    long ids[] = new long[invShards.size()];
                    for (int i = 0; i < invShards.size(); i++)
                        ids[i] = invShards.get(i).getId();
                    if (verbose) Utils.consolePrint("Combining " + Arrays.toString(ids));
                    Mod.hud.getWorld().getServerConnection().sendAction(
                            ids[0], ids, PlayerAction.COMBINE);
                } else if (invShards.size() == 1) {
                    Mod.hud.sendAction(PlayerAction.DROP, invShards.get(0).getId());
                }
            }

            if (Mod.hud.getWorld().getPlayerLayer() >= 0) {
                sleep(timeout);
                continue;
            }
            float stamina = Mod.hud.getWorld().getPlayer().getStamina();
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            SelectBarRenderer sbr = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                    ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "renderer"));
            Object wpb = ReflectionUtil.getPrivateField(sbr,
                    ReflectionUtil.getField(sbr.getClass(), "progressBar"));
            float progress = ReflectionUtil.getPrivateField(wpb,
                    ReflectionUtil.getField(wpb.getClass(), "progress"));
            if ((stamina + damage) > staminaThreshold && progress == 0f) {
                boolean actionTaken = false;
                if (pickaxe.getDamage() > 10)
                    Mod.hud.sendAction(PlayerAction.REPAIR, pickaxe.getId());
                switch (miningMode) {
                    case SelectedTile: {
                        PickableUnit tile = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                                ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
                        if (tile != null) {
                            sendMineActions(tile.getId());
                            actionTaken = true;
                        } else
                            Utils.consolePrint("No target selected!");
                        break;
                    }
                    case Area: {
                        int area[][] = Utils.getAreaCoordinates();
                        for (int i = 1; i < area.length; i += 2) {
                            Tiles.Tile type = Mod.hud.getWorld().getCaveBuffer().getTileType(area[i][0], area[i][1]);
                            if ((type.tilename.equals("Cave wall") || type.tilename.equals("Rocksalt") || (type.isOreCave() && !noOre))
                                    && !isErrorTile(area[i][0], area[i][1])) {
                                sendMineActions(area[i]);
                                lastTile = area[i];
                                actionTaken = true;
                                break;
                            }
                            if (i == 7) i = -2;
                        }
                        break;
                    }
                    case FrontTile: {
                        int area[][] = Utils.getAreaCoordinates();
                        Tiles.Tile type = Mod.hud.getWorld().getCaveBuffer().getTileType(area[7][0], area[7][1]);
                        if ((type.tilename.equals("Cave wall") || type.tilename.equals("Rocksalt") || (type.isOreCave() && !noOre))
                                && !isErrorTile(area[7][0], area[7][1])) {
                            sendMineActions(area[7]);
                            actionTaken = true;
                            lastTile = area[7];
                        } else
                            Utils.consolePrint("Can't mine the tile in front of you");
                        break;
                    }
                    case FixedTile:
                        sendMineActions(fixedTileId);
                        actionTaken = true;
                        break;
                }
                if ((!actionTaken || Math.abs(lastMining - System.currentTimeMillis()) > 120000) && moving) {
                    int area[][] = Utils.getAreaCoordinates();
                    Tiles.Tile frontTileType = Mod.hud.getWorld().getCaveBuffer().getTileType(area[7][0], area[7][1]);
                    Tiles.Tile rightTileType = Mod.hud.getWorld().getCaveBuffer().getTileType(area[5][0], area[5][1]);
                    Tiles.Tile leftTileType = Mod.hud.getWorld().getCaveBuffer().getTileType(area[3][0], area[3][1]);
                    Utils.stabilizePlayer();
                    Thread.sleep(100);
                    if (isMinableTile(frontTileType))
                        Utils.movePlayer(4);
                    else {
                        int turn = 0;
                        if (movingForwardBias >= 0) {
                            if (isMinableTile(leftTileType))
                                turn = -1;
                            else if (isMinableTile(rightTileType))
                                turn = 1;
                        } else {
                            if (isMinableTile(rightTileType))
                                turn = 1;
                            else if (isMinableTile(leftTileType))
                                turn = -1;
                        }
                        if (random.nextInt(Math.abs(movingForwardBias) + 1) == 0)
                            turn = -turn;
                        if (turn == 1) {
                            Utils.turnPlayer(90);
                            Thread.sleep(100);
                            Utils.movePlayer(4);
                            Thread.sleep(100);
                            Utils.turnPlayer(-90);
                            movingForwardBias++;
                        } else if (turn == -1) {
                            Utils.turnPlayer(-90);
                            Thread.sleep(100);
                            Utils.movePlayer(4);
                            Thread.sleep(100);
                            Utils.turnPlayer(90);
                            movingForwardBias--;
                        }
                    }
                    Thread.sleep(100);
                    Utils.stabilizePlayer();
                }
                if (smelting) {
                    List<InventoryMetaItem> lumps = Utils.getInventoryItems(smeltingOptions.smelter, "lump")
                            .stream()
                            .filter(item -> item.getRarity() == 0)
                            .collect(Collectors.toList());
                    if (lumps.size() > 0) {
                        for (int i = smeltingOptions.containers.size() - 1; i >= 0; i--) {
                            List<Long> moveList = new ArrayList<>();
                            for (int j = 0; j < lumps.size(); j++) {
                                if (lumps.get(j).getQuality() >= smeltingOptions.containers.get(i).getValue())
                                    moveList.add(lumps.get(j).getId());
                            }
                            if (moveList.size() > 0) {
                                long[] moveItemIds = new long[moveList.size()];
                                for (int k = 0; k < moveList.size(); k++)
                                    moveItemIds[k] = moveList.get(k);
                                Mod.hud.getWorld().getServerConnection()
                                        .sendMoveSomeItems(smeltingOptions.containers.get(i).getKey(), moveItemIds);
                                lumps.removeIf(item -> moveList.contains(item.getId()));
                            }
                        }
                    }
                    List<InventoryMetaItem> ores = Utils.getInventoryItems(smeltingOptions.pile, "ore");
                    if (ores.size() > 0) {
                        long[] oreIds = Utils.getItemIds(ores);
                        Mod.hud.getWorld().getServerConnection()
                                .sendMoveSomeItems(Utils.getRootItem(smeltingOptions.smelter).getId(), oreIds);
                    }

                    if (Math.abs(lastFuelling - System.currentTimeMillis()) > fuellingTimeout) {
                        lastFuelling = System.currentTimeMillis();
                        InventoryMetaItem item = Utils.getInventoryItem(fuel);
                        if (item != null)
                            Mod.hud.getWorld().getServerConnection().sendAction(item.getId(),
                                        new long[]{Utils.getRootItem(smeltingOptions.smelter).getId()},
                                        new PlayerAction("",(short)117, PlayerAction.ANYTHING));
                        else
                            Utils.consolePrint("No fuel in inventory!");
                    }
                }
            }
            sleep(timeout);
        }
    }

    static private boolean isMinableTile(Tiles.Tile type) {
        return type.tilename.equals("Cave") || type.tilename.equals("Reinforced cave");
    }

    private void handleDirectionChange(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(MinerBot.InputKey.dir);
            printCurrentDirection();
            return;
        }
        
        Direction newDirection = Direction.getByAbbreviation(input[0]);
        if (newDirection == Direction.UNKNOWN) {
            printInputKeyUsageString(MinerBot.InputKey.dir);
            return;
        }

        direction = newDirection;
        printCurrentDirection();
    }

    private void printCurrentDirection() {
        Utils.consolePrint("Current direction is \"" + direction.abbreviation + "\"");
    }

    private void toggleVerboseMode() {
        verbose = !verbose;
        Utils.consolePrint(getClass().getSimpleName() + " is " + (verbose?"":"not ") + "verbose");
    }

    private void setFuellingTimeout(String [] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(MinerBot.InputKey.sft);
            return;
        }
        try {
            fuellingTimeout = Long.parseLong(input[0]);
            Utils.consolePrint("New fuelling timeout is " + fuellingTimeout);
        } catch (NumberFormatException e) {
            Utils.consolePrint("Invalid timeout value");
        }
    }

    private void setFuelName(String []input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(MinerBot.InputKey.sfn);
            return;
        }
        StringBuilder fuelname = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++)
            fuelname.append(" ").append(input[i]);
        this.fuel = fuelname.toString();
        Utils.consolePrint("New fuel name is " + this.fuel);
    }

    private void addTarget(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(MinerBot.InputKey.at);
            return;
        }
        try {
            float minQuality = Float.parseFloat(input[0]);
            int x = Mod.hud.getWorld().getClient().getXMouse();
            int y = Mod.hud.getWorld().getClient().getYMouse();
            long[] container = Mod.hud.getCommandTargetsFrom(x, y);
            if (container != null && container.length > 0) {
                smeltingOptions.containers.add(new Pair<>(container[0], minQuality));
                smeltingOptions.containers.sort(Comparator.comparingDouble(Pair::getValue));
                Utils.consolePrint("Added a new target with id - " + container[0] +
                        " and minimum quality - " + String.format("%.2f", minQuality));
            } else
                Utils.consolePrint("Couldn't find the target for " + getClass().getSimpleName());
        } catch (NumberFormatException e) {
            Utils.consolePrint("Invalid value");
        }
    }

    private void addTargetById(String []input) {
        if (input == null || input.length != 2) {
            printInputKeyUsageString(MinerBot.InputKey.atid);
            return;
        }
        try {
            long id = Long.parseLong(input[0]);
            float q = Float.parseFloat(input[1]);
            smeltingOptions.containers.add(new Pair<>(id,  q));
            smeltingOptions.containers.sort(Comparator.comparingDouble(Pair::getValue));
            Utils.consolePrint("Added a new target with id - " + id +
                    " and minimum quality - " + String.format("%.2f", q));
        } catch(NumberFormatException e) {
            Utils.consolePrint("Invalid values!");
        }
    }

    private void addTargetInventory(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(MinerBot.InputKey.ati);
            return;
        }
        try {
            addTargetContainer(Integer.parseInt(input[0]));
        } catch (NumberFormatException e) {
            Utils.consolePrint("Invalid value");
        }
    }

    private void toggleSmelting() {
        smelting = !smelting;
        if (smelting) {
            if (smeltingOptions.smelter == null || smeltingOptions.pile == null || smeltingOptions.containers == null || smeltingOptions.containers.size() == 0) {
                Utils.consolePrint("You should set smelter, pile and containers first!");
                smelting = false;
            } else
                Utils.consolePrint("Smelting is on");
        } else
            Utils.consolePrint("Smelting is off");
    }

    private void toggleMoving() {
        moving = !moving;
        if (moving) {
            Utils.stabilizePlayer();
            Utils.consolePrint(getClass().getSimpleName() + " will automatically moving forward");
        }
        else
            Utils.consolePrint(getClass().getSimpleName() + " will NOT move automatically");
    }

    private void toggleOreMining() {
        noOre = !noOre;
        if (!noOre)
            Utils.consolePrint(getClass().getSimpleName() + " will mine ore tiles too");
        else
            Utils.consolePrint(getClass().getSimpleName() + " will NOT mine ore tiles");
    }

    private void setFrontTileMiningMode() {
        miningMode = MiningMode.FrontTile;
        Utils.consolePrint(getClass().getSimpleName() + " will mine the tile in front of you");
    }

    private void setAreaMiningMode() {
        miningMode = MiningMode.Area;
        Utils.consolePrint(getClass().getSimpleName() + " will mine the surrounding area");
    }

    private void setSelectedTileMiningMode() {
        miningMode = MiningMode.SelectedTile;
        Utils.consolePrint(getClass().getSimpleName() + " will mine the selected tile");
    }

    private void setClicksNumber(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(MinerBot.InputKey.c);
            return;
        }
        try {
            int n = Integer.parseInt(input[0]);
            if (n < 1) n = 1;
            if (n > 10) n = 10;
            clicks = n;
            Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " clicks each time");
        } catch (InputMismatchException e) {
            Utils.consolePrint("Bad value!");
        }
    }

    private void setCombiningShardsName(String []input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(MinerBot.InputKey.scn);
            return;
        }
        if (!shardsCombining) {
            Utils.consolePrint("Stone combining is off! Can't set shards name");
            return;
        }

        StringBuilder shards = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++)
            shards.append(" ").append(input[i]);
        this.shards = shards.toString();
        Utils.consolePrint(getClass().getSimpleName() + " will combine " + this.shards);
    }

    private void setFixedMiningMode() {
        PickableUnit tile;
        try {
            tile = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                    ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Utils.consolePrint("Error on getting tile information");
            return;
        }
        if (tile != null) {
            fixedTileId = tile.getId();
            miningMode = MiningMode.FixedTile;
            Utils.consolePrint(getClass().getSimpleName() + " will mine the selected tile and remember it");
        } else
            Utils.consolePrint("No tile selected!");
    }

    private void toggleShardsCombining() {
        shardsCombining = !shardsCombining;
        if (shardsCombining)
            Utils.consolePrint(getClass().getSimpleName() + " will combine the " + shards + " around you");
        else
            Utils.consolePrint("Shards combining is off");
    }

    private static void tileError() {
        if (lastTile != null)
            errorTiles.add(new Pair<>(lastTile[0], lastTile[1]));
    }

    private boolean isErrorTile(int x, int y) {
        Pair pair = new Pair<>(x, y);
        return errorTiles.contains(pair);
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("The cave walls sound hollow")
                || message.contains("Another tunnel is too close")
                || message.contains("The cave walls look very unstable.")
                || message.contains("The cave walls look very unstable and dirt flows in")
                || message.contains("A dangerous crack is starting to form on the floor")
                || message.contains("The ground is too steep to mine at here")
                || message.contains("The ground sounds strangely hollow and brittle")
                || message.contains("You fail to produce anything here.")
                || message.contains("You hear falling rocks from the other side of the wall.")
                || message.contains("You cannot keep mining here. The rock is unusually hard")
                || message.contains("The roof sounds strangely hollow and you notice dirt flowing in, so you stop mining")
                || message.contains("The roof sounds dangerously weak and you must abandon this attempt")
                || message.contains("You are not allowed to mine here")
                || message.contains("The rock is too hard to mine")
                || message.contains("on the surface disturbs your operation")
                || message.contains("This tile is protected by the gods. You can not mine here")
                || message.contains("A felled tree on the surface disturbs your operation")
                || message.contains("Lowering the floor further would make the cavern unstable"), MinerBot::tileError);
        registerEventProcessor(message -> message.contains("You mine "), () -> lastMining = System.currentTimeMillis());
    }

    private void sendMineActions(int coords[]) {
        //wallside is 5 for north, 4 for west, 3 for south, 2 for east
        int x = Mod.hud.getWorld().getPlayerCurrentTileX();
        int y = Mod.hud.getWorld().getPlayerCurrentTileY();
        int wallSide = 0;
        if (x != coords[0] && y != coords[1]) {
            Tiles.Tile westTileType = Mod.hud.getWorld().getCaveBuffer().getTileType(x - 1, y);
            Tiles.Tile eastTileType = Mod.hud.getWorld().getCaveBuffer().getTileType(x + 1, y);
            Tiles.Tile northTileType = Mod.hud.getWorld().getCaveBuffer().getTileType(x, y - 1);
            Tiles.Tile southTileType = Mod.hud.getWorld().getCaveBuffer().getTileType(x, y + 1);
            if (coords[0] < x && isMinableTile(westTileType)) {
                if (coords[1] < y)
                    wallSide = 3;
                else if (coords[1] > y)
                    wallSide = 5;
            } else if (coords[0] > x && isMinableTile(eastTileType)) {
                if (coords[1] < y)
                    wallSide = 3;
                else if (coords[1] > y)
                    wallSide = 5;
            } else if (coords[1] < y && isMinableTile(northTileType)) {
                if (coords[0] > x)
                    wallSide = 4;
                else if (coords[0] < x)
                    wallSide = 2;
            } else if (coords[1] > y && isMinableTile(southTileType)) {
                if (coords[0] > x)
                    wallSide = 4;
                else if (coords[0] < x)
                    wallSide = 2;
            }
        } else if (coords[0] < x)
            wallSide = 2;
        else if (coords[0] > x)
            wallSide = 4;
        else if (coords[1] < y)
            wallSide = 3;
        else if (coords[1] > y)
            wallSide = 5;
        sendMineActions(Tiles.getTileId(coords[0], coords[1], wallSide, false));
    }

    private void sendMineActions(long tileId) {
        if (verbose) Utils.consolePrint("Mining tile " + tileId);

        for (int i = 0; i < clicks; i++)
            Mod.hud.getWorld().getServerConnection().sendAction(
                    pickaxe.getId(),
                    new long[]{tileId},
                    direction.action);
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(MinerBot.InputKey.s);
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

    private void addTargetContainer(float minQuality) {
        WurmComponent container = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
        if (container == null) {
            Utils.consolePrint("Can't find the container!");
            return;
        }
        try {
            InventoryListComponent ilc = ReflectionUtil.getPrivateField(container,
                    ReflectionUtil.getField(container.getClass(), "component"));
            InventoryMetaItem rootItem = Utils.getRootItem(ilc);
            if (rootItem == null) {
                Utils.consolePrint("");
                return;
            }
            smeltingOptions.containers.add(new Pair<>(rootItem.getId(), minQuality));
            smeltingOptions.containers.sort(Comparator.comparingDouble(Pair::getValue));
            Utils.consolePrint("Added a new target with id - " + rootItem.getId() +
                    " and minimum quality - " + String.format("%.2f", minQuality));

        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void setSmelter() {
        WurmComponent smelter = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
        if (smelter == null) {
            Utils.consolePrint("Can't set the smelter!");
            return;
        }
        try {
            smeltingOptions.smelter = ReflectionUtil.getPrivateField(smelter,
                    ReflectionUtil.getField(smelter.getClass(), "component"));
            Utils.consolePrint("The smelter is set");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void setPile() {
        WurmComponent pile = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
        if (pile == null) {
            Utils.consolePrint("Can't set the pile!");
            return;
        }
        try {
            smeltingOptions.pile = ReflectionUtil.getPrivateField(pile,
                    ReflectionUtil.getField(pile.getClass(), "component"));
            Utils.consolePrint("The pile is set");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    static class SmeltingOptions {
        InventoryListComponent smelter;
        InventoryListComponent pile;
        ArrayList<Pair<Long, Float>> containers = new ArrayList<>();
    }

    enum MiningMode{
        Unknown,
        SelectedTile,
        Area,
        FrontTile,
        FixedTile
    }

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        c("Change the amount of clicks bot will do each time", "n(integer value)"),
        sc("Toggle the combining of shards lying around the player in piles", ""),
        scn("Change the name of shards to combine. See \"" + sc.name() + "\" key", "name"),
        fixed("Set the fixed tile mining mode. Bot will remember selected tile and mine it", ""),
        st("Set the mining mode in which bot will mine currently selected tile", ""),
        area("Set the area mining mode in which bot will mine 3x3 area around player", ""),
        ft("Set the mining mode in which bot will mine a tile in front of a player", ""),
        o("Toggle the mining of ore tiles. Enabled by default", ""),
        m("Toggle the automatic moving forward when bot have no work", ""),
        sm("Toggle the smelting of ores in selected pile", ""),
        at("Add the target(under the mouse cursor) for lumps with provided minimum quality", "min_quality(0-100)"),
        ati("Add the target inventory(under the mouse cursor) for lumps with provided minimum quality", "min_quality(0-100)"),
        atid("Add the target with provided id for lumps with provided minimum quality", "id min_quality(0-100)"),
        sp("Set a pile(under the mouse cursor) for smelting ores", ""),
        ssm("Set a smelter(under the mouse cursor) for smelting ores", ""),
        sft("Set a smelter fuelling timeout for smelting ores", "timeout(in milliseconds)"),
        sfn("Set a name for the fuel for smelting ores", "name"),
        v("Toggle the verbose mode. While verbose bot will show additional info in console", ""),
        dir("Set mining direction. Possible directions are: f - forward, u - upward, d - downward. Forward is default direction.", "direction");

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

    enum Direction {
        UNKNOWN("", PlayerAction.MINE_FORWARD),
        FORWARD("f", PlayerAction.MINE_FORWARD),
        UPWARD("u", PlayerAction.MINE_UP),
        DOWNWARD("d", PlayerAction.MINE_DOWN);

        String abbreviation;
        PlayerAction action;
        Direction(String abbreviation, PlayerAction action)
        {
            this.abbreviation = abbreviation;
            this.action = action;
        }

        static Direction getByAbbreviation(String abbreviation) {
            for(Direction direction : values())
                if (direction.abbreviation.equals(abbreviation))
                    return direction;
            return UNKNOWN;
        }
    }
}
