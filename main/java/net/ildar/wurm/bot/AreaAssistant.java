package net.ildar.wurm.bot;

import net.ildar.wurm.Utils;

public class AreaAssistant {
    private final int MOVES_IN_STEP = 20;//each step(tile) is divided to this number of moves
    private final int STEP_NUMBER = 3;//in tiles

    private long stepTimeout = 1000;

    private Bot bot;
    private int height = 0, width = 0;

    //start point - bottom left corner of area
    private int curX = 0, curY = 0;
    private boolean turnedRight = false;

    public AreaAssistant(Bot bot) {
        this.bot = bot;
    }
    public void areaNextPosition() {
        if (!areaTourActivated()) return;
        try {
            if (curX < height - 1) {
                for (int steps = 0; steps < STEP_NUMBER; steps++) {
                    if (curX >= height - 1) break;
                    for(int moves = 0; moves < MOVES_IN_STEP; moves++) {
                        Utils.movePlayer(4.0f / MOVES_IN_STEP);
                        Thread.sleep(stepTimeout / MOVES_IN_STEP);
                    }
                    curX++;
                }
            } else if (curY < width - 1) {
                if (turnedRight)
                    Utils.turnPlayer(-90);
                else
                    Utils.turnPlayer(90);
                Thread.sleep(300);
                for (int steps = 0; steps < STEP_NUMBER; steps++) {
                    if (curY >= width - 1) break;
                    for(int moves = 0; moves < MOVES_IN_STEP; moves++) {
                        Utils.movePlayer(4.0f / MOVES_IN_STEP);
                        Thread.sleep(stepTimeout / MOVES_IN_STEP);
                    }
                    curY++;
                }
                if (turnedRight)
                    Utils.turnPlayer(-90);
                else
                    Utils.turnPlayer(90);
                turnedRight = !turnedRight;
                curX = 0;
            } else
                stopAreaTour();
            Utils.stabilizePlayer();
        } catch(InterruptedException ignored) {}
    }

    public void stopAreaTour() {
        Utils.consolePrint("Area tour is ended");
        height = 0;
        width = 0;
        curX = 0;
        curY = 0;
        turnedRight = false;
    }

    public boolean areaTourActivated() {
        return height != 0 && width != 0;
    }

    public void startAreaTour(int tilesForward, int tilesToRight) {
        height = tilesForward;
        width = tilesToRight;
        curX = curY = 0;
        turnedRight = false;
        Utils.stabilizePlayer();
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
