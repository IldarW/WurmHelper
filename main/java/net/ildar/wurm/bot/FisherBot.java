package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

public class FisherBot extends Bot
{
    private boolean repairInstrument;

    private enum InputKey
    {
        r("Toggle the source item repairing(on the left side of crafting window). " +
                "Usually it is an instrument. When the source item gets 10% damage player will repair it automatically", "");

        public String description;
        public String usage;

        InputKey(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }
    }

    public FisherBot()
    {
        registerInputHandler(InputKey.r, input -> toggleRepairInstrument());

        repairInstrument = true;
    }

    @Override
    public void work() throws Exception
    {
        InventoryMetaItem fishingRod = Utils.getInventoryItem("fine fishing rod");
        if (fishingRod == null)
        {
            fishingRod = Utils.getInventoryItem("fishing rod");
            if (fishingRod == null) {
                Utils.consolePrint("You don't have any fishing rod");
                deactivate();
                return;
            }

        }
        Utils.consolePrint(this.getClass().getSimpleName() + " will use " + fishingRod.getBaseName());

        PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(), ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
        if (pickableUnit == null)
        {
            Utils.consolePrint("Select water tile");
            deactivate();
            return;
        }
        Utils.consolePrint(this.getClass().getSimpleName() + " will prospect " + pickableUnit.getHoverName());

        long fishingRodId = fishingRod.getId();
        long waterTileId = pickableUnit.getId();

        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        while (isActive())
        {
            float progress = ReflectionUtil.getPrivateField(progressBar, ReflectionUtil.getField(progressBar.getClass(), "progress"));

            if (repairInstrument && fishingRod.getDamage() > 10)
            {
                Mod.hud.sendAction(
                        PlayerAction.REPAIR,
                        fishingRodId
                );
            }

            if (progress == 0f)
            {
                Mod.hud.getWorld().getServerConnection().sendAction(
                        fishingRodId,
                        new long[]{waterTileId},
                        PlayerAction.FISH
                );
            }

            sleep(timeout);
        }
    }

    private void toggleRepairInstrument(){
        repairInstrument = !repairInstrument;
        if (repairInstrument)
            Utils.consolePrint("Rod auto repairing is on!");
        else
            Utils.consolePrint("Rod auto repairing is off!");
    }

}