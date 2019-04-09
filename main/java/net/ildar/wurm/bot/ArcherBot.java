package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.client.renderer.gui.PaperDollInventory;
import com.wurmonline.client.renderer.gui.PaperDollSlot;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.Map;

public class ArcherBot extends Bot {
    private static boolean stringBreaks;

    private float staminaThreshold;
    private InventoryMetaItem bow;

    public static BotRegistration getRegistration() {
        return new BotRegistration(ArcherBot.class,
                "Automatically shoots at selected target with currently equipped bow. " +
                        "When the string breaks tries to place a new one. " +
                        "Deactivates on target death.",
                "ar");
    }

    public ArcherBot() {
        registerInputHandler(ArcherBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(ArcherBot.InputKey.string, input -> stringTheBow());
    }

    @Override
    public void work() throws Exception{
        setStaminaThreshold(0.9f);
        PaperDollInventory pdi = ReflectionUtil.getPrivateField(Mod.hud,
                ReflectionUtil.getField(Mod.hud.getClass(), "paperdollInventory"));
        Map<Long, PaperDollSlot> frameList = ReflectionUtil.getPrivateField(pdi,
                ReflectionUtil.getField(PaperDollInventory.class, "frameList"));
        for (Map.Entry<Long, PaperDollSlot> frame : frameList.entrySet()) {
            PaperDollSlot slot = frame.getValue();
            if (slot == null || slot.getEquippedItem() == null) continue;
            if (slot.getEquipmentSlot() == 1) {
                bow = slot.getEquippedItem().getItem();
                Utils.consolePrint(this.getClass().getSimpleName() + " will use " + bow.getDisplayName() + " with QL:" + bow.getQuality() + " DMG:" + bow.getDamage());
            }
        }
        if (bow == null) {
            Utils.consolePrint("Equip the bow first!");
            deactivate();
            return;
        }

        PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
        if (pickableUnit == null){
            Utils.consolePrint("Select mob!");
            deactivate();
            return;
        }
        Utils.consolePrint(this.getClass().getSimpleName() + " will shoot at " + pickableUnit.getHoverName());
        long mobId = pickableUnit.getId();
        boolean isArcheryTarget=pickableUnit.getHoverName().contains("archery target");

        int maxActions = Utils.getMaxActionNumber();
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow,
                ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            float stamina = Mod.hud.getWorld().getPlayer().getStamina();
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            float progress = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "progress"));
            if ((stamina+damage) > staminaThreshold && creationWindow.getActionInUse() == 0 && progress == 0f) {
                if (stringBreaks) {
                        InventoryMetaItem bowstring = Utils.getInventoryItem("bow string");
                        if (bowstring != null) {
                            Mod.hud.getWorld().getServerConnection().sendAction(bowstring.getId(),
                                    new long[]{bow.getId()}, new PlayerAction("",(short) 132, PlayerAction.ANYTHING));//change bowstring
                        }
                }
                for (int i = 0; i < maxActions; i++)
                    Mod.hud.getWorld().getServerConnection().sendAction(bow.getId(), new long[]{mobId}, (!isArcheryTarget ? PlayerAction.SHOOT : new PlayerAction("",(short) 134, PlayerAction.ANYTHING)));

                ServerConnectionListenerClass sscc = Mod.hud.getWorld().getServerConnection().getServerConnectionListener();
                Map<Long, CreatureCellRenderable> creatures = ReflectionUtil.getPrivateField(sscc,
                        ReflectionUtil.getField(sscc.getClass(), "creatures"));
                boolean mobAlive = false;
                if (creatures!=null && !isArcheryTarget)
                    for(Map.Entry<Long, CreatureCellRenderable> entry:creatures.entrySet())
                        if (entry.getValue().getId() == mobId) mobAlive = true;
                if (!mobAlive && !isArcheryTarget){
                    Utils.consolePrint("Mob dead or too far away!");
                    Utils.showOnScreenMessage("Deactivating archerbot!");
                    deactivate();
                }
            }
            sleep(timeout);
        }
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You string the "), () -> stringBreaks = false);
        registerEventProcessor(message -> message.contains("The string breaks!"), () -> stringBreaks = true);
        registerMessageProcessor(":Combat", message -> message.contains("The string breaks!"), () -> stringBreaks = true);
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ArcherBot.InputKey.s);
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

    private void stringTheBow() {
        Utils.consolePrint(getClass().getSimpleName() + " will try to string the bow.");
        stringBreaks = true;
    }

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        string("String the current bow with a string",
                "");
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
