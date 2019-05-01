package net.ildar.wurm.annotations;

class AnnotatedBot {
    /**
     * Annotation with bot info
     */
    BotInfo botInfo;
    /**
     * Fully qualified class name of a bot
     */
    String botClass;

    AnnotatedBot(BotInfo botInfo, String botClass) {
        this.botInfo = botInfo;
        this.botClass = botClass;
    }
}
