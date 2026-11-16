package com.evernox.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 流式读取浏览器 CDP 抓包（{@code http-*.jsonl}），挑出图鉴所需的配置包、配置表与立绘。
 *
 * <p>记录通过 {@code id} 关联：请求/响应行带 {@code url}，响应体行只带 {@code id + body}，
 * 所以需要先收集 {@code id → url}，再收集响应体。
 */
public final class CaptureReader {

    /** 抓包扫描结果。 */
    public record Bundle(Map<String, byte[]> tables,
                         Map<String, byte[]> portraits,
                         Map<String, byte[]> heads,
                         String build,
                         List<String> warnings) {
    }

    private static final Pattern PKG = Pattern.compile(
            "https?://[^/]+/NarutoBeta(\\d+)\\.(\\d+)Build(\\d+)/config/archives/(item[123])\\.pkg");
    private static final Pattern CFG = Pattern.compile(
            "https?://[^/]+/NarutoBeta(\\d+)\\.(\\d+)Build(\\d+)/config/(?:skill|user|ninja|battle|seasonTask)/([A-Za-z0-9_]+\\.cfg)");
    private static final Pattern IMAGE = Pattern.compile(
            "https?://[^/]+/NarutoBeta(\\d+)\\.(\\d+)Build(\\d+)/assets/user/ninja/(ninjaBigImg|ninjaHead)/(\\d+\\.png)");

    private static final Set<String> WANTED_CFG = Set.of(
            "NinjaSkillCFG.cfg", "SkillKeyDescCFG.cfg", "SkillExStateCFG.cfg", "UpgradeStarAttributeCFG.cfg",
            "SeasonTaskNinjaInfoCFG.cfg");

    private CaptureReader() {
    }

    public static Bundle read(Path dir, ObjectMapper mapper) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.startsWith("http-") && name.endsWith(".jsonl");
                    })
                    .sorted()
                    .toList();
        }
        List<String> warnings = new ArrayList<>();
        if (files.isEmpty()) {
            throw new IOException("文件夹里没有 http-*.jsonl 抓包文件");
        }

        Map<String, Target> targets = new LinkedHashMap<>();
        for (Path file : files) {
            scanUrls(file, mapper, targets, warnings);
        }
        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        for (Path file : files) {
            scanBodies(file, mapper, targets.keySet(), bodies);
        }

        Map<String, byte[]> tables = new LinkedHashMap<>();
        Map<String, byte[]> portraits = new LinkedHashMap<>();
        Map<String, byte[]> heads = new LinkedHashMap<>();
        Map<String, Chosen> chosen = new LinkedHashMap<>();
        for (Map.Entry<String, Target> entry : targets.entrySet()) {
            Target target = entry.getValue();
            StringBuilder sb = bodies.get(entry.getKey());
            if (sb == null || sb.isEmpty()) {
                warnings.add("缺少响应体：" + target.url);
                continue;
            }
            String canonical = target.kind + ":" + target.name;
            Chosen exists = chosen.get(canonical);
            if (exists != null && exists.target.version.compareTo(target.version) >= 0) {
                continue;
            }
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(sb.toString());
            } catch (IllegalArgumentException e) {
                warnings.add("响应体不是合法的 base64：" + target.url);
                continue;
            }
            try {
                decoded = Amf3Reader.inflateIfNeeded(decoded);
            } catch (IOException e) {
                warnings.add("响应体解压失败：" + target.url);
                continue;
            }
            chosen.put(canonical, new Chosen(target, decoded));
        }

        String build = "";
        for (Chosen item : chosen.values()) {
            Target target = item.target;
            if (target.kind == Kind.PKG || target.kind == Kind.CFG) {
                tables.put(target.name, item.data);
            } else if (target.kind == Kind.PORTRAIT) {
                portraits.put(target.name, item.data);
            } else {
                heads.put(target.name, item.data);
            }
            if (build.isEmpty() || target.version.compareTo(versionOf(build)) > 0) {
                build = target.versionString();
            }
        }
        return new Bundle(tables, portraits, heads, build.isEmpty() ? "unknown" : build, warnings);
    }

    private static void scanUrls(Path file, ObjectMapper mapper, Map<String, Target> targets, List<String> warnings)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains("\"url\"") || !line.contains("NarutoBeta")) {
                    continue;
                }
                JsonNode node;
                try {
                    node = mapper.readTree(line);
                } catch (Exception e) {
                    warnings.add("跳过无法解析的抓包行（" + file.getFileName() + "）");
                    continue;
                }
                String id = text(node, "id");
                String url = text(node, "url");
                if (id.isEmpty() || url.isEmpty()) {
                    continue;
                }
                Target target = classify(url);
                if (target == null) {
                    continue;
                }
                Target exists = targets.get(id);
                if (exists == null || target.version.compareTo(exists.version) > 0) {
                    targets.put(id, target);
                }
            }
        }
    }

    private static void scanBodies(Path file, ObjectMapper mapper, Set<String> ids, Map<String, StringBuilder> bodies)
            throws IOException {
        if (ids.isEmpty()) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains("\"body\"") || !line.contains("\"id\"")) {
                    continue;
                }
                JsonNode node;
                try {
                    node = mapper.readTree(line);
                } catch (Exception e) {
                    continue;
                }
                if (!"body".equals(text(node, "type"))) {
                    continue;
                }
                String id = text(node, "id");
                if (!ids.contains(id)) {
                    continue;
                }
                JsonNode body = node.get("body");
                if (body == null || body.isNull()) {
                    continue;
                }
                bodies.computeIfAbsent(id, k -> new StringBuilder()).append(body.asText());
            }
        }
    }

    private static Target classify(String url) {
        Matcher m = PKG.matcher(url);
        if (m.find()) {
            return new Target(Kind.PKG, m.group(4), m.group(), version(m));
        }
        m = CFG.matcher(url);
        if (m.find()) {
            String name = m.group(4);
            if (!WANTED_CFG.contains(name)) {
                return null;
            }
            return new Target(Kind.CFG, name.replace(".cfg", ""), url, version(m));
        }
        m = IMAGE.matcher(url);
        if (m.find()) {
            Kind kind = "ninjaBigImg".equals(m.group(4)) ? Kind.PORTRAIT : Kind.HEAD;
            return new Target(kind, m.group(5), url, version(m));
        }
        return null;
    }

    private static Ver version(Matcher m) {
        return new Ver(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private static Ver versionOf(String text) {
        Matcher m = Pattern.compile("NarutoBeta(\\d+)\\.(\\d+)Build(\\d+)").matcher(text);
        return m.find() ? version(m) : new Ver(0, 0, 0);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private enum Kind {PKG, CFG, PORTRAIT, HEAD}

    private record Target(Kind kind, String name, String url, Ver version) {
        String versionString() {
            return "NarutoBeta" + version.major + "." + version.minor + "Build" + version.build;
        }
    }

    private record Chosen(Target target, byte[] data) {
    }

    private record Ver(int major, int minor, int build) implements Comparable<Ver> {
        @Override
        public int compareTo(Ver o) {
            if (major != o.major) {
                return Integer.compare(major, o.major);
            }
            if (minor != o.minor) {
                return Integer.compare(minor, o.minor);
            }
            return Integer.compare(build, o.build);
        }
    }
}
