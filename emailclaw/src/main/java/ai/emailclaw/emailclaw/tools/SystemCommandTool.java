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
package ai.emailclaw.emailclaw.tools;

import ai.emailclaw.emailclaw.model.TokenUsageRecord;
import ai.emailclaw.emailclaw.service.ToolService;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.VideoBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * System and OS level tools.
 */
public class SystemCommandTool extends BaseEmailclawTool {

    private static final Logger LOGGER = Logger.getLogger(SystemCommandTool.class.getName());

    private static final Set<String> IMAGE_EXTS =
            Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".tif", ".tiff");
    private static final Set<String> VIDEO_EXTS =
            Set.of(".mp4", ".mov", ".avi", ".mkv", ".webm", ".mpeg", ".mpg", ".m4v");
    private static final long TOOL_MEDIA_MAX_BYTES = 10L * 1024L * 1024L;

    public SystemCommandTool() {}

    @Tool(name = BuiltInToolNames.DESKTOP_SCREENSHOT, description = "Capture desktop screenshots")
    public String desktopScreenshot(
            @ToolParam(name = "output_path", description = "Output png path", required = false)
                    String outputPath) {
        if (off(BuiltInToolNames.DESKTOP_SCREENSHOT)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        try {
            Path out =
                    outputPath == null || outputPath.isBlank()
                            ? context.currentWorkspace()
                                    .resolve("desktop-" + System.currentTimeMillis() + ".png")
                            : resolveInScope(outputPath);
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            BufferedImage image = new Robot().createScreenCapture(new Rectangle(screen));
            ImageIO.write(image, "png", out.toFile());
            return "Screenshot saved: " + out;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = BuiltInToolNames.VIEW_IMAGE,
            description = "Load an image into LLM context for visual analysis")
    public ToolResultBlock viewImage(
            @ToolParam(name = "path", description = "Image path") String path) {
        if (off(BuiltInToolNames.VIEW_IMAGE)) {
            return ToolResultBlock.text("Tool disabled.");
        }
        try {
            Path p = resolveInScope(path);
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                return ToolResultBlock.error("Image file not found: " + p);
            }
            BufferedImage img = ImageIO.read(p.toFile());
            if (img == null) {
                return ToolResultBlock.error("Not a valid image file.");
            }
            Base64Source source = toBase64Source(p, "image/png", TOOL_MEDIA_MAX_BYTES);
            if (source == null) {
                return ToolResultBlock.error(
                        "Image is too large or unreadable. Max supported size is "
                                + (TOOL_MEDIA_MAX_BYTES / (1024 * 1024))
                                + " MB.");
            }
            List<ContentBlock> output = new ArrayList<>();
            output.add(ImageBlock.builder().source(source).build());
            if (!activeModelSupportsImage()) {
                output.add(
                        TextBlock.builder()
                                .text(
                                        "Warning: active model may not support image multimodal"
                                                + " input. If analysis fails, switch to a"
                                                + " vision-capable model.")
                                .build());
            } else {
                output.add(
                        TextBlock.builder()
                                .text(
                                        "Loaded image: "
                                                + p
                                                + " ("
                                                + img.getWidth()
                                                + "x"
                                                + img.getHeight()
                                                + ")")
                                .build());
            }
            return ToolResultBlock.of(output);
        } catch (Exception e) {
            return ToolResultBlock.error("Error: " + e.getMessage());
        }
    }

    @Tool(
            name = BuiltInToolNames.VIEW_VIDEO,
            description = "Load a video into LLM context for visual analysis")
    public ToolResultBlock viewVideo(
            @ToolParam(name = "path", description = "Video path") String path) {
        if (off(BuiltInToolNames.VIEW_VIDEO)) {
            return ToolResultBlock.text("Tool disabled.");
        }
        try {
            Path p = resolveInScope(path);
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                return ToolResultBlock.error("Video file not found: " + p);
            }
            Base64Source source = toBase64Source(p, "video/mp4", TOOL_MEDIA_MAX_BYTES);
            if (source == null) {
                return ToolResultBlock.error(
                        "Video is too large or unreadable. Max supported size is "
                                + (TOOL_MEDIA_MAX_BYTES / (1024 * 1024))
                                + " MB.");
            }
            List<ContentBlock> output = new ArrayList<>();
            output.add(VideoBlock.builder().source(source).build());
            if (!activeModelSupportsVideo()) {
                output.add(
                        TextBlock.builder()
                                .text(
                                        "Warning: active model may not support video multimodal"
                                            + " input. If analysis fails, switch to a video-capable"
                                            + " model.")
                                .build());
            } else {
                output.add(TextBlock.builder().text("Loaded video: " + p).build());
            }
            return ToolResultBlock.of(output);
        } catch (Exception e) {
            return ToolResultBlock.error("Error: " + e.getMessage());
        }
    }

    @Tool(name = BuiltInToolNames.SEND_FILE_TO_USER, description = "Send files to user")
    public ToolResultBlock sendFileToUser(
            @ToolParam(name = "path", description = "File path") String path) {
        if (off(BuiltInToolNames.SEND_FILE_TO_USER)) {
            return ToolResultBlock.text("Tool disabled.");
        }
        try {
            Path p = resolveInScope(path);
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                return ToolResultBlock.error("File not found: " + p);
            }
            if (isImageFile(p)) {
                Base64Source source = toBase64Source(p, "image/png", TOOL_MEDIA_MAX_BYTES);
                if (source == null) {
                    return ToolResultBlock.of(
                            TextBlock.builder()
                                    .text(
                                            "File is too large to inline for multimodal transfer: "
                                                    + p
                                                    + ".")
                                    .build());
                }
                return ToolResultBlock.of(
                        List.of(
                                ImageBlock.builder().source(source).build(),
                                TextBlock.builder().text("File sent successfully.").build()));
            }
            if (isVideoFile(p)) {
                Base64Source source = toBase64Source(p, "video/mp4", TOOL_MEDIA_MAX_BYTES);
                if (source == null) {
                    return ToolResultBlock.of(
                            TextBlock.builder()
                                    .text(
                                            "File is too large to inline for multimodal transfer: "
                                                    + p
                                                    + ".")
                                    .build());
                }
                return ToolResultBlock.of(
                        List.of(
                                VideoBlock.builder().source(source).build(),
                                TextBlock.builder().text("File sent successfully.").build()));
            }
            return ToolResultBlock.of(
                    TextBlock.builder().text("File ready for user: " + p.toUri()).build());
        } catch (Exception e) {
            return ToolResultBlock.error("Error: " + e.getMessage());
        }
    }

    @Tool(name = BuiltInToolNames.GET_CURRENT_TIME, description = "Get current date and time")
    public String getCurrentTime(
            @ToolParam(
                            name = "timezone",
                            description = "Timezone ID, e.g. America/New_York",
                            required = false)
                    String timezone) {
        if (off(BuiltInToolNames.GET_CURRENT_TIME)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        try {
            ZoneId zone =
                    timezone == null || timezone.isBlank() ? context.userZone : ZoneId.of(timezone);
            LOGGER.log(Level.FINE, "Querying current time, timezone: {0}", zone);
            return LocalDateTime.now(zone)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " "
                    + zone;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name = BuiltInToolNames.SET_USER_TIMEZONE, description = "Set user timezone")
    public String setUserTimezone(
            @ToolParam(name = "timezone", description = "Timezone ID") String timezone) {
        if (off(BuiltInToolNames.SET_USER_TIMEZONE)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        try {
            context.userZone = ZoneId.of(timezone);
            return "Timezone set to " + context.userZone;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name = BuiltInToolNames.GET_TOKEN_USAGE, description = "Get llm token usage")
    public String getTokenUsage(
            @ToolParam(name = "from_date", description = "From date yyyy-MM-dd", required = false)
                    String fromDate,
            @ToolParam(name = "to_date", description = "To date yyyy-MM-dd", required = false)
                    String toDate) {
        if (off(BuiltInToolNames.GET_TOKEN_USAGE)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        List<TokenUsageRecord> records = context.repository.loadTokenUsage();
        long prompt = records.stream().mapToLong(r -> r.promptTokens()).sum();
        long completion = records.stream().mapToLong(r -> r.completionTokens()).sum();
        return "Token usage summary: prompt="
                + prompt
                + ", completion="
                + completion
                + ", total="
                + (prompt + completion);
    }

    private Path resolveInScope(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }
        Path workspace = context.currentWorkspace().toAbsolutePath().normalize();
        Path p = Paths.get(input);
        Path resolved = p.isAbsolute() ? p.normalize() : workspace.resolve(input).normalize();

        boolean inScope = false;

        // 1. Check workspace
        if (resolved.startsWith(workspace)) {
            inScope = true;
        }

        // 2. Check project scope
        if (!inScope && context.currentProject() != null) {
            ai.emailclaw.emailclaw.model.ProjectInfo proj = context.currentProject();
            if (proj.getBaseDirectory() != null && !proj.getBaseDirectory().isBlank()) {
                Path base = Path.of(proj.getBaseDirectory()).toAbsolutePath().normalize();
                if (resolved.startsWith(base)) {
                    inScope = true;
                }
            }
            if (!inScope && proj.getAdditionalDirs() != null) {
                for (String dir : proj.getAdditionalDirs().keySet()) {
                    if (dir != null && !dir.isBlank()) {
                        Path extra = Path.of(dir).toAbsolutePath().normalize();
                        if (resolved.startsWith(extra)) {
                            inScope = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!inScope) {
            throw new SecurityException(
                    "Path is outside allowed scope (workspace or project): " + input);
        }
        if (isSensitivePath(resolved)) {
            throw new SecurityException("Sensitive file access is blocked: " + input);
        }
        return resolved;
    }

    private static boolean isSensitivePath(Path path) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String filename =
                path.getFileName() == null
                        ? ""
                        : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return filename.equals(".env")
                || filename.endsWith(".pem")
                || filename.endsWith(".key")
                || filename.equals("id_rsa")
                || filename.equals("id_dsa")
                || filename.equals("id_ecdsa")
                || filename.equals("id_ed25519")
                || normalized.contains("/.ssh/")
                || normalized.contains("/.aws/")
                || normalized.contains("/.config/gcloud/");
    }

    private boolean isImageFile(Path path) {
        return IMAGE_EXTS.contains(extension(path));
    }

    private boolean isVideoFile(Path path) {
        return VIDEO_EXTS.contains(extension(path));
    }

    private String extension(Path path) {
        if (path == null || path.getFileName() == null) return "";
        String name = path.getFileName().toString().toLowerCase();
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return "";
        return name.substring(idx);
    }

    private Base64Source toBase64Source(Path path, String fallbackMediaType, long maxBytes) {
        try {
            long size = Files.size(path);
            if (size <= 0 || size > maxBytes) return null;
            byte[] bytes = Files.readAllBytes(path);
            String mediaType = Files.probeContentType(path);
            if (mediaType == null || mediaType.isBlank()) {
                mediaType = fallbackMediaType;
            }
            return Base64Source.builder()
                    .mediaType(mediaType)
                    .data(Base64.getEncoder().encodeToString(bytes))
                    .build();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to convert media file to Base64: " + path, e);
            return null;
        }
    }
}
