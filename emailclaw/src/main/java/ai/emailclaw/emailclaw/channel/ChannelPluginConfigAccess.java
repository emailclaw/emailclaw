/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ai.emailclaw.emailclaw.channel;

import ai.emailclaw.emailclaw.model.ChannelInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Access utility class for plugin configuration, used to safely read and write typed data from ChannelInfo.pluginConfig.
 */
public final class ChannelPluginConfigAccess {

    private ChannelPluginConfigAccess() {}

    private static void ensureMap(ChannelInfo channel) {
        if (channel.getPluginConfig() == null) {
            channel.setPluginConfig(new LinkedHashMap<>());
        }
    }

    public static String str(ChannelInfo channel, String key, String def) {
        if (channel.getPluginConfig() == null || !channel.getPluginConfig().containsKey(key)) {
            return def;
        }
        Object val = channel.getPluginConfig().get(key);
        return val == null ? def : String.valueOf(val);
    }

    public static void putStr(ChannelInfo channel, String key, String val) {
        ensureMap(channel);
        channel.getPluginConfig().put(key, val);
    }

    public static boolean bool(ChannelInfo channel, String key, boolean def) {
        if (channel.getPluginConfig() == null || !channel.getPluginConfig().containsKey(key)) {
            return def;
        }
        Object val = channel.getPluginConfig().get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    public static void putBool(ChannelInfo channel, String key, boolean val) {
        ensureMap(channel);
        channel.getPluginConfig().put(key, val);
    }

    public static int intVal(ChannelInfo channel, String key, int def) {
        if (channel.getPluginConfig() == null || !channel.getPluginConfig().containsKey(key)) {
            return def;
        }
        Object val = channel.getPluginConfig().get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public static void putInt(ChannelInfo channel, String key, int val) {
        ensureMap(channel);
        channel.getPluginConfig().put(key, val);
    }

    public static long longVal(ChannelInfo channel, String key, long def) {
        if (channel.getPluginConfig() == null || !channel.getPluginConfig().containsKey(key)) {
            return def;
        }
        Object val = channel.getPluginConfig().get(key);
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public static void putLong(ChannelInfo channel, String key, long val) {
        ensureMap(channel);
        channel.getPluginConfig().put(key, val);
    }

    @SuppressWarnings("unchecked")
    public static List<String> strList(ChannelInfo channel, String key) {
        if (channel.getPluginConfig() == null || !channel.getPluginConfig().containsKey(key)) {
            return new ArrayList<>();
        }
        Object val = channel.getPluginConfig().get(key);
        if (val instanceof List<?> l) {
            List<String> result = new ArrayList<>();
            for (Object item : l) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    public static void putStrList(ChannelInfo channel, String key, List<String> val) {
        ensureMap(channel);
        channel.getPluginConfig().put(key, val);
    }

    public static void remove(ChannelInfo channel, String key) {
        if (channel.getPluginConfig() != null) {
            channel.getPluginConfig().remove(key);
        }
    }

    public static Map<String, Object> config(ChannelInfo channel) {
        if (channel.getPluginConfig() == null) {
            return Collections.emptyMap();
        }
        return channel.getPluginConfig();
    }
}
