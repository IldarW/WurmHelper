package net.ildar.wurm;

import com.wurmonline.shared.util.MulticolorLineSegment;
import net.ildar.wurm.bot.ArcherBot;
import net.ildar.wurm.bot.GuardBot;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Chat {
    private static Map<Function<String, Boolean>, Runnable> eventProcessors = new HashMap<>();

    // if (filter.apply(message)) callback.run()
    public static void registerEventProcessor(Function<String, Boolean> filter, Runnable callback) {
        eventProcessors.put(filter, callback);
    }
    public static void unregisterEventProcessor(Function<String, Boolean> filter) {
        eventProcessors.remove(filter);
    }

    public static void clearEventProcessors() {
        eventProcessors.clear();
    }

    public static void  onMessage(String context, Object input, boolean silent) {
        String message;
        if (input instanceof List) {
            message = pruneMulticolorString((List<MulticolorLineSegment>) input);
        } else
            message = (String)input;
        message = message.substring(11).trim();
        if (message.isEmpty()) return;
        switch (context) {
            case ":Event":
                for (Map.Entry entry : eventProcessors.entrySet()) {
                    Function<String, Boolean> filter = (Function) entry.getKey();
                    Runnable callback = (Runnable) entry.getValue();
                    if (filter.apply(message)) callback.run();
                }
                GuardBot.processEvent(message);
                break;
            case ":Combat":
                if (input instanceof List)
                    modifyCombatMessage((List<MulticolorLineSegment>) input);
                if (message.contains("The string breaks!"))
                    ArcherBot.stringBreaks = true;
                GuardBot.processEvent(message);
                break;
        }
    }

    private static String pruneMulticolorString(List<MulticolorLineSegment> multicolorString) {
        StringBuilder sb = new StringBuilder();
        for (Iterator<MulticolorLineSegment> iter = multicolorString.iterator(); iter.hasNext(); ) {
            MulticolorLineSegment segment = iter.next();
            sb.append(segment.getText());
        }
        return sb.toString();
    }

    //colorizes with blue the part of the message describing the part of your body that your enemy set target to
    private static void modifyCombatMessage(List<MulticolorLineSegment> segments) {
        for (Iterator<MulticolorLineSegment> iter = segments.iterator(); iter.hasNext(); ) {
            MulticolorLineSegment segment = iter.next();
            if (segment.getText().startsWith(" targets your")) {
                iter.remove();
                MulticolorLineSegment newsegment = new MulticolorLineSegment(" targets ", (byte)0);
                segments.add(newsegment);
                newsegment = new MulticolorLineSegment(segment.getText().substring(9), (byte)12);
                segments.add(newsegment);
                break;
            }
        }
    }
}
