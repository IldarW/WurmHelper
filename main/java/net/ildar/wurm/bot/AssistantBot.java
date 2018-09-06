package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.PaperDollInventory;
import com.wurmonline.client.renderer.gui.PaperDollSlot;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.Comparator;
import java.util.InputMismatchException;
import java.util.List;

public class AssistantBot extends Bot {
    private Enchant spellToCast = Enchant.DISPEL;
    private boolean casting;
    private long statuetteId;
    private long bodyId;
    private boolean wovCasting;
    private long lastWOV;
    private boolean successfullCastStart;
    private boolean successfullCasting;
    private boolean needWaitWov;

    private boolean lockpicking;
    private long chestId;
    private long lastLockpicking;
    private long lockpickingTimeout;
    private boolean successfullStartOfLockpicking;
    private int lockpickingResult;
    private boolean successfullLocking;
    private boolean noLock;

    private boolean drinking;
    private long waterId;
    private boolean successfullDrinkingStart;
    private boolean successfullDrinking;

    private boolean trashCleaning;
    private long trashCleaningTimeout;
    private long lastTrashCleaning;
    private long trashBinId;
    private boolean successfullStartTrashCleaning;

    private boolean praying;
    private long altarId;
    private long lastPrayer;
    private long prayingTimeout;
    private boolean successfullStartOfPraying;

    private boolean sacrificing;
    private long sacrificeAltarId;
    private long lastSacrifice;
    private long sacrificeTimeout;
    private boolean successfullStartOfSacrificing;

    private boolean kindlingBurning;
    private long forgeId;
    private long lastBurning;
    private long kindlingBurningTimeout;
    private boolean successfullStartOfBurning;

    private boolean verbose = false;

    public AssistantBot() {
        registerInputHandler(InputKey.w, input -> toggleDrinking(0));
        registerInputHandler(InputKey.wid, this::handleDrinkingTargetIdChange);
        registerInputHandler(InputKey.c, input -> toggleCasting());
        registerInputHandler(InputKey.p, input -> togglePraying(0));
        registerInputHandler(InputKey.pt, this::handlePrayerTimeoutChange);
        registerInputHandler(InputKey.pid, this::handlePrayingAltarIdChange);
        registerInputHandler(InputKey.s, input -> toggleSacrificing(0));
        registerInputHandler(InputKey.st, this::handleSacrificeTimeoutChange);
        registerInputHandler(InputKey.sid, this::handleSacrificingAltarIdChange);
        registerInputHandler(InputKey.kb, input -> toggleKindlingBurns(0));
        registerInputHandler(InputKey.kbt, this::handleKindlingBurnsTimeoutChange);
        registerInputHandler(InputKey.kbid, this::handleKindlingBurningForgeIdChange);
        registerInputHandler(InputKey.cwov, input -> toggleWOVCasting());
        registerInputHandler(InputKey.cleanup, input -> toggleTrashCleaning(0));
        registerInputHandler(InputKey.cleanupt, this::handleTrashCleaningTimeoutChange);
        registerInputHandler(InputKey.cleanupid, this::handleTrashCleaningTargetIdChange);
        registerInputHandler(InputKey.cmf, input -> spellToCast = Enchant.MORNINGFOG);
        registerInputHandler(InputKey.l, input -> toggleLockpicking(0));
        registerInputHandler(InputKey.lt, this::handleLockpickingTimeoutChange);
        registerInputHandler(InputKey.lid, this::handleLockpickingTargetIdChange);
        registerInputHandler(InputKey.v, input -> toggleVerbosity());
    }

    @Override
    public void work() throws Exception{
        registerEventProcessors();
        while (isActive()) {
            if (casting) {
                float favor = Mod.hud.getWorld().getPlayer().getSkillSet().getSkillValue("favor");
                if (favor > spellToCast.favorCap) {
                    successfullCasting = false;
                    successfullCastStart = false;
                    int counter = 0;
                    while (!successfullCastStart && counter++ < 50 && favor > spellToCast.favorCap) {
                        if (verbose) Utils.consolePrint("successfullCastStart counter=" + counter);
                        Mod.hud.getWorld().getServerConnection().sendAction(statuetteId, new long[]{bodyId}, spellToCast.playerAction);
                        favor = Mod.hud.getWorld().getPlayer().getSkillSet().getSkillValue("favor");
                        sleep(500);
                    }
                    counter = 0;
                    while (!successfullCasting && counter++ < 100 && favor > spellToCast.favorCap) {
                        if (verbose) Utils.consolePrint("successfullCasting counter=" + counter);
                        sleep(2000);
                    }
                }
            } else if (wovCasting && Math.abs(lastWOV - System.currentTimeMillis()) > 1810000) {
                float favor = Mod.hud.getWorld().getPlayer().getSkillSet().getSkillValue("favor");
                if (favor > 30) {
                    successfullCasting = false;
                    successfullCastStart = false;
                    needWaitWov = false;
                    int counter = 0;
                    while (!successfullCastStart && counter++ < 50 && !needWaitWov) {
                        if (verbose) Utils.consolePrint("successfullCastStart counter=" + counter);
                        Mod.hud.getWorld().getServerConnection().sendAction(statuetteId, new long[]{bodyId}, PlayerAction.WISDOM_OF_VYNORA);
                        sleep(500);
                    }
                    counter = 0;
                    while (!successfullCasting && counter++ < 100 && !needWaitWov) {
                        if (verbose) Utils.consolePrint("successfullCasting counter=" + counter);
                        sleep(2000);
                    }
                    if (needWaitWov)
                        lastWOV = lastWOV + 20000;
                    else
                        lastWOV = System.currentTimeMillis();
                }
            }
            if (drinking) {
                float thirst = Mod.hud.getWorld().getPlayer().getThirst();
                if (thirst > 0.1) {
                    successfullDrinking = false;
                    successfullDrinkingStart = false;
                    int counter = 0;
                    while (!successfullDrinkingStart && counter++ < 50) {
                        if (verbose) Utils.consolePrint("successfullDrinkingStart counter=" + counter);
                        Mod.hud.sendAction(new PlayerAction((short) 183, 65535), waterId);
                        sleep(500);
                    }
                    counter = 0;
                    while (!successfullDrinking && counter++ < 100) {
                        if (verbose) Utils.consolePrint("successfullDrinking counter=" + counter);
                        sleep(2000);
                    }
                }
            }
            if (lockpicking && Math.abs(lastLockpicking - System.currentTimeMillis()) > lockpickingTimeout) {
                long lockpickId = 0;
                InventoryMetaItem lockpick = Utils.getInventoryItem("lock picks");
                if (lockpick != null)
                    lockpickId = lockpick.getId();
                if (lockpickId == 0) {
                    Utils.consolePrint("No lockpicks in inventory! Turning lockpicking off");
                    lockpicking = false;
                    continue;
                }

                successfullStartOfLockpicking = false;
                lockpickingResult = -1;
                int counter = 0;
                while (!successfullStartOfLockpicking && counter++ < 50 && !noLock) {
                    if (verbose) Utils.consolePrint("successfullStartOfLockpicking counter=" + counter);
                    Mod.hud.getWorld().getServerConnection().sendAction(lockpickId,
                            new long[]{chestId}, new PlayerAction((short) 101, 65535));
                    sleep(500);
                }
                if (counter >= 50) continue;
                counter = 0;
                while (lockpickingResult == -1 && counter++ < 100 && !noLock) {
                    if (verbose) Utils.consolePrint("lockpickingResult counter=" + counter);
                    sleep(2000);
                }
                if (noLock || lockpickingResult > 0) {
                    long padlockId = 0;
                    InventoryMetaItem padlock = Utils.getInventoryItem("padlock");
                    if (padlock != null)
                        padlockId = padlock.getId();
                    if (padlockId == 0) {
                        sleep(1000);
                        continue;
                    }
                    successfullLocking = false;
                    counter = 0;
                    while (!successfullLocking && counter++ < 50) {
                        if (verbose) Utils.consolePrint("successfullLocking lockingcounter=" + counter);
                        Mod.hud.getWorld().getServerConnection().sendAction(padlockId,
                                new long[]{chestId}, new PlayerAction((short) 161, 65535));
                        sleep(500);
                    }
                }
                if (noLock)
                    noLock = false;
                else
                    lastLockpicking = System.currentTimeMillis();
            }
            if (trashCleaning) {
                if (Math.abs(lastTrashCleaning - System.currentTimeMillis()) > trashCleaningTimeout) {
                    lastTrashCleaning = System.currentTimeMillis();
                    successfullStartTrashCleaning = false;
                    int counter = 0;
                    while (!successfullStartTrashCleaning && counter++ < 30) {
                        if (verbose) Utils.consolePrint("successfullStartTrashCleaning counter=" + counter);
                        Mod.hud.sendAction(new PlayerAction((short) 954, 65535), trashBinId);
                        sleep(1000);
                    }
                    successfullStartTrashCleaning = true;
                }
            }

            if (praying) {
                if (Math.abs(lastPrayer - System.currentTimeMillis()) > prayingTimeout) {
                    lastPrayer = System.currentTimeMillis();
                    successfullStartOfPraying = false;
                    int counter = 0;
                    while (!successfullStartOfPraying && counter++ < 50) {
                        if (verbose) Utils.consolePrint("successfullStartOfPraying counter=" + counter);
                        Mod.hud.sendAction(PlayerAction.PRAY, altarId);
                        sleep(1000);
                    }
                    successfullStartOfPraying = true;
                }
            }

            if (sacrificing) {
                if (Math.abs(lastSacrifice - System.currentTimeMillis()) > sacrificeTimeout) {
                    lastSacrifice = System.currentTimeMillis();
                    successfullStartOfSacrificing = false;
                    int counter = 0;
                    while (!successfullStartOfSacrificing && counter++ < 50) {
                        if (verbose) Utils.consolePrint("successfullStartOfSacrificing counter=" + counter);
                        Mod.hud.sendAction(PlayerAction.SACRIFICE, sacrificeAltarId);
                        sleep(1000);
                    }
                    successfullStartOfSacrificing = true;
                }
            }

            if (kindlingBurning) {
                if (Math.abs(lastBurning - System.currentTimeMillis()) > kindlingBurningTimeout) {
                    lastBurning = System.currentTimeMillis();
                    List<InventoryMetaItem> kindlings = Utils.getInventoryItems("kindling");
                    if (kindlings.size() > 1) {
                        kindlings.sort(Comparator.comparingDouble(InventoryMetaItem::getWeight));
                        InventoryMetaItem biggestKindling = kindlings.get(kindlings.size() - 1);
                        kindlings.remove(biggestKindling);
                        long[] targetIds = new long[kindlings.size()];
                        for (int i = 0; i < Math.min(kindlings.size(), 64); i++)
                            targetIds[i] = kindlings.get(i).getId();
                        Mod.hud.getWorld().getServerConnection().sendAction(
                                targetIds[0], targetIds, PlayerAction.COMBINE);
                        successfullStartOfBurning = false;
                        int counter = 0;
                        while (!successfullStartOfBurning && counter++ < 50) {
                            if (verbose) Utils.consolePrint("successfullStartOfBurning counter=" + counter);
                            Mod.hud.getWorld().getServerConnection().sendAction(
                                    biggestKindling.getId(), new long[]{forgeId}, new PlayerAction((short) 117, 65535));
                            sleep(300);
                        }
                        successfullStartOfBurning = true;
                    }
                }
            }
            sleep(timeout);
        }
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("you will start dispelling")
                        || message.contains("You start to cast ")
                        || message.contains("you will start casting"),
                () -> successfullCastStart = true);
        registerEventProcessor(message -> message.contains("You cast ")
                        || message.contains("You fail to channel the "),
                () -> successfullCasting = true);
        registerEventProcessor(message -> message.contains("until you can cast Wisdom of Vynora again."),
                () -> needWaitWov = true);
        registerEventProcessor(message -> message.contains("you will start drinking"),
                () -> successfullDrinkingStart = true);
        registerEventProcessor(message -> message.contains("The water is refreshing and it cools you down")
                        || message.contains("You are so bloated you cannot bring yourself to drink any thing"),
                () -> successfullDrinking = successfullDrinkingStart = true);
        registerEventProcessor(message -> message.contains("You start to pick the lock")
                        || message.contains("you will start picking lock"),
                () -> successfullStartOfLockpicking = true);
        registerEventProcessor(message -> message.contains("You fail to pick the lock"),
                () -> lockpickingResult = 0);
        registerEventProcessor(message -> message.contains("You pick the lock of"),
                () -> lockpickingResult = 1);
        registerEventProcessor(message -> message.contains("you will start attaching lock")
                        || message.contains("You lock the "),
                () -> successfullLocking = true);
        registerEventProcessor(message -> message.contains("is not locked."),
                () -> noLock = true);
        registerEventProcessor(message -> message.contains("you will start cleaning."),
                () -> successfullStartTrashCleaning = true);
        registerEventProcessor(message -> message.contains("You will start praying")
                        || message.contains("You start to pray")
                        || message.contains("you will start praying"),
                () -> successfullStartOfPraying = true);
        registerEventProcessor(message -> message.contains("you will start burning")
                        || message.contains("You fuel the"),
                () -> successfullStartOfBurning = true);
        registerEventProcessor(message -> message.contains("You start to sacrifice")
                        || message.contains("you will start sacrificing"),
                () -> successfullStartOfSacrificing = true);
    }

    private void handleDrinkingTargetIdChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.wid);
            return;
        }
        try {
            toggleDrinking(Long.parseLong(input[0]));
        } catch (Exception e) {
            Utils.consolePrint("Can't get water id");
        }
    }

    private void handleLockpickingTargetIdChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.lid);
            return;
        }
        try {
            toggleLockpicking(Long.parseLong(input[0]));
        } catch (Exception e) {
            Utils.consolePrint("Can't get chest id");
        }
    }


    private void handleTrashCleaningTargetIdChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.cleanupid);
            return;
        }
        try {
            toggleTrashCleaning(Long.parseLong(input[0]));
        } catch (Exception e) {
            Utils.consolePrint("Can't get trash bin id");
        }
    }

    private void handlePrayingAltarIdChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.pid);
            return;
        }
        try {
            togglePraying(Long.parseLong(input[0]));
        } catch (Exception e) {
            Utils.consolePrint("Can't get altar id");
        }
    }

    private void handleSacrificingAltarIdChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.sid);
            return;
        }
        try {
            toggleSacrificing(Long.parseLong(input[0]));
        } catch (Exception e) {
            Utils.consolePrint("Can't get altar id");
        }
    }

    private void handleKindlingBurningForgeIdChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.kbid);
            return;
        }
        try {
            toggleKindlingBurns(Long.parseLong(input[0]));
        } catch (Exception e) {
            Utils.consolePrint("Can't get forge id");
        }
    }

    private void handleKindlingBurnsTimeoutChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.kbt);
            return;
        }
        if (kindlingBurning) {
            try {
                changeKinglingBurnsTimeout(Integer.parseInt(input[0]));
            } catch (InputMismatchException e) {
                Utils.consolePrint("Wrong timeout value!");
            }
        } else {
            Utils.consolePrint("Kindling burning is off!");
        }
    }

    private void changeKinglingBurnsTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            timeout = 100;
        }
        kindlingBurningTimeout = timeout;
        Utils.consolePrint("Current kindling burn timeout is " + kindlingBurningTimeout);
    }

    private void handlePrayerTimeoutChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.pt);
            return;
        }
        if (praying) {
            try {
                changePrayerTimeout(Integer.parseInt(input[0]));
            } catch (InputMismatchException e) {
                Utils.consolePrint("Wrong timeout value!");
            }
        } else {
            Utils.consolePrint("Automatic praying is off!");
        }
    }

    private void changePrayerTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            timeout = 100;
        }
        prayingTimeout = timeout;
        Utils.consolePrint("Current prayer timeout is " + prayingTimeout);
    }

    private void handleTrashCleaningTimeoutChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.cleanupt);
            return;
        }
        if (trashCleaning) {
            try {
                changeTrashCleaningTimeout(Integer.parseInt(input[0]));
            } catch (InputMismatchException e) {
                Utils.consolePrint("Wrong timeout value!");
            }
        } else {
            Utils.consolePrint("Trash cleaning is off!");
        }
    }

    private void changeTrashCleaningTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            timeout = 100;
        }
        trashCleaningTimeout = timeout;
        Utils.consolePrint("Current trash cleaning timeout is " + trashCleaningTimeout);
    }

    private void handleSacrificeTimeoutChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.st);
            return;
        }
        if (sacrificing) {
            try {
                changeSacrificeTimeout(Integer.parseInt(input[0]));
            } catch (InputMismatchException e) {
                Utils.consolePrint("Wrong timeout value!");
            }
        } else {
            Utils.consolePrint("Automatic sacrificing is off!");
        }
    }

    private void changeSacrificeTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            timeout = 100;
        }
        sacrificeTimeout = timeout;
        Utils.consolePrint("Current sacrifice timeout is " + sacrificeTimeout);
    }

    private void handleLockpickingTimeoutChange(String input[]) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.lt);
            return;
        }
        if (lockpicking) {
            try {
                changeLockpickingTimeout(Integer.parseInt(input[0]));
            } catch (InputMismatchException e) {
                Utils.consolePrint("Wrong timeout value!");
            }
        } else {
            Utils.consolePrint("Automatic lockpicking is off!");
        }
    }

    private void changeLockpickingTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            timeout = 100;
        }
        lockpickingTimeout = timeout;
        Utils.consolePrint("Current lockpicking timeout is " + lockpickingTimeout);
    }

    private void toggleTrashCleaning(long trashBinId) {
        trashCleaning = !trashCleaning;
        if (trashCleaning) {
            if (trashBinId == 0) {
                try {
                    PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                            ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
                    if (pickableUnit == null || !pickableUnit.getHoverName().contains("trash heap")) {
                        Utils.consolePrint("Select trash bin!");
                        trashCleaning = false;
                        return;
                    }
                    trashBinId = pickableUnit.getId();
                } catch (Exception e) {
                    Utils.consolePrint("Can't turn trash cleaning on!");
                    trashCleaning = false;
                    return;
                }
            }
            this.trashBinId = trashBinId;
            lastTrashCleaning = 0;
            Utils.consolePrint(this.getClass().getSimpleName() + " trash cleaning is on!");
            changeTrashCleaningTimeout(5000);
        } else
            Utils.consolePrint(this.getClass().getSimpleName() + " trash cleaning is off!");

    }

    private void toggleCasting() {
        casting = !casting;
        if (casting) {
            try {
                PaperDollInventory pdi = Mod.hud.getPaperDollInventory();
                PaperDollSlot pds = ReflectionUtil.getPrivateField(pdi,
                        ReflectionUtil.getField(pdi.getClass(), "bodyItem"));
                bodyId = pds.getItemId();
                InventoryMetaItem statuette = Utils.getInventoryItem("statuette of");
                if (statuette == null) {
                    Utils.consolePrint("Can't find a statuette in your inventory");
                    casting = false;
                    return;
                } else
                    Utils.consolePrint("Spellcasts are on!");
                statuetteId = statuette.getId();
            } catch (Exception e) {
                Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
                Utils.consolePrint(e.toString());
                casting = false;
            }
        } else
            Utils.consolePrint("Spellcasts are off!");
    }

    private void togglePraying(long altarId) {
        praying = !praying;
        if (praying) {
            if (altarId == 0) {
                try {
                    PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                            ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
                    if (pickableUnit == null || !pickableUnit.getHoverName().contains("altar")) {
                        Utils.consolePrint("Select an altar!");
                        praying = false;
                        return;
                    }
                    altarId = pickableUnit.getId();
                } catch (Exception e) {
                    Utils.consolePrint("Can't turn praying on!");
                    praying = false;
                    return;
                }
                this.altarId = altarId;
                lastPrayer = 0;
                Utils.consolePrint(this.getClass().getSimpleName() + " praying is on!");
                changePrayerTimeout(1230000);
            }
        } else
            Utils.consolePrint("Praying is off!");

    }

    private void toggleSacrificing(long altarId) {
        sacrificing = !sacrificing;
        if (sacrificing) {
            if (altarId == 0) {
                try {
                    PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                            ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
                    if (pickableUnit == null || !pickableUnit.getHoverName().contains("altar")) {
                        Utils.consolePrint("Select an altar!");
                        sacrificing = false;
                        return;
                    }
                    altarId = pickableUnit.getId();
                } catch (Exception e) {
                    Utils.consolePrint("Can't turn sacrificing on!");
                    sacrificing = false;
                    return;
                }
            }
            sacrificeAltarId = altarId;
            lastSacrifice = 0;
            Utils.consolePrint(this.getClass().getSimpleName() + " sacrificing is on!");
            changeSacrificeTimeout(1230000);
        } else
            Utils.consolePrint("Sacrificing is off!");

    }

    private void toggleKindlingBurns(long forgeId) {
        kindlingBurning = !kindlingBurning;
        if (kindlingBurning) {
            if (forgeId == 0) {
                try {
                    PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                            ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
                    if (pickableUnit == null) {
                        Utils.consolePrint("Select a forge first!");
                        kindlingBurning = false;
                        return;
                    }
                    forgeId = pickableUnit.getId();
                } catch (Exception e) {
                    Utils.consolePrint("Can't turn kindling burning on!");
                    kindlingBurning = false;
                    return;
                }
            }
            this.forgeId = forgeId;
            lastBurning = 0;
            Utils.consolePrint(this.getClass().getSimpleName() + " kindling burning is on!");
            changeKinglingBurnsTimeout(10000);
        } else
            Utils.consolePrint("Kindling burning is off!");

    }

    private void toggleWOVCasting() {
        wovCasting = !wovCasting;
        if (wovCasting) {
            casting = false;
            try {
                PaperDollInventory pdi = Mod.hud.getPaperDollInventory();
                PaperDollSlot pds = ReflectionUtil.getPrivateField(pdi,
                        ReflectionUtil.getField(pdi.getClass(), "bodyItem"));
                bodyId = pds.getItemId();
                InventoryMetaItem statuette = Utils.getInventoryItem("statuette of");
                if (statuette == null || bodyId == 0) {
                    wovCasting = false;
                    Utils.consolePrint("Couldn't find a statuette in your inventory. casting is off");
                } else {
                    statuetteId = statuette.getId();
                    Utils.consolePrint("Wysdom of Vynora spellcasts are on!");
                }
            } catch (Exception e) {
                Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
                Utils.consolePrint(e.toString());
            }
        } else
            Utils.consolePrint("Wysdom of Vynora casting is off!");
    }

    private void toggleLockpicking(long chestId) {
        lockpicking = !lockpicking;
        if (lockpicking) {
            if(chestId == 0) {
                int x = Mod.hud.getWorld().getClient().getXMouse();
                int y = Mod.hud.getWorld().getClient().getYMouse();
                long[] targets = Mod.hud.getCommandTargetsFrom(x, y);
                if (targets != null && targets.length > 0) {
                    chestId = targets[0];
                } else {
                    lockpicking = false;
                    Utils.consolePrint("Can't find the target for lockpicking");
                    return;
                }
            }
            this.chestId = chestId;
            lastLockpicking = 0;
            Utils.consolePrint("Lockpicking is on!");
            changeLockpickingTimeout(610000);
        } else
            Utils.consolePrint("Lockpicking is off!");
    }

    private void toggleDrinking(long targetId) {
        drinking = !drinking;
        if (drinking) {
            if (targetId == 0) {
                int x = Mod.hud.getWorld().getClient().getXMouse();
                int y = Mod.hud.getWorld().getClient().getYMouse();
                long[] targets = Mod.hud.getCommandTargetsFrom(x, y);
                if (targets != null && targets.length > 0) {
                    targetId = targets[0];
                } else {
                    drinking = false;
                    Utils.consolePrint("Can't find the target water");
                    return;
                }
            }
            waterId = targetId;
            Utils.consolePrint("Drinking is on!");
        } else
            Utils.consolePrint("Drinking is off!");
    }

    private void toggleVerbosity() {
        verbose = !verbose;
        if (verbose)
            Utils.consolePrint("Verbose mode is on!");
        else
            Utils.consolePrint("Verbose mode is off!");

    }

    private enum InputKey {
        w("Toggle automatic drinking of the liquid the user pointing at", ""),
        wid("Toggle automatic drinking of liquid with provided id", "id"),
        c("Toggle automatic casts of spells(if player has enough favor)", ""),
        p("Toggle automatic praying. The timeout between prayers can be configured separately.", ""),
        pt("Change the timeout between prayers", "timeout(in milliseconds)"),
        pid("Toggle automatic praying on altar with provided id", "id"),
        s("Toggle automatic sacrificing. The timeout between sacrifices can be configured separately.", ""),
        st("Change the timeout between sacrifices", "timeout(in milliseconds)"),
        sid("Toggle automatic sacrifices at altar with provided id", "id"),
        kb("Toggle automatic burning of kindlings in player's inventory. " +
                AssistantBot.class.getSimpleName() + " will combine the kindlings and burn them using selected forge. " +
                "The timeout of burns can be configured separately", ""),
        kbt("Change the timeout between kingling burns", "timeout(in milliseconds)"),
        kbid("Toggle automatic kindling burns at forge with provided id", "id"),
        cwov("Toggle automatic casts of Wysdom of Vynora spell", ""),
        cleanup("Toggle automatic trash cleanings. The timeout between cleanings can be configured separately", ""),
        cleanupt("Change the timeout between trash cleanings", "timeout(in milliseconds)"),
        cleanupid("Toggle automatic cleaning of items inside trash bin with provided id", "id"),
        cmf("Change the currently casted spell to Morning fog. Doesn't have any effect if autocasts are off", ""),
        l("Toggle automatic lockpicking. The target chest should be beneath the user's mouse", ""),
        lt("Change the timeout between lockpickings", "timeout(in milliseconds)"),
        lid("Toggle automatic lockpicking of target chest with provided id", "id"),
        v("Toggle verbose mode. In verbose mode the " + AssistantBot.class.getSimpleName() + " will output additional info to the console", "");

        public String description;
        public String usage;
        InputKey(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }
    }

    enum Enchant{
        BLESS(10, PlayerAction.BLESS),
        MORNINGFOG(5, PlayerAction.MORNING_FOG),
        DISPEL(10, PlayerAction.DISPEL);

        int favorCap;
        PlayerAction playerAction;
        Enchant(int favorCap, PlayerAction playerAction) {
            this.favorCap = favorCap;
            this.playerAction = playerAction;
        }
    }
}



