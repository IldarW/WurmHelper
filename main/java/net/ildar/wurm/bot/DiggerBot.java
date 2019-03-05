package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.TilePicker;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import javafx.util.Pair;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DiggerBot extends Bot{
    private final int STEPS = 5;

    private long stepDuration;
    private int clicks;
    private float staminaThreshold;
    private WorkMode workMode;
    private int diggingHeightLimit;
    private boolean levellingDone;
    private boolean toolRepairing = true;
    private DiggingTileInfo diggingTileInfo;
    private AreaAssistant areaAssistant;
    private InventoryMetaItem shovelItem;
    private PlayerAction digAction;
    private Set<Pair<Integer, Integer>> invalidCorners;
    private boolean surfaceMiningMode;
    private InventoryMetaItem pickaxeItem;

    public static Tiles.Tile[] DirtList = {Tiles.Tile.TILE_DIRT, Tiles.Tile.TILE_GRASS, Tiles.Tile.TILE_SAND, Tiles.Tile.TILE_MYCELIUM, Tiles.Tile.TILE_TUNDRA, Tiles.Tile.TILE_STEPPE};

    public static BotRegistration getRegistration() {
        return new BotRegistration(DiggerBot.class,
                "Does the dirty job for you", "d");
    }

    public DiggerBot() {
        registerInputHandler(DiggerBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(DiggerBot.InputKey.c, this::setClicksNumber);
        registerInputHandler(DiggerBot.InputKey.d, this::toggleDigging);
        registerInputHandler(DiggerBot.InputKey.dtile, this::toggleTileDiggingMode);
        registerInputHandler(DiggerBot.InputKey.dtp, input -> toggleDigToPileAction());
        registerInputHandler(DiggerBot.InputKey.l, input -> toggleLevelling());
        registerInputHandler(DiggerBot.InputKey.la, this::toggleLevellingArea);
        registerInputHandler(DiggerBot.InputKey.tr, input -> toggleToolRepairing());
        registerInputHandler(DiggerBot.InputKey.sm, input -> toggleSurfaceMining());

        areaAssistant = new AreaAssistant(this);
        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(2);
        invalidCorners = new HashSet<>();
        digAction = PlayerAction.DIG_TO_PILE;
        workMode = WorkMode.Unknown;
        stepDuration = 1000;
    }

    private void registerEventProcessors() {
        registerEventProcessor(message ->
                        message.contains("is too steep for your skill level") ||
                                message.contains("ground is flat here") ||
                                message.contains("You finish levelling"),
                () -> levellingDone = true);
        registerEventProcessor(message -> message.contains("You can not dig in the solid rock") ||
                message.contains("You hit the rock in a corner") ||
                message.contains("The road would be too steep to traverse") ||
                message.contains("The water is too deep or too shallow to dig using that tool") ||
                message.contains("You are not skilled enough to dig in such steep slopes") ||
                message.contains("You cannot dig in such terrain") ||
                message.contains("You hit rock") ||
                message.contains("Your shovel fails to penetrate the earth no matter what you try. Weird") ||
                message.contains("You suddenly become very weak, and your arm muscles fail you. You just can not dig here it seems") ||
                message.contains("You can't figure out how to remove the stone. You must become a bit better at digging first") ||
                message.contains("You need to be stronger to dig on roads") ||
                message.contains("The object nearby prevents digging further down"), this::handleInvalidCorner);
    }

    @Override
    protected void work() throws Exception{
        shovelItem = Utils.getInventoryItem("shovel");
        if (shovelItem == null) {
            Utils.consolePrint("Player doesn't have a shovel!");
            return;
        }
        setStaminaThreshold(0.95f);
        setTimeout(500);
        clicks = Utils.getMaxActionNumber();
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            if (toolRepairing) {
                if (surfaceMiningMode && pickaxeItem.getDamage() > 10)
                    Mod.hud.sendAction(PlayerAction.REPAIR, pickaxeItem.getId());
                if (!surfaceMiningMode && shovelItem.getDamage() > 10)
                    Mod.hud.sendAction(PlayerAction.REPAIR, shovelItem.getId());
            }
            float stamina = Mod.hud.getWorld().getPlayer().getStamina();
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            float progress = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "progress"));
            stopDiggingIfHeightIsLower(progressBar);
            if ((stamina + damage) > staminaThreshold && progress == 0f) {
                switch (workMode) {
                    case Digging: {
                        boolean actionsMade = doDigActions();
                        if (!actionsMade) {
                            workMode = WorkMode.Unknown;
                            Utils.showOnScreenMessage("Digging is over");
                            clearInvalidCorners();
                        }
                        break;
                    }
                    case DiggingTile:{
                        boolean actionsMade = doDigActions();
                        if (!actionsMade) {
                            if (validCornersExists())
                                moveToNextTileCorner();
                            else {
                                Utils.movePlayerBySteps(diggingTileInfo.x * 4 + 2, diggingTileInfo.y * 4 + 2, STEPS, stepDuration);
                                if (areaAssistant.areaTourActivated()) {
                                    while(areaAssistant.areaTourActivated()) {
                                        areaAssistant.areaNextPosition();
                                        diggingTileInfo.x = (int)(Mod.hud.getWorld().getPlayerPosX() / 4);
                                        diggingTileInfo.y = (int)(Mod.hud.getWorld().getPlayerPosY() / 4);
                                        if (validCornersExists()) {
                                            moveToNextTileCorner();
                                            break;
                                        }
                                    }
                                } else {
                                    Utils.showOnScreenMessage("The digging is over");
                                    workMode = WorkMode.Unknown;
                                }
                            }
                        }
                        break;
                    }
                    case Levelling: {
                        if (levellingDone) {
                            finishLeveling();
                            break;
                        }
                        PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                                ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
                        if (pickableUnit != null && pickableUnit instanceof TilePicker) {
                            if (pickableUnit.getHoverName().contains("(flat)")) {
                                finishLeveling();
                                break;
                            }
                            Mod.hud.getWorld().getServerConnection().sendAction(shovelItem.getId(),
                                    new long[]{pickableUnit.getId()},
                                    PlayerAction.LEVEL);
                        } else {
                            Utils.consolePrint("Dirt tile is not selected!");
                        }
                        break;
                    }
                    case LevellingArea: {
                        int[][] area = Utils.getAreaCoordinates();
                        boolean actionTaken = false;
                        for (int i = 0; i < area.length; i += 2) {
                            if (i == 4) continue;
                            Tiles.Tile tileType = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(area[i][0], area[i][1]);
                            boolean levelableTile = tileType == Tiles.Tile.TILE_DIRT || tileType == Tiles.Tile.TILE_GRASS;
                            if (levelableTile && needLevelling(area[i][0], area[i][1])) {
                                Mod.hud.getWorld().getServerConnection().sendAction(shovelItem.getId(),
                                        new long[]{Tiles.getTileId(area[i][0], area[i][1], 1)},
                                        PlayerAction.LEVEL);
                                actionTaken = true;
                                break;
                            }
                        }
                        if (!actionTaken) {
                            areaAssistant.areaNextPosition();
                        }
                        break;
                    }
                }
            }
            sleep(timeout);
        }
    }

    private void finishLeveling() {
        workMode = WorkMode.Unknown;
        Utils.showOnScreenMessage("The levelling is over");
        levellingDone = false;
    }

    private boolean needLevelling(int x, int y) {
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        boolean highCornerSurroundedWithDirt = true;
        for(int dx = 0; dx < 2; dx++) {
            for(int dy = 0; dy < 2; dy++) {
                int h = (int) (Mod.hud.getWorld().getNearTerrainBuffer().getHeight(x + dx, y + dy) * 10);
                if (h > maxH) {
                    maxH = h;
                    if (maxH > diggingHeightLimit && highCornerSurroundedWithDirt) {
                        highCornerSurroundedWithDirt = !isRockTileNear(x + dx, y + dy);
                    }
                }
                if (h < minH)
                    minH = h;
            }
        }
        if (maxH > diggingHeightLimit) {
            if (!highCornerSurroundedWithDirt) {
                Utils.consolePrint("The tile (" + x + ", " + y + ") has higher rock corners");
                return false;
            } else
                return maxH != minH;
        }
        return minH < diggingHeightLimit && maxH != minH;
    }

    private void stopDiggingIfHeightIsLower(Object progressBar) throws Exception{
        int x = Math.round(Mod.hud.getWorld().getPlayerPosX() / 4);
        int y = Math.round(Mod.hud.getWorld().getPlayerPosY() / 4);
        int h = (int) (Mod.hud.getWorld().getNearTerrainBuffer().getHeight(x, y)* 10);
        String actionKey = "digging";
        if (surfaceMiningMode)
            actionKey = "mining";
        if (h <= diggingHeightLimit) {
            String actionName = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "title"));
            if (actionName != null && actionName.contains(actionKey))
                Mod.hud.sendAction(PlayerAction.STOP, 0);
        }
    }

    /**
     *
     * @return true if actions were made
     */
    private boolean doDigActions() {
        int x = Math.round(Mod.hud.getWorld().getPlayerPosX() / 4);
        int y = Math.round(Mod.hud.getWorld().getPlayerPosY() / 4);
        Tiles.Tile tileType = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(x, y);
        if (isCornerInvalid(x, y))
            return false;
        int h = (int) (Mod.hud.getWorld().getNearTerrainBuffer().getHeight(x, y)* 10);
        if (h > diggingHeightLimit) {
            if (Mod.hud.getWorld().getPlayerLayer() < 0)
                return false;
            int neededClicks = Math.min(h - diggingHeightLimit, clicks);
            if (surfaceMiningMode) {
                for (int i = 0; i < neededClicks; i++) {
                    if(isTileRock(tileType)) {
                        Mod.hud.getWorld().getServerConnection().sendAction(pickaxeItem.getId(),
                                new long[]{Tiles.getTileId(x, y, 0)},
                                PlayerAction.MINE_FORWARD);
                    }else if(isTileDirt(tileType)){
                        Mod.hud.getWorld().getServerConnection().sendAction(shovelItem.getId(),
                                new long[]{Tiles.getTileId(x, y, 0)},
                                digAction);
                    }
                }
                return true;
            } else {
                for (int i = 0; i < neededClicks; i++) {
                    Mod.hud.getWorld().getServerConnection().sendAction(shovelItem.getId(),
                            new long[]{Tiles.getTileId(x, y, 0)},
                            digAction);
                }
                return true;
            }
        }
        return false;
    }

    private boolean areSurroundingTilesRocks(int x, int y) {
        for(int dx = 0; dx < 2; dx++)
            for(int dy = 0; dy < 2; dy++) {
                Tiles.Tile tileType = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(x-dx, y-dy);
                if (tileType != Tiles.Tile.TILE_ROCK)
                    return false;
            }
        return true;
    }

    private boolean isRockTileNear(int x, int y) {
        for(int dx = 0; dx < 2; dx++)
            for(int dy = 0; dy < 2; dy++) {
                Tiles.Tile tileType = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(x-dx, y-dy);
                if (tileType == Tiles.Tile.TILE_ROCK) {
                    return true;
                }
            }
        return false;
    }

    private void handleInvalidCorner() {
        int x = Math.round(Mod.hud.getWorld().getPlayerPosX() / 4);
        int y = Math.round(Mod.hud.getWorld().getPlayerPosY() / 4);
        invalidCorners.add(new Pair<>(x, y));
    }

    private void clearInvalidCorners(){
        invalidCorners.clear();
    }

    private boolean validCornersExists() {
        if (workMode != WorkMode.DiggingTile)
            return false;
        for(int dx = 0; dx < 2; dx++)
            for(int dy = 0; dy < 2; dy++) {
                int x = diggingTileInfo.x + dx;
                int y = diggingTileInfo.y + dy;
                if (isCornerInvalid(x, y)) {
                    continue;
                }
                int h = (int) (Mod.hud.getWorld().getNearTerrainBuffer().getHeight(x, y) * 10);
                if (h > diggingHeightLimit)
                    return true;
            }
        return false;
    }

    private boolean isTileRock(int x, int y){
        Tiles.Tile t = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(x,y);
        return isTileRock(t);
    }
    private boolean isTileRock(Tiles.Tile t){
        return t == Tiles.Tile.TILE_ROCK;
    }
    private boolean isTileDirt(int x, int y){
        Tiles.Tile t = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(x,y);
        return isTileDirt(t);
    }
    private boolean isTileDirt(Tiles.Tile t){
        return Arrays.asList(DirtList).contains(t);
    }

    private boolean isCornerInvalid(int x, int y) {
        if (invalidCorners.contains(new Pair<>(x, y)))
            return true;
        if (surfaceMiningMode && !areSurroundingTilesRocks(x, y) && isTileRock(x,y) )
            return true;
        if (!surfaceMiningMode && isRockTileNear(x, y))
            return true;
        return false;
    }

    private void moveToNextTileCorner() throws InterruptedException{
        if (workMode != WorkMode.DiggingTile)
            return;
        int x = Math.round(Mod.hud.getWorld().getPlayerPosX() / 4);
        int y = Math.round(Mod.hud.getWorld().getPlayerPosY() / 4);
        if (Math.abs(x - diggingTileInfo.x) > 1 || Math.abs(y - diggingTileInfo.y) > 1) {
            workMode = WorkMode.Unknown;
            Utils.showOnScreenMessage("You moved from tile too far away");
            return;
        }
        int destX = x;
        int destY = y;
        int tries = 0;
        boolean cornerFound = false;
        while(tries++ <= 4) {
            if (destX == diggingTileInfo.x && destY == diggingTileInfo.y)
                ++destX;
            else if (destX == diggingTileInfo.x + 1 && destY == diggingTileInfo.y)
                ++destY;
            else if (destX == diggingTileInfo.x + 1 && destY == diggingTileInfo.y + 1)
                --destX;
            else if (destX == diggingTileInfo.x && destY == diggingTileInfo.y + 1)
                --destY;
            if (isCornerInvalid(destX, destY))
                continue;
            int h = (int) (Mod.hud.getWorld().getNearTerrainBuffer().getHeight(destX, destY) * 10);
            if (h > diggingHeightLimit) {
                cornerFound = true;
                break;
            }
        }
        if (cornerFound)
            Utils.movePlayerBySteps(destX * 4, destY * 4, STEPS, stepDuration);
    }

    private void toggleSurfaceMining() {
        if (!surfaceMiningMode) {
            pickaxeItem = Utils.getInventoryItem("pickaxe");
            if (pickaxeItem == null) {
                Utils.consolePrint("You don't have a pickaxe");
            }
            surfaceMiningMode = true;
            Utils.consolePrint("Surface mining is on!");
        } else {
            surfaceMiningMode = false;
            Utils.consolePrint("Surface mining is off!");
        }
    }

    private void toggleLevelling() {
        if (workMode != WorkMode.Levelling) {
            workMode = WorkMode.Levelling;
            levellingDone = false;
            Utils.consolePrint("The levelling of selected tile is on");
        } else {
            workMode = WorkMode.Unknown;
            Utils.consolePrint("The levelling is off");
        }
    }


    private void toggleLevellingArea(String [] input) {
        if (workMode != WorkMode.LevellingArea) {
            if (input == null || input.length != 1) {
                printInputKeyUsageString(DiggerBot.InputKey.la);
                return;
            }
            try {
                diggingHeightLimit = Integer.parseInt(input[0]);
            } catch (NumberFormatException e) {
                Utils.consolePrint("Wrong height value!");
                return;
            }
            workMode = WorkMode.LevellingArea;
            Utils.consolePrint("The levelling of surrounding area is on");
            areaAssistant.setMoveRightDistance(1);
        } else {
            workMode = WorkMode.Unknown;
            Utils.consolePrint("The area levelling is off");
        }
    }

    private void toggleDigToPileAction() {
        if (digAction.getId() == PlayerAction.DIG_TO_PILE.getId()) {
            digAction = PlayerAction.DIG;
            Utils.consolePrint("The " + getClass().getSimpleName() + " will make default \"Dig\" actions");
        } else {
            digAction = PlayerAction.DIG_TO_PILE;
            Utils.consolePrint("The " + getClass().getSimpleName() + " will make \"Dig to pile\" actions");
        }
    }

    private void toggleDigging(String []input) {
        if (workMode != WorkMode.Digging) {
            if (input == null || input.length != 1) {
                printInputKeyUsageString(DiggerBot.InputKey.d);
                return;
            }
            try {
                diggingHeightLimit = Integer.parseInt(input[0]);
                workMode = WorkMode.Digging;
                Utils.consolePrint(this.getClass().getSimpleName() +
                        " will dig until " + diggingHeightLimit + " height is reached");
            } catch (NumberFormatException e) {
                Utils.consolePrint("Wrong height value!");
            }
        } else {
            workMode = WorkMode.Unknown;
            Utils.consolePrint("Digging was disabled");
        }
    }

    private void toggleTileDiggingMode(String []input) {
        if (workMode != WorkMode.DiggingTile) {
            if (input == null || input.length != 1) {
                printInputKeyUsageString(DiggerBot.InputKey.dtile);
                return;
            }
            try {
                diggingHeightLimit = Integer.parseInt(input[0]);
            } catch (NumberFormatException e) {
                Utils.consolePrint("Wrong height value!");
                return;
            }
            try {
                diggingTileInfo = new DiggingTileInfo();
                diggingTileInfo.x = (int)(Mod.hud.getWorld().getPlayerPosX() / 4);
                diggingTileInfo.y = (int)(Mod.hud.getWorld().getPlayerPosY() / 4);
                moveToNextTileCorner();
                workMode = WorkMode.DiggingTile;
                Utils.consolePrint("The digging of tile (" +diggingTileInfo.x + "," + diggingTileInfo.y + ") is on");
                areaAssistant.setMoveRightDistance(2);
            } catch(Exception e) {
                Utils.consolePrint("Error on turning the tile digging on");
            }
        } else {
            workMode = WorkMode.Unknown;
            Utils.consolePrint("Digging of tile was disabled");
        }
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(DiggerBot.InputKey.s);
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

    private void setClicksNumber(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(DiggerBot.InputKey.c);
        else {
            try {
                int clicks = Integer.parseInt(input[0]);
                setClicks(clicks);
            } catch (Exception e) {
                Utils.consolePrint("Wrong value!");
            }
        }
    }

    private void setClicks(int clicks) {
        this.clicks = clicks;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " chops each time");
    }

    private void toggleToolRepairing() {
        toolRepairing = !toolRepairing;
        Utils.consolePrint("The repairing of the tool is " + (toolRepairing ?"on":"off"));
    }

    private enum WorkMode{
        Unknown,
        Digging,
        DiggingTile,
        Levelling,
        LevellingArea
    }

    private static class DiggingTileInfo {
        int x;
        int y;
    }

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        d("Toggle the digging until the specified height is reached", "height(in slopes)"),
        dtp("Toogle the use of \"Dig to pile\" action", ""),
        dtile("Toggle the digging until the specified height is reached on all 4 corners of current tile", "height(in slopes)"),
        c("Set the amount of actions the bot will do each time", "c(integer value)"),
        l("Toggle the levelling of selected tile", ""),
        la("Toggle the levelling of area around player", "height(in slopes)"),
        tr("Toggle the repairing of the tool", ""),
        sm("Toggle the surface mining. The bot will do the same but with the pickaxe on the rock", "");

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
