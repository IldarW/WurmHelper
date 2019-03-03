package net.ildar.wurm.bot;

import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;

class AreaAssistant {
    private final static int STEPS_IN_MOVE = 5;//each moving is divided to this number of steps for each tile

    private int moveAheadDistance = 3;//in tiles
    private int moveRightDistance = 3;//in tiles
    private long stepTimeout = 1000;

    private Bot bot;
    private int height = 0, width = 0;

    //start point - bottom left corner of area
    private int movedAhead = 0, movedToRight = 0;
    private int startX, startY;
    private int startDirection;

    private boolean turnedRight = false;

    AreaAssistant(Bot bot) {
        this.bot = bot;
        bot.registerInputHandler(InputKey.area, this::toggleAreaTour);
        bot.registerInputHandler(InputKey.area_speed, this::setAreaModeSpeed);
    }
    void areaNextPosition() throws InterruptedException{
        if (!areaTourActivated()) return;
        recalculateBiases();
        if (movedAhead < 0 || movedAhead > height - 1 || movedToRight < 0 || movedToRight > width - 1) {
            Utils.consolePrint("Player leaved the area");
            stopAreaTour();
            return;
        }
        turnPlayer();
        if (movedAhead < height - 1) {
            for (int tiles = 0; tiles < moveAheadDistance; tiles++) {
                if (movedAhead >= height - 1) break;
                Utils.movePlayerBySteps(4, STEPS_IN_MOVE, stepTimeout);
                movedAhead++;
            }
        } else if (movedToRight < width - 1) {
            if (turnedRight)
                Utils.turnPlayer(-90);
            else
                Utils.turnPlayer(90);
            Thread.sleep(300);
            for (int tiles = 0; tiles < moveRightDistance; tiles++) {
                if (movedToRight >= width - 1) break;
                Utils.movePlayerBySteps(4, STEPS_IN_MOVE, stepTimeout);
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

    private void turnPlayer(){
        if (turnedRight) {
            Utils.turnPlayer(((startDirection+2)%4) * 90, 0);
        } else {
            Utils.turnPlayer(startDirection * 90, 0);
        }
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
        Utils.showOnScreenMessage("Area tour is ended");
        height = 0;
        width = 0;
        movedAhead = 0;
        movedToRight = 0;
        turnedRight = false;
    }

    boolean areaTourActivated() {
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

    void setMoveAheadDistance(int moveAheadDistance) {
        this.moveAheadDistance = moveAheadDistance;
    }

    void setMoveRightDistance(int moveRightDistance) {
        this.moveRightDistance = moveRightDistance;
    }

    void toggleAreaTour(String[] input) {
        if (areaTourActivated()) {
            stopAreaTour();
        } else  {
            if (input != null && input.length == 2) {
                try {
                    startAreaTour(Integer.parseInt(input[0]), Integer.parseInt(input[1]));
                    Utils.consolePrint("Activated area mode for " + bot.getClass().getSimpleName());
                } catch (NumberFormatException e) {
                    Utils.consolePrint("Wrong area size!");
                    bot.printInputKeyUsageString(InputKey.area);
                }
            }
            else
                bot.printInputKeyUsageString(InputKey.area);
        }
    }

    private void setAreaModeSpeed(String []input) {
        if (input == null || input.length != 1) {
            bot.printInputKeyUsageString(InputKey.area_speed);
            return;
        }
        float speed;
        try {
            speed = Float.parseFloat(input[0]);
            if (speed < 0) {
                Utils.consolePrint("Speed can not be negative");
                return;
            }
            if (speed == 0) {
                Utils.consolePrint("Speed can not be equal to 0");
                return;
            }
            this.stepTimeout = (long) (1000 / speed);
            Utils.consolePrint(String.format("The speed for area mode was set to %.2f", speed));
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong speed value");
        }
    }

    private enum InputKey implements Bot.InputKey {
        area("Toggle the area processing mode. ", "tiles_ahead tiles_to_the_right"),
        area_speed("Set the speed of moving for area mode. Default value is 1 second per tile.", "speed(float value)");

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
