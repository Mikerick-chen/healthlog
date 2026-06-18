package com.healthlog.security;

/**
 * 目前請求的使用者 ID（以 ThreadLocal 保存，由 {@link UserContextFilter} 在每個請求開頭設定）。
 * 服務層以此做資料隔離（§9 多使用者）。
 */
public final class CurrentUser {

    /** 示範使用者 ID（種子資料歸屬；未帶 X-User-Id 時的預設） */
    public static final Long DEMO_USER_ID = 1L;

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private CurrentUser() {}

    public static void set(Long userId) { HOLDER.set(userId); }

    /** 取得目前使用者 ID；未設定時回退到示範使用者，確保 API 永遠有歸屬 */
    public static Long id() {
        Long id = HOLDER.get();
        return id != null ? id : DEMO_USER_ID;
    }

    public static void clear() { HOLDER.remove(); }
}
