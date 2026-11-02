package logbook.notify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationsTest {

    @Test
    void nullEventDoesNotCallListeners() {
        AtomicInteger calls = new AtomicInteger();
        Notifications.dispatch(null, List.of(event -> calls.incrementAndGet()), Runnable::run);
        assertEquals(0, calls.get());
    }

    @Test
    void oneListenerFailureDoesNotStopTheNext() {
        List<Notification> seen = new ArrayList<>();
        Notification event = new CondRecovered(1, "第一艦隊");
        List<NotificationListener> listeners = List.of(
                ignored -> {
                    throw new IllegalStateException("失敗");
                },
                seen::add);
        Notifications.dispatch(event, listeners, Runnable::run);
        assertEquals(List.of(event), seen);
    }

    @Test
    void eachListenerReceivesTheEvent() {
        List<String> names = new ArrayList<>();
        Notification event = new PortSupplyCompleted(2, "第二艦隊");
        Notifications.dispatch(event, List.of(
                ignored -> names.add("a"),
                ignored -> names.add("b")), Runnable::run);
        assertEquals(List.of("a", "b"), names);
        assertTrue(event instanceof PortSupplyCompleted);
    }
}
