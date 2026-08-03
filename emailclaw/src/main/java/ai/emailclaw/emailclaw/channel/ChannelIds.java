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

/**
 * Channel identifier constants in channel plugins and configurations.
 *
 * <p>Keep consistent with the {@code id} field of each record in {@code channels.json}, session {@code channel} field, and
 * the return value of {@link ai.emailclaw.emailclaw.plugin.EmailclawPlugin#id()}.
 */
public final class ChannelIds {

    public static final String CONSOLE = "console";
    public static final String EMAILCLAW = "emailclaw";

    //    public static final String DINGTALK = "dingtalk";

    //    public static final String FEISHU = "feishu";
    //    public static final String IMESSAGE = "imessage";
    //    public static final String DISCORD = "discord";
    //    public static final String TELEGRAM = "telegram";
    //    public static final String QQ = "qq";
    //    public static final String MATRIX = "matrix";
    //    public static final String SIP = "sip";
    //    public static final String XIAOYI = "xiaoyi";
    //    public static final String MATTERMOST = "mattermost";
    //    public static final String MQTT = "mqtt";
    //    public static final String VOICE = "voice";
    //    public static final String WECOM = "wecom";
    //    public static final String WEIXIN = "weixin";
    //    public static final String ONEBOT = "onebot";

    private ChannelIds() {}
}
