package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.StompAuthChannelInterceptor;
import com.xai.dungeonmaster.dto.ActionRequest;
import com.xai.dungeonmaster.service.GameEngineFactory;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WS actions for two different session ids must hit different engines and
 * publish acks to session-scoped topics.
 */
class GameWebSocketControllerTest {

    private GameInstanceService games;
    private SimpMessagingTemplate messaging;
    private GameWebSocketController controller;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        GameEngineFactory factory = new GameEngineFactory(
                3, 3, new String[]{"Kael"}, new String[]{"Warrior"},
                "", "local-stub", 4000, null);
        games = new GameInstanceService(factory, factory.createDefault(), tmp);
        messaging = mock(SimpMessagingTemplate.class);
        controller = new GameWebSocketController(games, messaging);
    }

    @Test
    void unauthenticatedActionUsesGlobalTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionAttributes(new HashMap<>());

        controller.handleAction(new ActionRequest("not-a-real-choice"), accessor);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(messaging, atLeastOnce()).convertAndSend(topic.capture(), anyString());
        assertEquals("/topic/narrative", topic.getValue());
        assertEquals(0, games.sessionCount());
    }

    @Test
    void authenticatedActionUsesSessionTopicAndEngine() {
        String sid = "player-alice";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(StompAuthChannelInterceptor.SESSION_ID_ATTR, sid);
        accessor.setSessionAttributes(attrs);

        controller.handleAction(new ActionRequest("zzzz-unknown"), accessor);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(messaging, atLeastOnce()).convertAndSend(topic.capture(), anyString());
        assertEquals("/topic/narrative/" + sid, topic.getValue());
        assertEquals(1, games.sessionCount());
        assertTrue(games.peek(sid).isPresent());
    }

    @Test
    void twoSessionsStayIsolated() {
        for (String sid : new String[]{"a", "b"}) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(StompAuthChannelInterceptor.SESSION_ID_ATTR, sid);
            accessor.setSessionAttributes(attrs);
            controller.handleAction(new ActionRequest("nope"), accessor);
        }
        assertEquals(2, games.sessionCount());
        assertNotSame(games.forSession("a"), games.forSession("b"));
    }
}
