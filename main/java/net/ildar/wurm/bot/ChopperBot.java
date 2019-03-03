package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.ConcurrentModificationException;
import java.util.Map;

public class ChopperBot extends Bot {
    private static float distance = 4;
    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private float staminaThreshold;
    private int clicks;

    public static BotRegistration getRegistration() {
        return new BotRegistration(ChopperBot.class,
                "Automatically chops felled trees near player",
                "ch");
    }

    public ChopperBot() {
        registerInputHandler(ChopperBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(ChopperBot.InputKey.d, this::setDistance);
        registerInputHandler(ChopperBot.InputKey.c, this::setClickNumber);

        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(1);
    }

    @Override
    public void work() throws Exception{
        setStaminaThreshold(0.96f);
        setClicks(Utils.getMaxActionNumber());
        InventoryMetaItem hatchet = Utils.getInventoryItem("hatchet");
        long hatchetId;
        if (hatchet == null) {
            Utils.consolePrint("You don't have a hatchet!");
            return;
        } else {
            hatchetId = hatchet.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + hatchet.getDisplayName() + " to chop shriveled trees.");
            Utils.consolePrint("QL:" + hatchet.getQuality() + " DMG:" + hatchet.getDamage());
        }
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow,
                ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        ServerConnectionListenerClass sscc = Mod.hud.getWorld().getServerConnection().getServerConnectionListener();
        while (isActive()) {
            waitOnPause();
            float stamina = Mod.hud.getWorld().getPlayer().getStamina();
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            float progress = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "progress"));
            if ((stamina+damage) > staminaThreshold && progress == 0f) {
                Map<Long, GroundItemCellRenderable> groundItems = ReflectionUtil.getPrivateField(sscc,
                        ReflectionUtil.getField(sscc.getClass(), "groundItems"));
                float x = Mod.hud.getWorld().getPlayerPosX();
                float y = Mod.hud.getWorld().getPlayerPosY();
                boolean didSomething = false;
                if (groundItems.size() > 0) {
                    try {
                        for (Map.Entry<Long, GroundItemCellRenderable> entry : groundItems.entrySet()) {
                            GroundItemData groundItemData = ReflectionUtil.getPrivateField(entry.getValue(),
                                    ReflectionUtil.getField(entry.getValue().getClass(), "item"));
                            float itemX = groundItemData.getX();
                            float itemY = groundItemData.getY();
                            if (Math.sqrt(Math.pow(itemX - x, 2) + Math.pow(itemY - y, 2)) <= distance)
                                if (groundItemData.getName().contains("felled tree")) {
                                    for (int i = 0; i < clicks; i++)
                                        Mod.hud.getWorld().getServerConnection().sendAction(hatchetId, new long[]{groundItemData.getId()}, PlayerAction.CHOP_UP);
                                    didSomething = true;
                                    break;
                                }
                        }
                    } catch (ConcurrentModificationException e) {
                        Utils.consolePrint("Got concurrent modification exception!");
                    }
                }
                if (!didSomething) {
                    areaAssistant.areaNextPosition();
                    continue;
                }
            }
            sleep(timeout);
        }
    }

    private void setDistance(String[] input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(ChopperBot.InputKey.d);
            return;
        }
        try {
            distance = Float.parseFloat(input[0]);
            Utils.consolePrint("New lookup distance is " + distance + " meters");
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong distance value!");
        }
    }

    private void setStaminaThreshold(String[] input) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ChopperBot.InputKey.s);
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

    private void setClickNumber(String[] input) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ChopperBot.InputKey.c);
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

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        d("Set the distance the bot should look around player in search for a felled tree",
                "distance(in meters)"),
        c("Set the amount of chops the bot will do each time", "c(integer value)");

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