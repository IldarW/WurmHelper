package net.ildar.wurm.bot;

import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;

public class AreaAssistant {
    private final int MOVES_IN_STEP = 5;//each step(tile) is divided to this number of moves
    private final int STEP_NUMBER = 3;//in tiles

    private long stepTimeout = 1000;

    private Bot bot;
    private int height = 0, width = 0;

    //start point - bottom left corner of area
    private int movedAhead = 0, movedToRight = 0;
    private int startX, startY;
    private int startDirection;

    private boolean turnedRight = false;

    public AreaAssistant(Bot bot) {
        this.bot = bot;
    }
    public void areaNextPosition() throws InterruptedException{
        if (!areaTourActivated()) return;
        recalculateBiases();
        if (movedAhead < 0 || movedAhead > height - 1 || movedToRight < 0 || movedToRight > width - 1) {
            Utils.consolePrint("Player leaved the area");
            stopAreaTour();
            return;
        }
        if (movedAhead < height - 1) {
            for (int steps = 0; steps < STEP_NUMBER; steps++) {
                if (movedAhead >= height - 1) break;
                for(int moves = 0; moves < MOVES_IN_STEP; moves++) {
                    Utils.movePlayer(4.0f / MOVES_IN_STEP);
                    Thread.sleep(stepTimeout / MOVES_IN_STEP);
                }
                movedAhead++;
            }
        } else if (movedToRight < width - 1) {
            if (turnedRight)
                Utils.turnPlayer(-90);
            else
                Utils.turnPlayer(90);
            Thread.sleep(300);
            for (int steps = 0; steps < STEP_NUMBER; steps++) {
                if (movedToRight >= width - 1) break;
                for(int moves = 0; moves < MOVES_IN_STEP; moves++) {
                    Utils.movePlayer(4.0f / MOVES_IN_STEP);
                    Thread.sleep(stepTimeout / MOVES_IN_STEP);
                }
                movedToRight++;
            }
            if (turnedRight)
                Utils.turnPlayer(-90);
            else
                Utils.turnPlayer(90);
            turnedRight = !turnedRight;
            movedAhead = 0;
        } else
            stopAreaTour();
        Utils.stabilizePlayer();
    }

    private void recalculateBiases() {
        int x = Mod.hud.getWorld().getPlayerCurrentTileX();
        int y = Mod.hud.getWorld().getPlayerCurrentTileY();
        switch (startDirection) {
            case 1://east, x is increasing
                movedAhead = x - startX;
                movedToRight = y - startY;
                break;
            case 2://south, y is increasing
                movedAhead = y - startY;
                movedToRight = startX - x;
                break;
            case 3://west, x is decreasing
                movedAhead = startX - x;
                movedToRight = startY - y;
                break;
            default://north, y is decreasing
                movedAhead = startY - y;
                movedToRight = x - startX;
                break;
        }
        if (turnedRight)
            movedAhead = height - movedAhead - 1;
    }

    private void stopAreaTour() {
        Mod.hud.addOnscreenMessage("Area tour is ended", 1, 1, 1, (byte)1);
        height = 0;
        width = 0;
        movedAhead = 0;
        movedToRight = 0;
        turnedRight = false;
    }

    public boolean areaTourActivated() {
        return height != 0 && width != 0;
    }

    private void startAreaTour(int tilesForward, int tilesToRight) {
        height = tilesForward;
        width = tilesToRight;
        movedAhead = movedToRight = 0;
        startX = Mod.hud.getWorld().getPlayerCurrentTileX();
        startY = Mod.hud.getWorld().getPlayerCurrentTileY();
        turnedRight = false;
        Utils.stabilizePlayer();
        startDirection = Math.round(Mod.hud.getWorld().getPlayerRotX() / 90);
    }

    /**
     * @return true on success
     */
    public boolean toggleAreaTour(String[] input) {
        if (areaTourActivated()) {
            stopAreaTour();
            return true;
        } else  {
            if (input.length == 2) {
                try {
                    startAreaTour(Integer.parseInt(input[0]), Integer.parseInt(input[1]));
                    Utils.consolePrint("Activated area mode for " + bot.getClass().getSimpleName());
                    return true;
                } catch (NumberFormatException e) {
                    Utils.consolePrint("Wrong area size!");
                    return false;
                }
            }
            else
                return false;
        }
    }

    public void setStepTimeout(long stepTimeout) {
        this.stepTimeout = stepTimeout;
    }
}
