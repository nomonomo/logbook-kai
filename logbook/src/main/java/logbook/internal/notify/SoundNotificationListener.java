package logbook.internal.notify;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import logbook.bean.AppConfig;
import logbook.bean.AppConfig.NotifyKind;
import logbook.internal.Audios;
import logbook.notify.BadlyDamagedAlert;
import logbook.notify.CondRecovered;
import logbook.notify.GimmickAchieved;
import logbook.notify.MissionConditionAlert;
import logbook.notify.MissionReturning;
import logbook.notify.MissionStarted;
import logbook.notify.NdockCompleted;
import logbook.notify.Notification;
import logbook.notify.NotificationListener;
import logbook.notify.PortSupplyCompleted;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知音を再生する。遠征開始は捨てる。
 */
@Slf4j
public final class SoundNotificationListener implements NotificationListener {

    /** フォルダ通知で共有し、再生中は次を鳴らさない */
    private AudioClip clip;

    @Override
    public void onNotify(Notification event) {
        Platform.runLater(() -> play(event));
    }

    @Override
    public Optional<Duration> missionRemindInterval() {
        AppConfig config = AppConfig.get();
        if (!config.notifiesBySound(NotifyKind.MISSION) || !Boolean.TRUE.equals(config.getUseSoundRemind())) {
            return Optional.empty();
        }
        if (config.getRemind() < 1) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(config.getRemind()));
    }

    private void play(Notification event) {
        if (!audible(event)) {
            return;
        }
        switch (event) {
            case MissionReturning _ -> playFolder(AppConfig.get().soundDir(NotifyKind.MISSION));
            case NdockCompleted _ -> playFolder(AppConfig.get().soundDir(NotifyKind.NDOCK));
            case MissionConditionAlert _ -> playFolder(AppConfig.get().soundDir(NotifyKind.MISSION_CONDITION));
            case PortSupplyCompleted _ -> playFolder(AppConfig.get().soundDir(NotifyKind.PORT_SUPPLY));
            case CondRecovered _ -> playFolder(AppConfig.get().soundDir(NotifyKind.COND));
            case GimmickAchieved _ -> playFolder(AppConfig.get().soundDir(NotifyKind.GIMMICK));
            case BadlyDamagedAlert e -> playAlert(e);
            case MissionStarted _ -> {
            }
        }
    }

    /**
     * この回を鳴らすかを返します。
     *
     * @param event 通知
     * @return 鳴らすなら true
     */
    static boolean audible(Notification event) {
        return NotifyChannels.sound(event);
    }

    private void playFolder(String dir) {
        if (this.clip != null && this.clip.isPlaying()) {
            return;
        }
        try {
            Path path = Audios.randomAudioFile(Paths.get(dir));
            if (path != null) {
                this.clip = new AudioClip(path.toUri().toString());
                this.clip.setVolume(AppConfig.get().getSoundLevel() / 100D);
                this.clip.play();
            } else {
                Audios.beep();
            }
        } catch (Exception e) {
            log.warn("サウンド通知に失敗しました", e);
        }
    }

    /**
     * 警告音フォルダを 1 回鳴らす。対象艦が残っていないときは鳴らさない。
     * フォルダにもデフォルトサウンドにもファイルが無いときは OS のビープ音を鳴らす。
     *
     * @param event 大破通知
     */
    private static void playAlert(BadlyDamagedAlert event) {
        if (BadlyDamageNotices.ships(event).isEmpty()) {
            return;
        }
        try {
            NotifyKind kind = event.phase() == BadlyDamagedAlert.Phase.SORTIE
                    ? NotifyKind.BADLY_SORTIE
                    : NotifyKind.BADLY_ADVANCE;
            Path path = Audios.randomAudioFile(Paths.get(AppConfig.get().soundDir(kind)));
            if (path != null) {
                AudioClip alert = new AudioClip(path.toUri().toString());
                alert.setVolume(AppConfig.get().getSoundLevel() / 100D);
                alert.play();
            } else {
                Audios.beep();
            }
        } catch (Exception e) {
            log.warn("サウンド通知に失敗しました", e);
        }
    }
}
