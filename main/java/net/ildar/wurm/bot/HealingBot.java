package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.*;

public class HealingBot extends Bot {
    private final Set<String> WOUND_NAMES = new HashSet<>(Arrays.asList("Cut", "Bite", "Bruise", "Burn", "Hole", "Acid", "Infection"));
    private float minDamage = 0;

    public static BotRegistration getRegistration() {
        return new BotRegistration(HealingBot.class,
                "Heals the player's wounds with cotton found in inventory", "h");
    }

    public HealingBot() {
        registerInputHandler(HealingBot.InputKey.md, this::setMinimumDamage);
    }

    @Override
    protected void work() throws Exception{
        setTimeout(500);
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        while (isActive()) {
            waitOnPause();
            float progress = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "progress"));
            if (progress != 0f) {
                sleep(timeout);
                continue;
            }
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            if (damage == 0) {
                Utils.consolePrint("The player is fully healed");
                return;
            }
            InventoryMetaItem cottonItem = Utils.getInventoryItems("cotton").stream().filter(item -> item.getBaseName().equals("cotton")).findFirst().orElse(null);
            if (cottonItem == null) {
                Utils.consolePrint("The player don't have a cotton!");
                return;
            }
            List<InventoryMetaItem> inventoryItems = new ArrayList<>();
            inventoryItems.add(Utils.getRootItem(Mod.hud.getInventoryWindow().getInventoryListComponent()));
            List<InventoryMetaItem> wounds = new ArrayList<>();
            while (inventoryItems.size() > 0) {
                InventoryMetaItem item = inventoryItems.get(0);
                if (WOUND_NAMES.contains(item.getBaseName())
                        && !item.getDisplayName().contains("bandaged")
                        && item.getDamage() > minDamage)
                    wounds.add(item);
                if (item.getChildren() != null)
                    inventoryItems.addAll(item.getChildren());
                inventoryItems.remove(item);
            }
            if (wounds.size() == 0) {
                Utils.consolePrint("All wounds were treated");
                return;
            }
            wounds.sort(Comparator.comparingDouble(InventoryMetaItem::getDamage).reversed());
            int maxActionNumber = Utils.getMaxActionNumber();
            int i = 0;
            for (InventoryMetaItem wound : wounds) {
                Mod.hud.getWorld().getServerConnection().sendAction(cottonItem.getId(), new long[]{wound.getId()}, PlayerAction.FIRSTAID);
                if (++i >= maxActionNumber)
                    break;
            }
            sleep(timeout);
        }
    }

    private void setMinimumDamage(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(HealingBot.InputKey.md);
            return;
        }
        try {
            minDamage = Float.parseFloat(input[0]);
            Utils.consolePrint(String.format("The wound must have damage greater than %.2f in order to be treated", minDamage));
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong value!");
        }
    }


    private enum InputKey implements Bot.InputKey {
        md("Set the minimum damage of the wound to be treated", "min_damage");

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
