package com.lcq.aiagent.chat.session;

import org.springframework.ai.session.SessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SessionCleanupTask {

    private final SessionService sessionService;

    public SessionCleanupTask(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(fixedDelayString = "${app.ai.session.cleanup-interval}")
    public void deleteExpiredSessions() {
        sessionService.deleteExpiredSessions(Instant.now());
    }
}
