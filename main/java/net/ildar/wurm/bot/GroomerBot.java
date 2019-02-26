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
    private float staminaThreshold;
    private boolean processed = false;
    private int processedCount = 0;
    private List<Creature> creatures = new ArrayList<>();
    private long lastActionTime;
    private int queue;
    private ServerConnectionListenerClass listenerClass;


    public GroomerBot() {
        registerInputHandler(GroomerBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(GroomerBot.InputKey.d, this::toggleDistance);
        registerInputHandler(GroomerBot.InputKey.v, inputData -> toggleVerbose());
        registerInputHandler(GroomerBot.InputKey.area, this::toggleAreaMode);
        registerInputHandler(GroomerBot.InputKey.area_speed, this::toggleAreaModeSpeed);

        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(1);
    }

    public static BotRegistration getRegistration() {
        return new BotRegistration(GroomerBot.class,
                "Groomer Bot",
                "gr");
    }

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
                lastActionTime = 0;
                processedCount = creatures.size();
                queue = maxActions;
                creatures.forEach(creature -> {
                    if (creature.getStatus() != Creature.PROCESSED && creature.getLastGroom() + 1000 * 60 * 60 < System.currentTimeMillis() && queue <= maxActions) {
                        processed = false;
                        Mod.hud.getWorld().getServerConnection().sendAction(groomingBrushId, new long[]{creature.getId()}, PlayerAction.GROOM);
                        queue++;
                        lastActionTime = System.currentTimeMillis();
                        try {
                            sleep(300);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (processed) {
                            creature.setStatus(Creature.PROCESSED);
                            creature.setLastGroom(System.currentTimeMillis());
                            processedCount--;
                        }
                    }
                });
                if (processedCount == creatures.size() && areaAssistant.areaTourActivated()) {
                    areaAssistant.areaNextPosition();
                    continue;
                }
            }
            sleep(timeout);
        }

    }

    private void toggleDistance(String[] input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(GroomerBot.InputKey.d);
            return;
        }
        try {
            distance = Float.parseFloat(input[0]);
            consolePrint("New lookup distance is " + distance + " meters");
        } catch (NumberFormatException e) {
            consolePrint("Wrong distance value!");
        }
    }

    private void setStaminaThreshold(String[] input) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(GroomerBot.InputKey.s);
        else {
            try {
                float threshold = Float.parseFloat(input[0]);
                setStaminaThreshold(threshold);
            } catch (Exception e) {
                consolePrint("Wrong threshold value!");
            }
        }
    }

    private void setStaminaThreshold(float s) {
        staminaThreshold = s;
        consolePrint("Current threshold for stamina is " + staminaThreshold);
    }

    private void toggleAreaMode(String[] input) {
        boolean successfullAreaModeChange = areaAssistant.toggleAreaTour(input);
        if (!successfullAreaModeChange) {
            printInputKeyUsageString(GroomerBot.InputKey.area);
        }
    }

    private void toggleAreaModeSpeed(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(GroomerBot.InputKey.area_speed);
            return;
        }
        float speed;
        try {
            speed = Float.parseFloat(input[0]);
            if (speed < 0) {
                consolePrint("Speed can not be negative");
                return;
            }
            if (speed == 0) {
                consolePrint("Speed can not be equal to 0");
                return;
            }
            areaAssistant.setStepTimeout((long) (1000 / speed));
            consolePrint(String.format("The speed for area mode was set to %.2f", speed));
        } catch (NumberFormatException e) {
            consolePrint("Wrong speed value");
        }
    }

    private void scanTerritory() {
        try {
            Map<Long, CreatureCellRenderable> aCreatures = ReflectionUtil.getPrivateField(listenerClass,
                    ReflectionUtil.getField(listenerClass.getClass(), "creatures"));
            for (Map.Entry<Long, CreatureCellRenderable> entry : aCreatures.entrySet()) {

                Creature creature = new Creature(entry.getKey(), entry.getValue(), entry.getValue().getModelName().toString(), entry.getValue().getHoverName());
                if (!creature.isMob()) {
                    continue;
                }
                long currDistance = getDistance(creature.getX(), creature.getY());
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
        } catch (ConcurrentModificationException e) {
            consolePrint("Got concurrent modification exception!");
            if (verbose) {
                e.printStackTrace();
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void toggleVerbose() {
        verbose = !verbose;
        consolePrint("Verbose mode is " + (verbose ? "on" : "off"));
    }

    private void registerEventProcessors() {
        registerEventProcessor(message ->
                message.contains("is already well tended") || message.contains("seems pleased."), () -> processed = true);
    }

    private enum InputKey implements Bot.InputKey {
        d("Set the distance the bot should look around player in search", "distance(in meters))"),
        v("Verbose mode", ""),
        area("Toggle the area processing mode. ", "tiles_ahead tiles_to_the_right"),
        area_speed("Set the speed of moving for area mode. Default value is 1 second per tile.", "speed(float value)"),
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold", "threshold(float value between 0 and 1)");

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
