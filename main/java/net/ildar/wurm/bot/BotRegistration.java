package net.ildar.wurm.bot;

class BotRegistration {
    private Class<? extends Bot> botClass;
    private String description;
    private String abbreviation;

    BotRegistration(Class<? extends Bot> botClass, String description, String abbreviation) {
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
