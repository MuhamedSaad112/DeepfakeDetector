package com.deepfakedetector.model.enums;

public enum MediaFileType {
    // Video Formats
    VIDEO_MP4("video/mp4"),
    VIDEO_AVI("video/x-msvideo"),
    VIDEO_WMV("video/x-ms-wmv"),
    VIDEO_MOV("video/quicktime"),
    VIDEO_MKV("video/x-matroska"),
    VIDEO_FLV("video/x-flv"),
    VIDEO_WEBM("video/webm"),
    VIDEO_3GP("video/3gpp"),
    VIDEO_OGG("video/ogg"),
    VIDEO_MPEG("video/mpeg"),

    // Image Formats
    IMAGE_JPEG("image/jpeg"),
    IMAGE_JPG("image/jpeg"),
    IMAGE_PNG("image/png"),
    IMAGE_GIF("image/gif"),
    IMAGE_BMP("image/bmp"),
    IMAGE_TIFF("image/tiff"),
    IMAGE_WEBP("image/webp"),
    IMAGE_SVG("image/svg+xml"),
    IMAGE_HEIC("image/heic"),
    IMAGE_HEIF("image/heif");

    private final String mimeType;

    MediaFileType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }

    // Helper method to find MediaType by mimeType
    public static MediaFileType fromMimeType(String mimeType) {
        for (MediaFileType type : values()) {
            if (type.getMimeType().equalsIgnoreCase(mimeType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported media type: " + mimeType);
    }
}
