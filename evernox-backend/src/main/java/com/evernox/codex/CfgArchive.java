package com.evernox.codex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端 {@code config/archives/itemN.pkg} 归档读取。
 *
 * <p>解压后是极简归档：{@code [2B 文件名长度][文件名 UTF-8][4B 数据长度][数据]} 连续排列。
 */
public final class CfgArchive {

    private CfgArchive() {
    }

    public static Map<String, byte[]> unpack(byte[] data) throws IOException {
        byte[] raw = Amf3Reader.inflateIfNeeded(data);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        int offset = 0;
        int length = raw.length;
        while (offset + 6 <= length) {
            int nameLen = ((raw[offset] & 0xFF) << 8) | (raw[offset + 1] & 0xFF);
            if (nameLen <= 0 || nameLen > 300) {
                break;
            }
            int nameStart = offset + 2;
            if (nameStart + nameLen + 4 > length) {
                break;
            }
            String name = new String(raw, nameStart, nameLen, StandardCharsets.UTF_8);
            int sizePos = nameStart + nameLen;
            long size = ((long) (raw[sizePos] & 0xFF) << 24)
                    | ((long) (raw[sizePos + 1] & 0xFF) << 16)
                    | ((long) (raw[sizePos + 2] & 0xFF) << 8)
                    | (raw[sizePos + 3] & 0xFF);
            int dataStart = sizePos + 4;
            if (size < 0 || dataStart + size > length) {
                break;
            }
            byte[] content = new byte[(int) size];
            System.arraycopy(raw, dataStart, content, 0, (int) size);
            entries.put(name, content);
            offset = dataStart + (int) size;
        }
        return entries;
    }

    /**
     * 按文件名后缀查找归档条目（pkg 内路径形如 {@code config/user/NinjaInfoCFG.cfg}）。
     */
    public static byte[] find(Map<String, byte[]> entries, String fileName) {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            if (name.equals(fileName) || name.endsWith("/" + fileName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
