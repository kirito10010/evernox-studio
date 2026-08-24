package com.evernox.util;

/**
 * 图片类型校验
 *
 * 上传的是原始文件，服务端能看到真实字节，因此按文件魔数校验实际类型，
 * 避免仅凭前端传来的 mimeType 或扩展名判断被伪造。
 */
public final class ImageTypeValidator {

    private ImageTypeValidator() {
    }

    /**
     * 判断字节流是否为支持的图片格式 (jpeg / png / gif / webp / bmp)
     */
    public static boolean isSupportedImage(byte[] data) {
        return detectMimeType(data) != null;
    }

    /**
     * 根据魔数识别 MIME 类型，无法识别返回 null
     */
    public static String detectMimeType(byte[] d) {
        if (d == null || d.length < 12) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && (d[4] & 0xFF) == 0x0D && (d[5] & 0xFF) == 0x0A
                && (d[6] & 0xFF) == 0x1A && (d[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        // GIF: "GIF87a" / "GIF89a"
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') {
            return "image/gif";
        }
        // WEBP: "RIFF" .... "WEBP"
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        // BMP: "BM"
        if (d[0] == 'B' && d[1] == 'M') {
            return "image/bmp";
        }
        return null;
    }
}
