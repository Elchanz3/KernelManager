package com.luk.kernelmanager.utils.kernel.soc;

import android.content.Context;

import com.luk.kernelmanager.utils.Utils;
import com.luk.kernelmanager.utils.root.Control;

import java.util.ArrayList;
import java.util.List;

/**
 * Small helpers for plain sysfs nodes: read through root, write through
 * {@link Control#runSetting} so every write is recorded for Apply on Boot under
 * the page's category.
 */
public class SocNode {

    public static boolean exists(String path) {
        return Utils.existFile(path);
    }

    public static String read(String path) {
        String value = Utils.readFile(path);
        return value == null ? "" : value.trim();
    }

    /** First whitespace-separated token as an int, or {@code fallback}. */
    public static int readInt(String path, int fallback) {
        String value = read(path);
        if (value.isEmpty()) return fallback;
        try {
            return Integer.parseInt(value.split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static List<Integer> readIntList(String path) {
        List<Integer> list = new ArrayList<>();
        for (String token : read(path).split("\\s+")) {
            try {
                list.add(Integer.parseInt(token));
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    public static void write(String path, String value, String category, Context context) {
        Control.runSetting(Control.write(value, path), category, path, context);
    }

    /** Write the same value to several nodes as one Apply on Boot entry. */
    public static void writeAll(List<String> paths, String value, String category, String id,
                                Context context) {
        StringBuilder command = new StringBuilder();
        for (String path : paths) {
            if (command.length() > 0) command.append(" ; ");
            command.append(Control.write(value, path));
        }
        Control.runSetting(command.toString(), category, id, context);
    }

    public static String mhz(int khz) {
        return (khz / 1000) + " MHz";
    }
}
