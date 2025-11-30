package uk.gegc.kidsgptbackend.shared.util;

public class CdnAvatarUtil {
    private static final String CDN_BASE_URL = "https://cdn.example.com/avatars/";

    public static String getAvatarUrl(String avatarId) {
        if (avatarId == null || avatarId.isBlank()) {
            return null;
        }
        return CDN_BASE_URL + avatarId + ".png";
    }
} 