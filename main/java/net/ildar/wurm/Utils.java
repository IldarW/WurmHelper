package net.ildar.wurm;

import com.wurmonline.client.game.SkillLogicSet;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class Utils {
    //used to synchronize server calls
    public static ReentrantLock serverCallLock = new ReentrantLock();
    //console messages queue
    public static Queue<String> consoleMessages = new ConcurrentLinkedQueue<>();
    /**
     * Print the message to the console
     */
    public static void consolePrint(String message) {
        if (message != null)
            consoleMessages.add(message);
    }

    public static void showOnScreenMessage(String message) {
        showOnScreenMessage(message, 1, 1, 1);
    }
    public static void showOnScreenMessage(String message, float r, float g, float b) {
        Mod.hud.addOnscreenMessage(message, r, g, b, (byte)1);
        consolePrint(message);
    }

    /**
     * Turn player by specified angle
     * @param dxRot angle in degrees
     */
    public static void turnPlayer(float dxRot) {
        try{
            float xRot = ReflectionUtil.getPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xRotUsed"));
            xRot = (xRot + dxRot)%360;
            if (xRot < 0 ) xRot = (xRot + 360)%360;
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xRotUsed"), xRot);
        } catch (Exception e) {
            consolePrint("Unexpected error while turning - " + e.getMessage());
        }
    }

    /**
     * Turn player at exact angle.
     * @param xRot the horizontal angle. Between 0 and 359, clockwise, 0 is north
     * @param yRot the vertical angle, 0 is center, 90 is bottom, -90 is top
     */
    public static void turnPlayer(float xRot, float yRot) {
        try{
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xRotUsed"), xRot);
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "yRotUsed"), yRot);
        } catch (Exception e) {
            consolePrint("Unexpected error while turning - " + e.getMessage());
        }
    }

    /**
     * Move player at specified distance in current direction
     * @param d distance in meters
     */
    public static void movePlayer(float d) {
        try{
            float x = Mod.hud.getWorld().getPlayerPosX();
            float y = Mod.hud.getWorld().getPlayerPosY();
            float xr = ReflectionUtil.getPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xRotUsed"));
            float dx = (float)(d*Math.sin((double)xr/180*Math.PI));
            float dy = (float)(-d*Math.cos((double)xr/180*Math.PI));
            movePlayer(x+dx, y+dy);
        } catch (Exception e) {
            consolePrint("Unexpected error while moving - " + e.getMessage());
            consolePrint( e.toString());
        }
    }

    public static void movePlayerBySteps(float d, int steps, long duration) throws InterruptedException{
        try{
            float x = Mod.hud.getWorld().getPlayerPosX();
            float y = Mod.hud.getWorld().getPlayerPosY();
            float xr = ReflectionUtil.getPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xRotUsed"));
            float dx = (float)(d*Math.sin((double)xr/180*Math.PI));
            float dy = (float)(-d*Math.cos((double)xr/180*Math.PI));
            movePlayerBySteps(x+dx, y+dy, steps, duration);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            consolePrint("Unexpected error while moving - " + e.getMessage());
            consolePrint( e.toString());
        }
    }

    public static void movePlayerBySteps(float x, float y, int steps, long duration) throws InterruptedException{
        float curX = Mod.hud.getWorld().getPlayerPosX();
        float curY = Mod.hud.getWorld().getPlayerPosY();
        float xStep = (x - curX) / steps;
        float yStep = (y - curY) / steps;
        for (int stepIndex = 0; stepIndex < steps; stepIndex++) {
            movePlayer(curX + xStep * (stepIndex + 1), curY + yStep * (stepIndex + 1));
            Thread.sleep(duration / steps);
        }
    }

    public static void movePlayer(float x, float y) {
        try{
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xPosUsed"), x);
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "yPosUsed"), y);
        } catch (Exception e) {
            consolePrint("Unexpected error while moving - " + e.getMessage());
            consolePrint( e.toString());
        }
    }

    /**
     * Place the player at the center of the tile and turn the look towards nearest cardinal direction
     */
    public static void stabilizePlayer() {
        moveToCenter();
        stabilizeLook();
    }

    /**
     * Turns the look towards nearest cardinal direction
     */
    public static void stabilizeLook() {
        try{
            float xRot = ReflectionUtil.getPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xRotUsed"));
            xRot = Math.round(xRot/90)*90;
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xRotUsed"), xRot);
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "yRotUsed"), (float)0.0);
        } catch (Exception e) {
            consolePrint("Unexpected error while turning - " + e.getMessage());
        }
    }

    public static void moveToCenter() {
        try{
            float x = Mod.hud.getWorld().getPlayerPosX();
            float y = Mod.hud.getWorld().getPlayerPosY();
            x = (float)(Math.floor((double)x/4)*4 + 2);
            y = (float)(Math.floor((double)y/4)*4 + 2);
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xPosUsed"), x);
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "yPosUsed"), y);

        } catch (Exception e) {
            consolePrint("Unexpected error while moving - " + e.getMessage());
        }
    }

    public static void moveToNearestCorner() {
        try {
            float x = Mod.hud.getWorld().getPlayerPosX();
            float y = Mod.hud.getWorld().getPlayerPosY();
            x = Math.round(x / 4) * 4;
            y = Math.round(y / 4) * 4;
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "xPosUsed"), x);
            ReflectionUtil.setPrivateField(Mod.hud.getWorld().getPlayer(),
                    ReflectionUtil.getField(Mod.hud.getWorld().getPlayer().getClass(), "yPosUsed"), y);
        } catch(Exception e) {
            consolePrint("Error on moving to the corner");
        }
    }

    public static float itemFavor(InventoryMetaItem item, float c) {
        float quality = item.getQuality() * (1- item.getDamage()/100);
        return quality * quality / 500 * c;
    }

    public static List<InventoryMetaItem>  getSelectedItems() {
        return getSelectedItems(Mod.hud.getInventoryWindow().getInventoryListComponent());
    }
    public static List<InventoryMetaItem> getSelectedItems(InventoryListComponent ilc) {
        return getSelectedItems(ilc, false, true);
    }
    public static List<InventoryMetaItem> getSelectedItems(InventoryListComponent ilc, boolean getAll, boolean recursive) {
        List<InventoryMetaItem> selItems = new ArrayList<>();
        try {
            WurmTreeList wtl = ReflectionUtil.getPrivateField(ilc,
                    ReflectionUtil.getField(ilc.getClass(), "itemList"));
            Object rootNode = ReflectionUtil.getPrivateField(wtl,
                    ReflectionUtil.getField(wtl.getClass(), "rootNode"));
            @SuppressWarnings("unchecked")
            List lines = new ArrayList(ReflectionUtil.getPrivateField(rootNode,
                    ReflectionUtil.getField(rootNode.getClass(), "children")));
            selItems =  getSelectedItems(lines, getAll, recursive);
        } catch(Exception e){
            consolePrint("Unexpected error while getting selected items - " + e.getMessage());
            consolePrint(e.toString());
        }
        return selItems;
    }
    public static List<InventoryMetaItem> getSelectedItems(List lines, boolean getAll, boolean recursive) {
        //List<WTreeListNode<InventoryListComponent.InventoryTreeListItem>> lines
        List<InventoryMetaItem> selItems = new ArrayList<>();
        try {
            for (Object currentLine : lines) {
                boolean isSelected = ReflectionUtil.getPrivateField(currentLine,
                        ReflectionUtil.getField(currentLine.getClass(), "isSelected"));
                @SuppressWarnings("unchecked")
                List children = new ArrayList(ReflectionUtil.getPrivateField(currentLine,
                        ReflectionUtil.getField(currentLine.getClass(), "children")));
                Object lineItem = ReflectionUtil.getPrivateField(currentLine,
                        ReflectionUtil.getField(currentLine.getClass(), "item"));
                InventoryMetaItem item = ReflectionUtil.getPrivateField(lineItem,
                        ReflectionUtil.getField(lineItem.getClass(), "item"));
                if (item == null) continue;
                boolean isContainer = ReflectionUtil.getPrivateField(lineItem,
                        ReflectionUtil.getField(lineItem.getClass(), "isContainer"));
                boolean isInventoryGroup = ReflectionUtil.getPrivateField(lineItem,
                        ReflectionUtil.getField(lineItem.getClass(), "isInventoryGroup"));
                if (children.size() > 0) {
                    if (isContainer && !isInventoryGroup && (getAll || isSelected)) {
                        selItems.add(item);
                        if (recursive || getAll)
                            selItems.addAll(getSelectedItems(children, true, true));
                    } else
                        selItems.addAll(getSelectedItems(children, getAll || isSelected, recursive));
                } else if (!isInventoryGroup && (getAll || isSelected))
                    selItems.add(item);
            }
        } catch(Exception e){
            consolePrint("Unexpected error while getting selected items - " + e.getMessage());
            consolePrint(e.toString());
        }
        return selItems;
    }

    public static InventoryMetaItem getInventoryItem(String item) {
        return getInventoryItem(Mod.hud.getInventoryWindow().getInventoryListComponent(), item);
    }
    public static InventoryMetaItem getInventoryItem(InventoryListComponent ilc, String item) {
        try {
            List<InventoryMetaItem> items = getSelectedItems(ilc, true, true);
            if (items == null || items.size() == 0) {
                return null;
            }
            for (InventoryMetaItem invItem : items) {
                if (invItem.getBaseName().contains(item)) {
                    return invItem;
                }
            }
        } catch (Exception e) {
            consolePrint("Got error while searching for " + item + " in your inventory. Error - " + e.getMessage());
            consolePrint( e.toString());
        }
        return null;
    }

    public static List<InventoryMetaItem> getInventoryItems(String item) {
        return getInventoryItems(Mod.hud.getInventoryWindow().getInventoryListComponent(), item);
    }
    public static List<InventoryMetaItem> getInventoryItems(InventoryListComponent ilc, String item) {
        List<InventoryMetaItem> targets = new ArrayList<>();
        try {
            List<InventoryMetaItem> items = getSelectedItems(ilc, true, true);
            if (items == null || items.size() == 0) {
                return targets;
            }
            for (InventoryMetaItem invItem : items) {
                if (invItem.getBaseName().contains(item)) {
                    targets.add(invItem);
                }
            }
        } catch (Exception e) {
            consolePrint("Got error while searching for " + item + " in your inventory. Error - " + e.getMessage());
            consolePrint( e.toString());
        }
        return targets;
    }

    public static List<InventoryMetaItem> getInventoryItemsAtPoint(int x, int y) {
        return getInventoryItemsAtPoint(Mod.hud.getInventoryWindow().getInventoryListComponent(), x, y);
    }
    public static List<InventoryMetaItem> getInventoryItemsAtPoint(InventoryListComponent ilc, int x, int y) {
        List<InventoryMetaItem> itemList = new ArrayList<>();
        try {
            WurmTreeList wtl = ReflectionUtil.getPrivateField(ilc,
                    ReflectionUtil.getField(ilc.getClass(), "itemList"));
            Method getNodeAt = ReflectionUtil.getMethod(wtl.getClass(), "getNodeAt");
            getNodeAt.setAccessible(true);
            Object hoveredNode = getNodeAt.invoke(wtl, x, y);
            if (hoveredNode != null) {
                List childLines = new ArrayList(ReflectionUtil.getPrivateField(hoveredNode,
                        ReflectionUtil.getField(hoveredNode.getClass(), "children")));
                List<InventoryMetaItem> items = Utils.getSelectedItems(childLines, true, true);
                Object lineItem = ReflectionUtil.getPrivateField(hoveredNode,
                        ReflectionUtil.getField(hoveredNode.getClass(), "item"));
                InventoryMetaItem item = ReflectionUtil.getPrivateField(lineItem,
                        ReflectionUtil.getField(lineItem.getClass(), "item"));
                boolean isContainer = ReflectionUtil.getPrivateField(lineItem,
                        ReflectionUtil.getField(lineItem.getClass(), "isContainer"));
                if (childLines.size() == 0 || isContainer)
                    items.add(item);
            }
        } catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return itemList;
    }
    public static List<InventoryMetaItem> getFirstLevelItems() {
        return getFirstLevelItems(Mod.hud.getInventoryWindow().getInventoryListComponent());
    }
    public static List<InventoryMetaItem> getFirstLevelItems(InventoryListComponent ilc) {
        try {
            WurmTreeList wtl = ReflectionUtil.getPrivateField(ilc,
                    ReflectionUtil.getField(ilc.getClass(), "itemList"));
            Object rootNode = ReflectionUtil.getPrivateField(wtl,
                    ReflectionUtil.getField(wtl.getClass(), "rootNode"));
            @SuppressWarnings("unchecked")
            List lines = new ArrayList(ReflectionUtil.getPrivateField(rootNode,
                    ReflectionUtil.getField(rootNode.getClass(), "children")));
            Object nodeLineItem = ReflectionUtil.getPrivateField(lines.get(1),
                    ReflectionUtil.getField(lines.get(1).getClass(), "item"));
            InventoryMetaItem nodeItem = ReflectionUtil.getPrivateField(nodeLineItem,
                    ReflectionUtil.getField(nodeLineItem.getClass(), "item"));
            return new ArrayList<>(nodeItem.getChildren());
        } catch (Exception e) {
            Utils.consolePrint("getFirstLevelItems() has encountered an error - " + e.getMessage());
            Utils.consolePrint( e.toString());
        }
        return new ArrayList<>();
    }

    public static InventoryMetaItem getRootItem(InventoryListComponent ilc) {
        try {
            Object listRootItem = ReflectionUtil.getPrivateField(ilc,
                    ReflectionUtil.getField(ilc.getClass(), "rootItem"));
            return ReflectionUtil.getPrivateField(listRootItem,
                    ReflectionUtil.getField(listRootItem.getClass(), "item"));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static WurmComponent getTargetComponent(Function<WurmComponent, Boolean> filter) {
        int x = Mod.hud.getWorld().getClient().getXMouse();
        int y = Mod.hud.getWorld().getClient().getYMouse();
        try {
            for (int i = 0; i < Mod.components.size(); i++) {
                WurmComponent wurmComponent = Mod.components.get(i);
                if (wurmComponent.contains(x, y)) {
                    if (filter != null && !filter.apply(wurmComponent))
                        continue;
                    return wurmComponent;
                }
            }
        } catch (Exception e) {
            Utils.consolePrint("Can't get target component! Error - " + e.getMessage());
            Utils.consolePrint( e.toString());
        }
        return null;
    }

    public static int[][] getAreaCoordinates() {
        int area[][] = new int[9][2];
        int x = Mod.hud.getWorld().getPlayerCurrentTileX();
        int y = Mod.hud.getWorld().getPlayerCurrentTileY();
        int direction = Math.round(Mod.hud.getWorld().getPlayerRotX() / 90);
        switch (direction) {
            case 1:
                for (int i = 0; i < 3; i++)
                    for (int j = 0; j < 3; j++) {
                        area[i * 3 + j][0] = x + i - 1;
                        area[i * 3 + j][1] = y + j - 1;
                    }
                break;
            case 2:
                for (int j = 0; j < 3; j++)
                    for (int i = 0; i < 3; i++) {
                        area[j * 3 + i][0] = x - i + 1;
                        area[j * 3 + i][1] = y + j - 1;
                    }
                break;
            case 3:
                for (int i = 0; i < 3; i++)
                    for (int j = 0; j < 3; j++) {
                        area[i * 3 + j][0] = x - i + 1;
                        area[i * 3 + j][1] = y - j + 1;
                    }
                break;
            default:
                for (int j = 0; j < 3; j++)
                    for (int i = 0; i < 3; i++) {
                        area[j * 3 + i][0] = x + i - 1;
                        area[j * 3 + i][1] = y - j + 1;
                    }
        }
        return area;
    }

    public static URL getResource(String r) {
        URL url = Mod.class.getClassLoader().getResource(r);
        if (url == null && Mod.class.getClassLoader() == HookManager.getInstance().getLoader()) {
            url = HookManager.getInstance().getClassPool().find(Mod.class.getName());
            if (url != null) {
                String path = url.toString();
                int pos = path.lastIndexOf('!');
                if (pos != -1) {
                    if (r.substring(0,1).equals("/"))
                        r = r.substring(1);
                    path = path.substring(0, pos) + "!/" + r;
                }
                try {
                    url = new URL(path);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        }
        return url;
    }

    public static float getTotalWeight() {
        PaperDollInventory paperDollInventory = Mod.hud.getPaperDollInventory();
        try {
            PaperDollSlot equippedWeightItem = ReflectionUtil.getPrivateField(paperDollInventory,
                    ReflectionUtil.getField(paperDollInventory.getClass(), "equippedWeightItem"));
            InventoryMetaItem inventoryItem = ReflectionUtil.getPrivateField(paperDollInventory,
                    ReflectionUtil.getField(paperDollInventory.getClass(), "inventoryItem"));
            return equippedWeightItem.getWeight() + inventoryItem.getWeight();
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return 0;
    }
    public static float getMaxWeight() {
        float bs = SkillLogicSet.getSkill("Body strength").getValue();
        return bs * 7;
    }

    public static long[] getItemIds(List<InventoryMetaItem> container) {
        if (container == null)
            return null;
        long[] ids = new long[container.size()];
        for (int i = 0; i < container.size(); i++) {
            ids[i] = container.get(i).getId();
        }
        return ids;
    }

    public static int getMaxActionNumber() {
        MindLogicCalculator mlc;
        try {
            mlc = ReflectionUtil.getPrivateField(Mod.hud,
                    ReflectionUtil.getField(Mod.hud.getClass(), "mindLogicCalculator"));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
            return 0;
        }
        return mlc.getMaxNumberOfActions();
    }
}
