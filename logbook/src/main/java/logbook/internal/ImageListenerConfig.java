package logbook.internal;

import java.util.List;
import java.util.Objects;

/**
 * {@link ImageListener} の保存対象設定。
 *
 * @param imgCategories {@code /kcs2/img/{category}/} の category 一覧
 */
record ImageListenerConfig(List<String> imgCategories) {

    ImageListenerConfig {
        imgCategories = List.copyOf(Objects.requireNonNull(imgCategories));
    }

    static ImageListenerConfig empty() {
        return new ImageListenerConfig(List.of());
    }
}
