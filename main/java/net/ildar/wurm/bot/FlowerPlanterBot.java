package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.GrassData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.List;

public class FlowerPlanterBot extends Bot {
    private float staminaThreshold;
    private long sickleId;
    private long shovelId;

    public static BotRegistration getRegistration() {
        return new BotRegistration(FlowerPlanterBot.class,
                "Skills up player's gardening skill by planting and picking flowers in surrounding area",
                "fp");
    }

    public FlowerPlanterBot() {
        registerInputHandler(FlowerPlanterBot.InputKey.s, this::setStaminaThreshold);
    }

    @Override
    public void work() throws Exception{
        setTimeout(300);
        setStaminaThreshold(0.96f);

        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Method sendCreateAction = ReflectionUtil.getMethod(CreationWindow.class, "sendCreateAction");
        sendCreateAction.setAccessible(true);
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow,
                ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        int maxActions = Utils.getMaxActionNumber();
        InventoryMetaItem sickle = Utils.getInventoryItem("sickle");
        InventoryMetaItem shovel = Utils.getInventoryItem("shovel");
        if (sickle != null)
            sickleId = sickle.getId();
        if (shovel != null)
            shovelId = shovel.getId();
        if (sickleId == 0) {
            Utils.consolePrint("You don't have a sickle! " + this.getClass().getSimpleName() + " won't start");
            deactivate();
            return;
        }
        if (shovelId == 0) {
            Utils.consolePrint("You don't have a shovel! " + this.getClass().getSimpleName() + " won't start");
            deactivate();
            return;
        }

        BotState state = BotState.PLANT;
        while (isActive()) {
            waitOnPause();
            float stamina = Mod.hud.getWorld().getPlayer().getStamina();
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            float progress = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "progress"));
            int checkedtiles[][] = Utils.getAreaCoordinates();
            int sentactions = 0;

            if ((stamina+damage) > staminaThreshold && creationWindow.getActionInUse() == 0 && progress == 0f) {
                switch (state) {
                    case PLANT:
                        long[] flowerIds = new long[maxActions];
                        int flowersFound = 0;
                        List<InventoryMetaItem> bouquets = Utils.getInventoryItems("bouquet of");
                        for (InventoryMetaItem item : bouquets) {
                            flowerIds[flowersFound++] = item.getId();
                            if (flowersFound >= maxActions)
                                break;
                        }
                        for(int i = 0; i < 9 && sentactions < maxActions; i++) {
                            Tiles.Tile type = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(checkedtiles[i][0], checkedtiles[i][1]);
                            if (type.tilename.equals("Dirt")) {
                                Mod.hud.getWorld().getServerConnection().sendAction(flowerIds[sentactions],
                                        new long[]{Tiles.getTileId(checkedtiles[i][0], checkedtiles[i][1], 0)},
                                        new PlayerAction("",(short)186, PlayerAction.ANYTHING));
                                ++sentactions;
                            }
                        }
                        state = BotState.PICK;
                        break;
                    case PICK:
                        for(int i = 0; i < 9 && sentactions < maxActions; i++) {
                            Tiles.Tile type = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(checkedtiles[i][0], checkedtiles[i][1]);
                            byte data = Mod.hud.getWorld().getNearTerrainBuffer().getData(checkedtiles[i][0], checkedtiles[i][1]);
                            if (type.isGrass() && GrassData.getFlowerTypeName(data).contains("flowers")) {
                                Mod.hud.getWorld().getServerConnection().sendAction(sickleId,
                                        new long[]{Tiles.getTileId(checkedtiles[i][0], checkedtiles[i][1], 0)},
                                        new PlayerAction("",(short)187, PlayerAction.ANYTHING));
                                ++sentactions;
                            }
                        }
                        state = BotState.CULTIVATE;
                        break;
                    case CULTIVATE:
                        for(int i = 0; i < 9 && sentactions < maxActions; i++) {
                            Tiles.Tile type = Mod.hud.getWorld().getNearTerrainBuffer().getTileType(checkedtiles[i][0], checkedtiles[i][1]);
                            byte data = Mod.hud.getWorld().getNearTerrainBuffer().getData(checkedtiles[i][0], checkedtiles[i][1]);
                            if (type.isGrass()&& !GrassData.getFlowerTypeName(data).contains("flowers")) {
                                Mod.hud.getWorld().getServerConnection().sendAction(shovelId,
                                        new long[]{Tiles.getTileId(checkedtiles[i][0], checkedtiles[i][1], 0)},
                                        PlayerAction.CULTIVATE);
                                ++sentactions;
                            }
                        }
                        state = BotState.PLANT;
                        break;
                }
            }

            sleep(timeout);
        }
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(FlowerPlanterBot.InputKey.s);
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

    enum BotState{
        PLANT,
        PICK,
        CULTIVATE
    }

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)");

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
