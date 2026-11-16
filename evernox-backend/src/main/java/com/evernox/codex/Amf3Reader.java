package com.evernox.codex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * 火影忍者OL 客户端配置解码器（腾讯 Flash 变体 AMF3）。
 *
 * <p>格式要点：
 * <ul>
 *   <li>文件头 3 字节 {@code 0A 0B 01}，之后是 {@code [字符串主键][0x0A][traits 值][字段值...]} 的连续记录；</li>
 *   <li>字符串表、对象表、traits 表相互独立（本实现只用到字符串表，因为样本中未出现对象/traits 引用）；</li>
 *   <li>整数为 29 位有符号（最高位为符号位），double 为大端。</li>
 * </ul>
 *
 * <p>解析结果是「配置主键 → 字段 Map」，字段顺序保留，便于后续按名字取值。
 */
public final class Amf3Reader {

    /** 一条配置记录：主键 + 字段。 */
    public record Entry(String key, Map<String, Object> fields) {
    }

    private final byte[] buf;
    private int pos;
    private final List<String> stringTable = new ArrayList<>();

    private Amf3Reader(byte[] buf) {
        this.buf = buf;
    }

    /**
     * 解析一个 .cfg 字节流（自动识别 zlib / gzip 压缩）。
     */
    public static List<Entry> parse(byte[] data) throws IOException {
        byte[] raw = inflateIfNeeded(data);
        if (raw.length < 4 || (raw[0] & 0xFF) != 0x0A || (raw[1] & 0xFF) != 0x0B || (raw[2] & 0xFF) != 0x01) {
            throw new IOException("不是预期的配置文件格式");
        }
        Amf3Reader reader = new Amf3Reader(raw);
        reader.pos = 3;
        List<Entry> entries = new ArrayList<>();
        List<String> lastFields = null;
        int length = raw.length;
        while (reader.pos < length) {
            // 末尾可能只剩 1 字节终止符 0x01
            if (length - reader.pos < 3) {
                break;
            }
            String key = reader.readRawString();
            if (reader.pos >= length || (raw[reader.pos] & 0xFF) != 0x0A) {
                throw new IOException("记录 " + key + " 的对象标记缺失");
            }
            reader.pos++;
            long tv = reader.u29();
            List<String> fields;
            if (tv == 0x05) {
                if (lastFields == null) {
                    throw new IOException("记录 " + key + " 引用了不存在的 traits");
                }
                fields = lastFields;
            } else {
                int count = (int) (tv >> 4);
                if ((tv & 0x08) != 0) {
                    throw new IOException("不支持 traits 引用（记录 " + key + "）");
                }
                reader.readRawString(); // 类名，样本中始终为空
                fields = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    fields.add(reader.readRawString());
                }
                lastFields = fields;
            }
            Map<String, Object> obj = new LinkedHashMap<>();
            for (String field : fields) {
                obj.put(field, reader.readValue(0));
            }
            entries.add(new Entry(key, obj));
        }
        return entries;
    }

    static byte[] inflateIfNeeded(byte[] data) throws IOException {
        if (data.length > 1 && (data[0] & 0xFF) == 0x78) {
            try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data), new Inflater())) {
                return in.readAllBytes();
            }
        }
        if (data.length > 1 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
                return in.readAllBytes();
            }
        }
        return data;
    }

    private long u29() throws IOException {
        long value = 0;
        for (int i = 0; i < 3; i++) {
            int x = next();
            value = (value << 7) | (x & 0x7F);
            if ((x & 0x80) == 0) {
                return value;
            }
        }
        int x = next();
        return ((value << 8) | x) & 0xFFFFFFFFL;
    }

    private int next() throws IOException {
        if (pos >= buf.length) {
            throw new IOException("读取越界");
        }
        return buf[pos++] & 0xFF;
    }

    /** 主键 / 字段名：U29 只取长度位，读到的字符串进入字符串表。 */
    private String readRawString() throws IOException {
        long v = u29();
        int len = (int) (v >> 1);
        if (len < 0 || pos + len > buf.length) {
            throw new IOException("字符串长度越界");
        }
        String s = new String(buf, pos, len, StandardCharsets.UTF_8);
        pos += len;
        stringTable.add(s);
        return s;
    }

    private Object readValue(int depth) throws IOException {
        if (depth > 64) {
            throw new IOException("嵌套过深");
        }
        int marker = next();
        switch (marker) {
            case 0x00:
            case 0x01:
                return null;
            case 0x02:
                return Boolean.FALSE;
            case 0x03:
                return Boolean.TRUE;
            case 0x04: {
                long v = u29();
                if (v > 0x0FFFFFFFL) {
                    v -= 0x20000000L;
                }
                return (int) v;
            }
            case 0x05: {
                long bits = 0;
                for (int i = 0; i < 8; i++) {
                    bits = (bits << 8) | next();
                }
                return Double.longBitsToDouble(bits);
            }
            case 0x06: {
                long v = u29();
                if ((v & 1) == 0) {
                    int idx = (int) (v >> 1);
                    return idx >= 0 && idx < stringTable.size() ? stringTable.get(idx) : "";
                }
                int len = (int) (v >> 1);
                if (len < 0 || pos + len > buf.length) {
                    throw new IOException("字符串值长度越界");
                }
                String s = new String(buf, pos, len, StandardCharsets.UTF_8);
                pos += len;
                stringTable.add(s);
                return s;
            }
            case 0x09: {
                long v = u29();
                if ((v & 1) == 0) {
                    // 数组引用：样本中没有，返回空数组避免中断
                    return new ArrayList<>();
                }
                int count = (int) (v >> 1);
                // 关联段：以空字符串（0x01）结束
                while (pos < buf.length) {
                    int peek = buf[pos] & 0xFF;
                    if (peek == 0x01) {
                        pos++;
                        break;
                    }
                    if (peek == 0x06) {
                        readValue(depth + 1);
                        readValue(depth + 1);
                        continue;
                    }
                    break;
                }
                List<Object> list = new ArrayList<>(Math.max(0, count));
                for (int i = 0; i < count; i++) {
                    list.add(readValue(depth + 1));
                }
                return list;
            }
            case 0x0A: {
                long tv = u29();
                if (tv == 0x05) {
                    throw new IOException("嵌套对象 traits 引用未支持");
                }
                int count = (int) (tv >> 4);
                if ((tv & 0x08) != 0) {
                    throw new IOException("嵌套对象 traits 引用未支持");
                }
                readRawString(); // 类名
                List<String> fields = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    fields.add(readRawString());
                }
                Map<String, Object> obj = new LinkedHashMap<>();
                for (String field : fields) {
                    obj.put(field, readValue(depth + 1));
                }
                return obj;
            }
            case 0x07:
            case 0x0B: {
                long v = u29();
                int len = (int) (v >> 1);
                String s = len > 0 && pos + len <= buf.length
                        ? new String(buf, pos, len, StandardCharsets.UTF_8) : "";
                pos = Math.min(buf.length, pos + Math.max(0, len));
                return s;
            }
            case 0x08: {
                u29();
                long bits = 0;
                for (int i = 0; i < 8; i++) {
                    bits = (bits << 8) | next();
                }
                return Double.longBitsToDouble(bits);
            }
            case 0x0C: {
                long v = u29();
                int len = (int) (v >> 1);
                if (len < 0 || pos + len > buf.length) {
                    throw new IOException("字节数组长度越界");
                }
                byte[] bytes = new byte[len];
                System.arraycopy(buf, pos, bytes, 0, len);
                pos += len;
                return bytes;
            }
            default:
                throw new IOException("未知的 AMF3 标记: 0x" + Integer.toHexString(marker));
        }
    }
}
