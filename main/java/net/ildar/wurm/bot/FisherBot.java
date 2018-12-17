package net.ildar.wurm.bot;

import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.Tiles;
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

        World world = Mod.hud.getWorld();
        long tileId = Tiles.getTileId(
                world.getPlayerCurrentTileX(),
                world.getPlayerCurrentTileY(),
                0
        );

        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        while (isActive())
        {
            float progress = ReflectionUtil.getPrivateField(progressBar, ReflectionUtil.getField(progressBar.getClass(), "progress"));

            if (repairInstrument && fishingRod.getDamage() > 10)
            {
                Mod.hud.sendAction(
                        PlayerAction.REPAIR,
                        fishingRod.getId()
                );
            }

            if (progress == 0f)
            {
                world.getServerConnection().sendAction(
                        fishingRod.getId(),
                        new long[]{tileId},
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