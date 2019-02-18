package net.ildar.wurm;

import net.ildar.wurm.bot.Bot;

public class BotRegistration {
    private Class<? extends Bot> botClass;
    private String description;
    private String abbreviation;

    public BotRegistration(Class<? extends Bot> botClass, String description, String abbreviation) {
        this.botClass = botClass;
        this.description = description;
        this.abbreviation = abbreviation;
    }

    public Class<? extends Bot> getBotClass() {
        return botClass;
    }

    public String getDescription() {
        return description;
    }

    public String getAbbreviation() {
        return abbreviation;
    }
}
