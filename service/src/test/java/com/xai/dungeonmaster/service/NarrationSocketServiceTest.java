package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.dto.Envelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies NarrationSocketService streams typed envelopes to /topic/narrative:
 * one or more narrative_chunk envelopes followed by a final narrative_update.
 * Uses a mocked SimpMessagingTemplate — no broker, no Spring context.
 */
class NarrationSocketServiceTest {

    @Test
    void streamsChunkEnvelopesThenFinalUpdate() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 3, new String[] { "Kael" }, new String[] { "Warrior" });
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        NarrationSocketService svc = new NarrationSocketService(engine, messaging);

        svc.streamNarration("look around", "req-1");

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messaging, atLeast(2)).convertAndSend(eq("/topic/narrative"), sent.capture());

        List<Envelope<?>> envelopes = new java.util.ArrayList<>();
        for (Object o : sent.getAllValues()) {
            envelopes.add((Envelope<?>) o);
        }

        assertTrue(envelopes.stream().anyMatch(e -> "narrative_chunk".equals(e.type())),
                "at least one narrative_chunk envelope");
        Envelope<?> last = envelopes.get(envelopes.size() - 1);
        assertEquals("narrative_update", last.type(), "final message is the complete update");
        assertEquals(1, last.version());
        assertEquals("req-1", envelopes.get(0).requestId(), "requestId echoed on every envelope");
    }
}
