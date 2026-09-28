package edu.whut.clf.common.enums;

/** 文件用途，决定可见性与鉴权范围。 */
public enum FilePurpose {
    PUBLIC_POST(true),
    PRIVATE_CLAIM(false),
    PRIVATE_DISPUTE(false),
    PRIVATE_LEAD(false);

    private final boolean publicVisible;

    FilePurpose(boolean publicVisible) {
        this.publicVisible = publicVisible;
    }

    public boolean isPublicVisible() {
        return publicVisible;
    }
}
