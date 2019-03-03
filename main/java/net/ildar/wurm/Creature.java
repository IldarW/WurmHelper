package net.ildar.wurm;

import com.wurmonline.client.renderer.cell.CreatureCellRenderable;

public class Creature {
    private long id;
    private String modelName;
    private String hoverName;
    private CreatureCellRenderable creatureCellRenderable;
    private boolean groomed;
    private long lastGroom;
    private long lastAction;
    private boolean available;

    public Creature(long id, CreatureCellRenderable creature, String modelName, String hoverName) {
        this.id = id;
        this.creatureCellRenderable = creature;
        this.modelName = modelName;
        this.hoverName = hoverName;
        this.available = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Creature) {
            Creature creature = (Creature) obj;
            return creature.getId() == this.getId();
        }
        return super.equals(obj);
    }


    public long getId() {
        return this.id;
    }

    public boolean isGroomed() {
        return groomed;
    }

    public void setGroomed(boolean groomed) {
        this.groomed = groomed;
    }

    public long getLastGroom() {
        return lastGroom;
    }

    public void setLastGroom(long lastGroom) {
        this.lastGroom = lastGroom;
    }

    public String getHoverName() {
        return this.hoverName;
    }

    private String getModelName() {
        return this.modelName;
    }

    public boolean isPlayer() {
        return (this.getModelName().contains("model.creature.humanoid.human.player")
                && !this.getModelName().contains("zombie"));
    }

    public boolean isGroomableMob() {
        return this.getModelName().contains("model.creature")
                && !this.getModelName().contains("humanoid.human")
                && !this.getModelName().contains("zombie");
    }

    public float getX() {
        return creatureCellRenderable.getXPos();
    }

    public float getY() {
        return creatureCellRenderable.getYPos();
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public long getLastAction() {
        return lastAction;
    }

    public void setLastAction(long lastAction) {
        this.lastAction = lastAction;
    }
}

