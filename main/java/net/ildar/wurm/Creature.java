package net.ildar.wurm;

import com.wurmonline.client.renderer.cell.CreatureCellRenderable;

public class Creature {
    private long id;
    private String modelName;
    private String hoverName;
    private CreatureCellRenderable creature;
    private int status;
    private long lastGroom;

    public static final int PROCESSED = 1;

    public Creature(long id, CreatureCellRenderable creature, String modelName, String hoverName) {
        this.id = id;
        this.creature = creature;
        this.modelName = modelName;
        this.hoverName = hoverName;
    }

    @Override
    public boolean equals(Object obj) {
        Creature creature = (Creature)obj;
        return creature.getId() == this.getId();
    }


    public long getId() {
        return this.id;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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

    public String getModelName() {
        return this.modelName;
    }

    public boolean isPlayer() {
        return (this.getModelName().contains("model.creature.humanoid.human.player") && !this.getModelName().contains("zombie"));
    }

    public boolean isMob() {
        return this.getModelName().contains("model.creature") && !this.getModelName().contains("humanoid.human");
    }

    public float getX(){
        return creature.getXPos();
    }
    public float getY(){
        return creature.getYPos();
    }
}

