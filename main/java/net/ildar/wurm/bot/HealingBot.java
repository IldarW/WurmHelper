package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.*;

public class HealingBot extends Bot {

    @Override
    protected void work() throws Exception{
        setTimeout(500);
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        while(isActive()) {
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
            InventoryMetaItem cottonItem = Utils.getInventoryItem("cotton");
            if (cottonItem == null) {
                Utils.consolePrint("The player don't have a cotton!");
                return;
            }
            List<InventoryMetaItem> inventoryItems = new ArrayList<>();
            inventoryItems.add(Utils.getRootItem(Mod.hud.getInventoryWindow().getInventoryListComponent()));
            Set<String> woundNames = new HashSet<>(Arrays.asList("Cut", "Bite", "Bruise", "Burn", "Hole", "Acid", "Infection"));
            List<InventoryMetaItem> wounds = new ArrayList<>();
            while (inventoryItems.size() > 0) {
                InventoryMetaItem item = inventoryItems.get(0);
                if (woundNames.contains(item.getBaseName())
                        && !item.getDisplayName().contains("bandaged"))
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
}
