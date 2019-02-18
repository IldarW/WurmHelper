package net.ildar.wurm.bot;


import net.ildar.wurm.BotRegistration;
import net.ildar.wurm.Utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class GuardBot extends Bot {
    private static Set <String> keywords;
    private static long lastEvent;

    private String customSound;
    private long alarmTimeout;

    public static BotRegistration getRegistration() {
        return new BotRegistration(GuardBot.class,
                "Looks for messages in Event and Combat tabs. " +
                        "Raises alarm if no messages were received during configured time. " +
                        "With no provided keywords the bot will be satisfied with every message. " +
                        "If user adds some keywords bot will compare messages only with them.",
                "g");
    }

    public GuardBot() {
        registerInputHandler(GuardBot.InputKey.a, this::addKeyword);
        registerInputHandler(GuardBot.InputKey.at, this::setAlarmTimeout);
        registerInputHandler(GuardBot.InputKey.soundtest, input -> playSound());
        registerInputHandler(GuardBot.InputKey.cs, this::setSoundFileName);
    }

    @Override
    public void work() throws Exception{
        lastEvent = System.currentTimeMillis();
        setAlarmTimeout(300000);
        while (isActive()) {
            waitOnPause();
            if (Math.abs(lastEvent - System.currentTimeMillis()) > alarmTimeout) {
                playSound();
                lastEvent += 60000;
            }
            sleep(timeout);
        }
    }

    public void deactivate() {
        super.deactivate();
        keywords = null;
    }

    private void playSound() {
        try {
            InputStream soundStream;
            if (customSound != null)
                soundStream = new FileInputStream(customSound);
            else
                soundStream = Utils.getResource("alarm_sound.wav").openStream();
            new com.sun.media.sound.JavaSoundAudioClip(soundStream).play();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setAlarmTimeout(int timeout) {
        if (timeout < 60000) {
            Utils.consolePrint("Too small timeout!");
            timeout = 60000;
        }
        this.alarmTimeout = timeout;
        Utils.consolePrint("Current alarm timeout is " + timeout + " milliseconds");
    }

    private void setAlarmTimeout(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(GuardBot.InputKey.at);
            return;
        }
        try {
            int timeout = Integer.parseInt(input[0]);
            setAlarmTimeout(timeout);
        } catch (Exception e) {
            Utils.consolePrint("Wrong timeout value!");
        }
    }

    private void addKeyword(String []input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(GuardBot.InputKey.a);
            return;
        }
        StringBuilder keyword = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++)
            keyword.append(" ").append(input[i]);
        addKeyword(keyword.toString());
    }

    public static void processEvent(String message) {
        if (keywords == null)
            lastEvent = System.currentTimeMillis();
        else
            for(String keyword:keywords)
                if (message.contains(keyword)) {
                    lastEvent = System.currentTimeMillis();
                    return;
                }
    }

    private static void addKeyword(String keyword) {
        if (keywords == null)
            keywords = new HashSet<>();
        keywords.add(keyword);
        Utils.consolePrint("Current keyword set in GuardBot - " + keywords.toString());

    }

    private void setSoundFileName(String [] input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(GuardBot.InputKey.cs);
            return;
        }
        StringBuilder path = new StringBuilder(input[0]);
        for (int i = 1; i < input.length; i++) {
            path.append(" ").append(input[i]);
        }
        customSound = path.toString();
        Utils.consolePrint("Alarm sound is now \"" + customSound + "\"");
    }

    private enum InputKey implements Bot.InputKey {
        at("Set the alarm timeout. " +
                "Alarm will be raised if no valid messages was processed during that period",
                "timeout(in milliseconds)"),
        a("Add new keyword", "keyword"),
        cs("Set a path to a custom sound file for alarm. Use .wav file", "path"),
        soundtest("Plays the alarm sound", "");

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
