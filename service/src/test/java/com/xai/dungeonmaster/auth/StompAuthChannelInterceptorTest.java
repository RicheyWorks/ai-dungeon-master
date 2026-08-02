package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** CONNECT with a valid Bearer JWT binds the session id into WS session attrs. */
class StompAuthChannelInterceptorTest {

    private JwtService jwt;
    private SessionService sessions;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwt = new JwtService("stomp-test-secret-abcdefghijklmn", 3600);
        sessions = new SessionService(jwt);
        interceptor = new StompAuthChannelInterceptor(jwt, sessions);
    }

    @Test
    void connectWithBearerBindsSessionId() {
        SessionService.Issued issued = sessions.createSession("Kael");
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + issued.token());
        Map<String, Object> attrs = new HashMap<>();
        accessor.setSessionAttributes(attrs);
        accessor.setLeaveMutable(true);

        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(msg, null);

        assertEquals(issued.session().id(), attrs.get(StompAuthChannelInterceptor.SESSION_ID_ATTR));
    }

    @Test
    void connectWithoutTokenLeavesAttrsEmpty() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Map<String, Object> attrs = new HashMap<>();
        accessor.setSessionAttributes(attrs);
        accessor.setLeaveMutable(true);

        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(msg, null);

        assertNull(attrs.get(StompAuthChannelInterceptor.SESSION_ID_ATTR));
    }

    @Test
    void connectWithXAuthTokenHeaderWorks() {
        SessionService.Issued issued = sessions.createSession("Lira");
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("X-Auth-Token", issued.token());
        Map<String, Object> attrs = new HashMap<>();
        accessor.setSessionAttributes(attrs);
        accessor.setLeaveMutable(true);

        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(msg, null);

        assertEquals(issued.session().id(), attrs.get(StompAuthChannelInterceptor.SESSION_ID_ATTR));
    }

    @Test
    void sessionIdOfReadsAttribute() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(StompAuthChannelInterceptor.SESSION_ID_ATTR, "abc");
        accessor.setSessionAttributes(attrs);
        assertEquals("abc", StompAuthChannelInterceptor.sessionIdOf(accessor));
        assertNull(StompAuthChannelInterceptor.sessionIdOf(null));
    }

    @Test
    void authRequiredRejectsConnectWithoutToken() {
        StompAuthChannelInterceptor strict = new StompAuthChannelInterceptor(jwt, sessions, true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(org.springframework.messaging.MessageDeliveryException.class,
                () -> strict.preSend(msg, null));
    }

    @Test
    void subscribeOwnTopicAllowed() {
        SessionService.Issued issued = sessions.createSession("A");
        StompAuthChannelInterceptor strict = new StompAuthChannelInterceptor(jwt, sessions, true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(StompAuthChannelInterceptor.SESSION_ID_ATTR, issued.session().id());
        accessor.setSessionAttributes(attrs);
        accessor.setDestination("/topic/narrative/" + issued.session().id());
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertDoesNotThrow(() -> strict.preSend(msg, null));
    }

    @Test
    void subscribeOtherSessionDenied() {
        SessionService.Issued a = sessions.createSession("A");
        SessionService.Issued b = sessions.createSession("B");
        StompAuthChannelInterceptor strict = new StompAuthChannelInterceptor(jwt, sessions, true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(StompAuthChannelInterceptor.SESSION_ID_ATTR, a.session().id());
        accessor.setSessionAttributes(attrs);
        accessor.setDestination("/topic/narrative/" + b.session().id());
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(org.springframework.messaging.MessageDeliveryException.class,
                () -> strict.preSend(msg, null));
    }

    @Test
    void authRequiredRejectsSharedNarrativeTopic() {
        SessionService.Issued issued = sessions.createSession("A");
        StompAuthChannelInterceptor strict = new StompAuthChannelInterceptor(jwt, sessions, true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(StompAuthChannelInterceptor.SESSION_ID_ATTR, issued.session().id());
        accessor.setSessionAttributes(attrs);
        accessor.setDestination("/topic/narrative");
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(org.springframework.messaging.MessageDeliveryException.class,
                () -> strict.preSend(msg, null));
    }

    @Test
    void sendUnknownDestinationDeniedWhenAuthOn() {
        SessionService.Issued issued = sessions.createSession("A");
        StompAuthChannelInterceptor strict = new StompAuthChannelInterceptor(jwt, sessions, true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(StompAuthChannelInterceptor.SESSION_ID_ATTR, issued.session().id());
        accessor.setSessionAttributes(attrs);
        accessor.setDestination("/app/hack");
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(org.springframework.messaging.MessageDeliveryException.class,
                () -> strict.preSend(msg, null));
    }

}
