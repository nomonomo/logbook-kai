package logbook.internal.notify;

import java.time.Duration;

/**
 * 遠征・入渠の完了通知を出すかの判定。残り 1 分以内で、未通知か再通知間隔を過ぎていれば true。
 */
public final class CompletionDue {

    /** この残り時間以内で通知する */
    public static final Duration THRESHOLD = Duration.ofMinutes(1);

    private CompletionDue() {
    }

    /**
     * @param remaining 完了までの残り時間
     * @param lastNotifiedAt 前回通知した時刻（epoch milli）。未通知なら 0
     * @param now 現在時刻（epoch milli）
     * @param remind 再通知するなら true
     * @param remindInterval 再通知の間隔
     * @return 通知するなら true
     */
    public static boolean due(Duration remaining, long lastNotifiedAt, long now, boolean remind,
            Duration remindInterval) {
        if (remaining.compareTo(THRESHOLD) > 0) {
            return false;
        }
        Duration course = Duration.ofMillis(now - lastNotifiedAt);
        if (course.compareTo(remindInterval) < 0) {
            return false;
        }
        return lastNotifiedAt == 0L || remind;
    }
}
