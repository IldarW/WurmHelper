package net.ildar.wurm.bot;

import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

public class FisherBot extends Bot
{
    private boolean repairInstrument;
    private boolean lineBreaks;

    /*public static BotRegistration getRegistration() {
        return new BotRegistration(FisherBot.class,
                "Catches and cuts fish", "fsh");
    }*/

    public FisherBot()
    {
        registerInputHandler(FisherBot.InputKey.r, input -> toggleRepairInstrument());
        registerInputHandler(FisherBot.InputKey.line, input -> putFishingLine());

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
        lineBreaks=fishingRod.getBaseName().contains("unstrung");
        World world = Mod.hud.getWorld();
        long tileId = Tiles.getTileId(
                world.getPlayerCurrentTileX(),
                world.getPlayerCurrentTileY(),
                0
        );

        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        registerEventProcessors();
        while (isActive())
        {
            waitOnPause();
            float progress = ReflectionUtil.getPrivateField(progressBar, ReflectionUtil.getField(progressBar.getClass(), "progress"));

            if (repairInstrument && fishingRod.getDamage() > 10)
            {
                Mod.hud.sendAction(
                        PlayerAction.REPAIR,
                        fishingRod.getId()
                );
            }

            if (progress == 0f && creationWindow.getActionInUse() == 0) 
            {
                if (Tiles.getTileId(world.getPlayerCurrentTileX(), world.getPlayerCurrentTileY(), 0) != tileId)
                    tileId = Tiles.getTileId(world.getPlayerCurrentTileX(), world.getPlayerCurrentTileY(), 0);

                if (lineBreaks) 
                {
                    InventoryMetaItem fishingLine = Utils.getInventoryItem("fine fishing line");
                    if (fishingLine == null) 
                    {
                        fishingLine = Utils.getInventoryItem("fishing line");
                    }
                    if (fishingLine != null) 
                    {
                        Mod.hud.getWorld().getServerConnection().sendAction(fishingLine.getId(),
                                new long[]{fishingRod.getId()}, new PlayerAction("",(short) 132, PlayerAction.ANYTHING));
                    }
                    else
                    {
                        Utils.consolePrint("You don't have any fishing line");
                    }
                }
                if (fishingRod.getDamage() > 1)
                    Mod.hud.sendAction(PlayerAction.REPAIR, fishingRod.getId());

                world.getServerConnection().sendAction(
                        fishingRod.getId(),
                        new long[]{tileId},
                        PlayerAction.FISH
                );
            }

            sleep(timeout);
        }
    }

    private void toggleRepairInstrument() {
        repairInstrument = !repairInstrument;
        if (repairInstrument)
            Utils.consolePrint("Rod auto repairing is on!");
        else
            Utils.consolePrint("Rod auto repairing is off!");
    }

    private void putFishingLine() {
        Utils.consolePrint(getClass().getSimpleName() + " will try to put a fishing line.");
        lineBreaks = true;
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You string the "), () -> lineBreaks = false);
        registerEventProcessor(message -> message.contains("The line snaps, and the fish escapes!"), () -> lineBreaks = true);
    }


    private enum InputKey implements Bot.InputKey {
        r("Toggle the source item repairing(on the left side of crafting window). " +
                "Usually it is an instrument. When the source item gets 10% damage player will repair it automatically", ""),
        line("Replace a fishing line on the current rod",
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