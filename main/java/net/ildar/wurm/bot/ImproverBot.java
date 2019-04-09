package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.CreatureData;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.shared.constants.PlayerAction;
import com.wurmonline.shared.util.MaterialUtilities;
import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.*;

public class ImproverBot extends Bot {
    private List<Tool> tools = new ArrayList<>();
    private List<InventoryListComponent> targets = new ArrayList<>();
    private float staminaThreshold;
    private boolean improveActionFinished;
    private boolean groundMode;
    private ToolSkill toolSkill = ToolSkill.UNKNOWN;

    public static BotRegistration getRegistration() {
        return new BotRegistration(ImproverBot.class,
        "Improves selected items in provided inventories. Tools searched from player's inventory. " +
                "Items like water or stone searched before each improve, " +
                "actual instruments searched one time before improve of the first item that must be improved with this tool. " +
                "Tool for improving is determined by improve icon that you see on the right side of item row in inventory. " +
                "For example improve icons for stone chisel and carving knife are equal, and sometimes bot can choose wrong tool. " +
                "Use \"" + ImproverBot.InputKey.ci.name() + "\" key to change the chosen instrument.",
                "i");
    }

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    public ImproverBot() {
        registerInputHandler(ImproverBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(ImproverBot.InputKey.at, input -> addTarget());
        registerInputHandler(ImproverBot.InputKey.ls, input -> listAvailableSkills());
        registerInputHandler(ImproverBot.InputKey.g, this::toggleGroundMode);
        registerInputHandler(ImproverBot.InputKey.ci, input -> changeInstrument());
        registerInputHandler(ImproverBot.InputKey.ss, this::setToolSkill);

        tools.add(new Tool(1201, "carving knife", true, false, new HashSet<>(Arrays.asList(ToolSkill.CARPENTRY))));
        tools.add(new Tool(741, "mallet", true, false, new HashSet<>(Arrays.asList(ToolSkill.CARPENTRY, ToolSkill.LEATHERWORKING))));
        tools.add(new Tool(749, "file", true, false, new HashSet<>(Arrays.asList(ToolSkill.CARPENTRY))));
        tools.add(new Tool(602, "pelt", true, false, new HashSet<>(Arrays.asList(ToolSkill.CARPENTRY, ToolSkill.BLACKSMITHING))));
        tools.add(new Tool(606, "log", false, false, new HashSet<>(Arrays.asList(ToolSkill.CARPENTRY))));

        tools.add(new Tool(1201, "stone chisel", true, false, new HashSet<>(Arrays.asList(ToolSkill.MASONRY))));
        tools.add(new Tool(610, "shards", false, false, new HashSet<>(Arrays.asList(ToolSkill.MASONRY))));

        tools.add(new Tool(808, "spatula", true, false, new HashSet<>(Arrays.asList(ToolSkill.POTTERY))));
        tools.add(new Tool(802, "clay shaper", true, false, new HashSet<>(Arrays.asList(ToolSkill.POTTERY))));
        tools.add(new Tool(540, "water", false, false, new HashSet<>(Arrays.asList(ToolSkill.POTTERY, ToolSkill.CLOTH_TAILORING, ToolSkill.BLACKSMITHING))));
        tools.add(new Tool(591, "clay", false, true, new HashSet<>(Arrays.asList(ToolSkill.POTTERY))));
        tools.add(new Tool(4, "hand", true, false, new HashSet<>(Arrays.asList(ToolSkill.POTTERY))));

        tools.add(new Tool(788, "needle", true, false, new HashSet<>(Arrays.asList(ToolSkill.CLOTH_TAILORING, ToolSkill.LEATHERWORKING))));
        tools.add(new Tool(748, "scissors", true, false, new HashSet<>(Arrays.asList(ToolSkill.CLOTH_TAILORING))));
        tools.add(new Tool(620, "string of cloth", false, false, new HashSet<>(Arrays.asList(ToolSkill.CLOTH_TAILORING))));

        tools.add(new Tool(766, "leather knife", true, false, new HashSet<>(Arrays.asList(ToolSkill.LEATHERWORKING))));
        tools.add(new Tool(754, "awl", true, false, new HashSet<>(Arrays.asList(ToolSkill.LEATHERWORKING))));
        tools.add(new Tool(602, "leather", false, true, new HashSet<>(Arrays.asList(ToolSkill.LEATHERWORKING))));

        tools.add(new Tool(742, "hammer", true, true, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));
        tools.add(new Tool(803, "whetstone", true, true, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));

        tools.add(new Tool(633, "'lump, iron'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//iron
        tools.add(new Tool(636, "'lump, copper'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//copper
        tools.add(new Tool(632, "'lump, silver'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//silver
        tools.add(new Tool(631, "'lump, electrum'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//electrum
        tools.add(new Tool(631, "'lump, gold'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//gold
        tools.add(new Tool(637, "'lump, tin'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//tin
        tools.add(new Tool(635, "'lump, zinc'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//zinc
        tools.add(new Tool(634, "'lump, lead'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//lead

        tools.add(new Tool(672, "'lump, steel'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//steel
        tools.add(new Tool(673, "'lump, brass'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//brass
        tools.add(new Tool(671, "'lump, bronze'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//bronze

        tools.add(new Tool(638, "'lump, glimmersteel'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//glimmersteel
        tools.add(new Tool(639, "'lump, adamantine'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//adamantine
        tools.add(new Tool(630, "'lump, seryll'", false, false, new HashSet<>(Arrays.asList(ToolSkill.BLACKSMITHING))));//seryll

    }

    @Override
    public void work() throws Exception{
        setStaminaThreshold(0.8f);
        setTimeout(300);
        registerEventProcessors();
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));
        while (isActive()) {
            waitOnPause();
            if (targets.size() == 0 && !groundMode) {
                sleep(timeout);
                continue;
            }
            float progress = ReflectionUtil.getPrivateField(progressBar, ReflectionUtil.getField(progressBar.getClass(), "progress"));
            float stamina = Mod.hud.getWorld().getPlayer().getStamina();
            float damage = Mod.hud.getWorld().getPlayer().getDamage();
            boolean improveInitiated = false;
            if ((stamina+damage) > staminaThreshold && progress == 0f && creationWindow.getActionInUse() == 0) {
                if (!groundMode) {
                    List<InventoryMetaItem> selectedItems = new ArrayList<>();
                    for (InventoryListComponent ilc : targets) {
                        List<InventoryMetaItem> targetSelectedItems = Utils.getSelectedItems(ilc, false, true);
                        if (targetSelectedItems != null)
                            selectedItems.addAll(targetSelectedItems);
                    }
                    if (selectedItems.size() == 0) {
                        Utils.consolePrint("No selected items!");
                        sleep(timeout);
                        continue;
                    }
                    selectedItems.sort(Comparator.comparingDouble(item -> item.getQuality() * (1- item.getDamage()/100)));
                    for (InventoryMetaItem itemToImprove : selectedItems) {
                        if (itemToImprove == null || itemToImprove.getImproveIconId() < 0) {
                            continue;
                        }
                        Tool tool = findToolForImprove(itemToImprove);
                        if (tool == null) {
                            Utils.consolePrint("Can't find a tool to improve " + itemToImprove.getBaseName() + " " + itemToImprove.getId());
                            continue;
                        }
                        if (tool.itemId == 0 || !tool.fixed) {
                            boolean toolItemFound = assignItemForTool(tool, itemToImprove.getWeight());
                            if (!toolItemFound)
                                continue;
                        }
                        if(MaterialUtilities.isMetal(itemToImprove.getMaterialId())){
                            if(itemToImprove.getTemperature()<5) {
                                Utils.consolePrint("Item \"" + itemToImprove.getBaseName() + "\" isn't hot enough");
                                continue;
                            }
                        }

                        if (itemToImprove.getDamage() > 0)
                            Mod.hud.sendAction(PlayerAction.REPAIR, itemToImprove.getId());
                        improveActionFinished = false;
                        improveInitiated = true;
                        Mod.hud.getWorld().getServerConnection().sendAction(tool.itemId,
                                new long[]{itemToImprove.getId()}, PlayerAction.IMPROVE);
                        break;
                    }
                } else {
                    PickableUnit pickableUnit = ReflectionUtil.getPrivateField(Mod.hud.getSelectBar(),
                            ReflectionUtil.getField(Mod.hud.getSelectBar().getClass(), "selectedUnit"));
                    boolean isCreatureCell = pickableUnit instanceof CreatureCellRenderable && ((CreatureCellRenderable)pickableUnit).isItem();
                    boolean isGroundCell = pickableUnit instanceof GroundItemCellRenderable;
                    if (pickableUnit == null || (!isCreatureCell && !isGroundCell)) {
                        Utils.consolePrint("No selected item!");
                        sleep(timeout);
                        continue;
                    }
                    byte materialId=-1;
                    if(isCreatureCell){
                        CreatureData creatureItem=((CreatureCellRenderable)pickableUnit).getCreatureData();
                        materialId=creatureItem.getMaterialId();
                    }
                    if(isGroundCell){
                        GroundItemData pickableItem = ReflectionUtil.getPrivateField(pickableUnit, ReflectionUtil.getField(GroundItemCellRenderable.class, "item"));
                        materialId=pickableItem.getMaterialId();
                    }
                    if(materialId==-1){
                        sleep(timeout);
                        continue;
                    }

                    ToolSkill groundSkill = ToolSkill.getSkillForItem(materialId);
                    if(groundSkill== ToolSkill.UNKNOWN){
                        sleep(timeout);
                        continue;
                    }

                    toolSkill=(groundSkill!=ToolSkill.UNKNOWN && groundSkill!=toolSkill)?groundSkill:toolSkill;

                    improveActionFinished = false;
                    Mod.hud.sendAction(PlayerAction.REPAIR, pickableUnit.getId());
                    for (Tool tool : getToolsBySkill(toolSkill)) {
                        if (tool.itemId == 0 || !tool.fixed) {
                            //process metal lumps
                            if(MaterialUtilities.isMetal(materialId) && !tool.name.contains(MaterialUtilities.getMaterialString(materialId)) && tool.name.contains("lump"))
                                continue;

                            boolean toolItemFound = assignItemForTool(tool,-1);
                            if (!toolItemFound)
                                continue;
                        }
                        improveInitiated = true;
                        Mod.hud.getWorld().getServerConnection().sendAction(tool.itemId,
                                new long[]{pickableUnit.getId()}, PlayerAction.IMPROVE);
                        sleep(100);
                    }
                }
                // wait for improve completion
                if (improveInitiated) {
                    int counter = 0;
                    while (!improveActionFinished && counter++ < 50) {
                        sleep(200);
                    }
                    if (!improveActionFinished)
                        Utils.consolePrint("Improve action didn't finish!");
                }
            }
            sleep(timeout);
        }
    }

    private List<Tool> getToolsBySkill(ToolSkill toolSkill) {
        if (toolSkill == null || toolSkill == ToolSkill.UNKNOWN)
            return tools;
        List<Tool> toolsBySkill = new ArrayList<>();
        for(Tool tool : tools) {
            if (tool.toolSkills.contains(toolSkill))
                toolsBySkill.add(tool);
        }
        return toolsBySkill;
    }

    private Tool findToolForImprove(InventoryMetaItem item) {
        if (item == null) return null;
        Tool returnTool = null;
        if(!this.groundMode)
            toolSkill = ToolSkill.getSkillForItem(item.getMaterialId());

        for(Tool tool : getToolsBySkill(toolSkill))
            if (tool.improveIconId == item.getImproveIconId()) {
                if(MaterialUtilities.isMetal(item.getMaterialId()) && !tool.name.contains(MaterialUtilities.getMaterialString(item.getMaterialId())) && tool.name.contains("lump"))
                    continue;

                if (tool.itemId == 0) {
                    returnTool = tool;
                    continue;
                }
                return tool;
            }
        return returnTool;
    }

    private void printShortToolInfo(InventoryMetaItem toolItem) {
        Utils.consolePrint("Item \"" + toolItem.getBaseName() + "\" will be used as the tool");
        Utils.consolePrint(" QL:" + String.format("%.2f", toolItem.getQuality()) + " DMG:" + String.format("%.2f", toolItem.getDamage()) + " Weight:" + toolItem.getWeight());
    }

    /**
     * @return true on success
     */
    private boolean assignItemForTool(Tool tool, float weight) {
        InventoryMetaItem toolItem = null;
        if (tool.exactName) {
            Optional<InventoryMetaItem> toolOptionalItem;
            toolOptionalItem = Utils.getInventoryItems(Mod.hud.getInventoryWindow().getInventoryListComponent(), tool.name).stream().filter(item -> item.getBaseName().equals(tool.name)).findFirst();
            if (toolOptionalItem.isPresent())
                toolItem = toolOptionalItem.get();
        } else if (!tool.fixed) {
            Optional<InventoryMetaItem> toolOptionalItem;
            if (weight != -1)
                toolOptionalItem = Utils.getInventoryItems(Mod.hud.getInventoryWindow().getInventoryListComponent(), tool.name).stream().filter(item -> item.getWeight() > 0.5 || item.getWeight() > weight * 0.05).max((item1, item2) -> Float.compare(item1.getWeight(), item2.getWeight()));
            else
                toolOptionalItem = Utils.getInventoryItems(tool.name).stream().max((item1, item2) -> Float.compare(item1.getWeight(), item2.getWeight()));
            if (toolOptionalItem.isPresent())
                toolItem = toolOptionalItem.get();
        }
        else
            toolItem = Utils.getInventoryItem(Mod.hud.getInventoryWindow().getInventoryListComponent(), tool.name);
        if (toolItem == null) {
            Utils.consolePrint("Can't find an item for a tool \"" + tool.name + "\"");
            return false;
        }
        //check lump heat
        if(MaterialUtilities.isMetal(toolItem.getMaterialId()) && toolItem.getBaseName().contains("lump")){
            if(toolItem.getTemperature()<5) {
                Utils.consolePrint("The \"" + toolItem.getDisplayName() + "\" isn't hot enough");
                return false;
            }
        }
        tool.itemId = toolItem.getId();
        if (tool.fixed)
            printShortToolInfo(toolItem);
        return true;
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You improve the")
                        || message.contains("You damage the")
                        || message.contains("You will want to polish the")
                        || message.contains("You must use a mallet on the ")
                        || message.contains("You must use a file to smooth ")
                        || message.contains("You notice some notches you must carve away")
                        || message.contains("could be improved with")
                        || message.contains("some irregularities that must be removed with a stone chisel")
                        || message.contains("has some stains that must be washed away")
                        || message.contains("has an open seam that must be backstitched with an iron needle to improve")
                        || message.contains("has a seam that needs to be hidden by slipstitching with an iron needle")
                        || message.contains("has some excess cloth that needs to be cut away with a scissors")
                        || message.contains("has some excess leather that needs to be cut away with a leather knife")
                        || message.contains("needs some holes punched with an awl")
                        || message.contains("has some holes and must be tailored with an iron needle to improve")
                        || message.contains("in order to smooth out a quirk")
                        || message.contains("some flaws that must be fixed")
                        || message.contains("some flaws that must be removed")
                        || message.contains("needs water")
                        || message.contains("needs to be sharpened")
                        || message.contains("has some dents that must be flattened")
                        || message.contains("dipping it in water")
                        || message.contains("doesn't need repairing")
                        || message.contains("You repair the"),
                () -> improveActionFinished = true);
    }



    private void changeInstrument() {
        List<InventoryMetaItem> selectedItems = Utils.getSelectedItems();
        if (selectedItems == null || selectedItems.size() == 0) {
            Utils.consolePrint("Select the instrument first!");
            return;
        }
        InventoryMetaItem instrument = selectedItems.get(0);
        for(Tool tool : tools) {
            if(instrument.getBaseName().contains(tool.name)) {
                printShortToolInfo(instrument);
                tool.itemId = instrument.getId();
                for(Tool anotherTool : tools)
                    if (!anotherTool.equals(tool) && anotherTool.improveIconId == tool.improveIconId)
                        anotherTool.itemId = 0;
                return;
            }
        }
        Utils.consolePrint("Unable to assign selected item to any known tool");
    }
    private void listAvailableSkills() {
        Utils.consolePrint(" skill_name \t\t abbreviation");
        for(ToolSkill toolSkill : ToolSkill.values()) {
            if (toolSkill == ToolSkill.UNKNOWN) continue;
            Utils.consolePrint(" " + toolSkill.name() + " \t\t " + toolSkill.abbreviation);
        }
    }

    private void setToolSkill(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ImproverBot.InputKey.ss);
            return;
        }
        ToolSkill toolSkill = ToolSkill.getByAbbreviation(input[0]);
        if (toolSkill == ToolSkill.UNKNOWN) {
            Utils.consolePrint("Unknown skill abbreviation!");
        } else {
            this.toolSkill = toolSkill;
            Utils.consolePrint("The skill was set to " + toolSkill.name());
        }
    }

    private void toggleGroundMode(String input[]) {
        if (groundMode) {
            groundMode = false;
            Utils.consolePrint("Ground mode is off!");
        } else {
            groundMode = true;
            Utils.consolePrint("Ground mode is on!");
        }
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ImproverBot.InputKey.s);
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

    private void addTarget() {
        WurmComponent inventoryComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (inventoryComponent == null) {
            Utils.consolePrint("Didn't find an inventory");
            return;
        }
        InventoryListComponent ilc;
        try {
            ilc = ReflectionUtil.getPrivateField(inventoryComponent,
                    ReflectionUtil.getField(inventoryComponent.getClass(), "component"));
        } catch(Exception e) {
            Utils.consolePrint("Unable to get inventory information");
            return;
        }
        targets.add(ilc);
        Utils.consolePrint("A new inventory was added");
    }

    enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        at("Add new inventory(under mouse cursor). Selected items in this inventory will be improved.", ""),
        ls("List available improving skills", ""),
        ss("Set the skill. Only tools from that skill will be used. You can list available skills using \"" + ls.name() + "\" key", "skill_abbreviation"),
        g("Toggle the ground mode. Set the skill first by \"" + ss.name() + "\" key", ""),
        ci("Change previously chosen instrument by tool selected in player's inventory", "");

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

    static class Tool{
        int improveIconId;
        long itemId;
        String name;
        //items like water or stone will be searched in player's inventory on each use and will have this value set to false
        boolean fixed;
        boolean exactName;
        Set<ToolSkill> toolSkills;

        Tool(int improveIconId, String name, boolean fixed, boolean exactName, Set<ToolSkill> toolSkills) {
            this.improveIconId = improveIconId;
            this.name = name;
            this.fixed = fixed;
            this.exactName = exactName;
            this.toolSkills = toolSkills;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum ToolSkill {
        UNKNOWN("?"),
        CARPENTRY("c"),
        MASONRY("m"),
        POTTERY("p"),
        CLOTH_TAILORING("ct"),
        BLACKSMITHING("b"),
        LEATHERWORKING("l");

        String abbreviation;
        ToolSkill(String abbreviation) {
            this.abbreviation = abbreviation;
        }

        static ToolSkill getByAbbreviation(String abbreviation) {
            for(ToolSkill toolSkill : values())
                if (toolSkill.abbreviation.equals(abbreviation))
                    return toolSkill;
            return UNKNOWN;
        }
        static ToolSkill getSkillForItem(byte materialId){
            if(MaterialUtilities.isWood(materialId))
                return ToolSkill.CARPENTRY;
            if(MaterialUtilities.isMetal(materialId))
                return ToolSkill.BLACKSMITHING;
            if(MaterialUtilities.isLeather(materialId))
                return ToolSkill.LEATHERWORKING;
            if(MaterialUtilities.isCloth(materialId))
                return ToolSkill.CLOTH_TAILORING;
            if(MaterialUtilities.isStone(materialId))
                return ToolSkill.MASONRY;
            if(MaterialUtilities.isClay(materialId))
                return ToolSkill.POTTERY;
            return UNKNOWN;
        }
    }
}
