package com.livingagent.gateway.proactive;

import com.livingagent.core.proactive.alert.AlertNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(ConsoleAlertNotifier.class);

    @Override
    public String getChannelName() {
        return "console";
    }

    @Override
    public boolean send(Alert alert) {
        log.info("[PROACTIVE][{}] {} - {}", alert.level(), alert.title(), alert.content());
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
