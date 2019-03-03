package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Creature;
import net.ildar.wurm.Mod;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

import static net.ildar.wurm.Utils.*;

public class GroomerBot extends Bot {

    private static float distance = 4;
    private static boolean verbose;
    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private List<Creature> creatures = new ArrayList<>();
    private int queue;
    private ServerConnectionListenerClass listenerClass;
    private List<Long> creaturesInAction = new ArrayList<>();


    public GroomerBot() {
        registerInputHandler(GroomerBot.InputKey.d, this::setDistance);
        registerInputHandler(GroomerBot.InputKey.v, inputData -> toggleVerbose());

        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(1);
    }

    public static BotRegistration getRegistration() {
        return new BotRegistration(GroomerBot.class,
                "Groomer Bot",
                "gr");
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected void work() throws Exception {

        registerEventProcessors();

        setStaminaThreshold(0.96f);
        InventoryMetaItem groomingBrush = getInventoryItem("grooming brush");

        long groomingBrushId;
        if (groomingBrush == null) {
            consolePrint("You don't have a grooming brush!");
            return;
        } else {
            groomingBrushId = groomingBrush.getId();
            consolePrint(this.getClass().getSimpleName() + " will use " + groomingBrush.getDisplayName() + " to groom animals.");
            consolePrint("QL:" + groomingBrush.getQuality() + " DMG:" + groomingBrush.getDamage());
        }

        listenerClass = Mod.hud.getWorld().getServerConnection().getServerConnectionListener();
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow,
                ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        int maxActions = getMaxActionNumber();
        while (isActive()) {

            waitOnPause();
            float stamina = Mod.hud.getWorld().getPlayer().getStamina();
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            float progress = ReflectionUtil.getPrivateField(progressBar,
                    ReflectionUtil.getField(progressBar.getClass(), "progress"));
            scanTerritory();
            if ((stamina + damage) > staminaThreshold && progress == 0f) {
                queue = maxActions;
                creatures.forEach(creature -> {
                    if (!creature.isGroomed()
                            && creature.getLastGroom() + 1000 * 60 * 60 < System.currentTimeMillis() // wait one hour from last grooming
                            && (creature.isAvailable() || creature.getLastAction() + 1000 * 10 < System.currentTimeMillis())  // wait 5 seconds from last try
                            && queue <= maxActions) {
                        Mod.hud.getWorld().getServerConnection().sendAction(groomingBrushId, new long[]{creature.getId()}, PlayerAction.GROOM);
                        if (!creaturesInAction.contains(creature.getId())) {
                            creaturesInAction.add(creature.getId());
                        }
                        queue++;
                    }
                });
                if (creatures.stream().noneMatch(Creature::isGroomed) && areaAssistant.areaTourActivated()) {
                    areaAssistant.areaNextPosition();
                    continue;
                }
            }
            sleep(timeout);
        }

    }

    private void setDistance(String[] input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(InputKey.d);
            return;
        }
        try {
            distance = Float.parseFloat(input[0]);
            consolePrint("New lookup distance is " + distance + " meters");
        } catch (NumberFormatException e) {
            consolePrint("Wrong distance value!");
        }
    }

    private void scanTerritory() {
        try {
            Map<Long, CreatureCellRenderable> aCreatures = ReflectionUtil.getPrivateField(listenerClass,
                    ReflectionUtil.getField(listenerClass.getClass(), "creatures"));
            for (Map.Entry<Long, CreatureCellRenderable> entry : aCreatures.entrySet()) {

                Creature creature = new Creature(entry.getKey(), entry.getValue(), entry.getValue().getModelName().toString(), entry.getValue().getHoverName());
                if (!creature.isGroomableMob()) {
                    continue;
                }
                long currDistance = (long) getDistance(creature.getX(), creature.getY());
                if (currDistance < distance) {
                    if (!creatures.contains(creature)) {
                        creatures.add(creature);
                        if (verbose) {
                            consolePrint("Add " + creature.getHoverName() + " " + creature.getId() + " at " + currDistance + " meters");
                        }
                    }
                } else {
                    if (creatures.contains(creature)) {
                        creatures.remove(creature);
                        if (verbose) {
                            consolePrint("Remove " + creature.getHoverName() + " " + creature.getId() + " at " + currDistance + " meters");
                        }
                    }
                }
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            if (verbose) {
                e.printStackTrace();
            }
        }
    }

    private void toggleVerbose() {
        verbose = !verbose;
        consolePrint("Verbose mode is " + (verbose ? "on" : "off"));
    }

    private void setProcessed() {
        if (creaturesInAction.size() > 0 && creatures.size() > 0) {
            creatures.stream()
                    .filter(c -> creaturesInAction.get(0) == c.getId())
                    .findFirst().ifPresent(
                    currCreature -> {
                        currCreature.setGroomed(true);
                        currCreature.setLastGroom(System.currentTimeMillis());
                        creaturesInAction.remove(currCreature.getId());
                    });
        }
    }

    private void setNotAvailable(){
        if (creaturesInAction.size() > 0 && creatures.size() > 0) {
            creatures.stream()
                    .filter(c -> creaturesInAction.get(0) == c.getId())
                    .findFirst().ifPresent(
                    currCreature -> {
                        currCreature.setAvailable(false);
                        currCreature.setLastAction(System.currentTimeMillis());
                        creaturesInAction.remove(currCreature.getId());
                        if (verbose) consolePrint(currCreature.getHoverName() + " is not available now. Skipped.");
                    });
        }
    }

    private void registerEventProcessors() {
        registerEventProcessor(message ->
                message.contains("is already well tended") || message.contains("seems pleased."), this::setProcessed);
        registerEventProcessor(message ->
                message.contains("is in the way.") || message.contains("You are too far away to do that."), this::setNotAvailable);
    }

    private enum InputKey implements Bot.InputKey {
        d("Set the distance the bot should look around player in search", "distance(in meters))"),
        v("Verbose mode", "");

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
