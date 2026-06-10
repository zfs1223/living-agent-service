package com.livingagent.core.model.pool;

public enum Protocol {
    OPENAI_COMPATIBLE("openai_compatible"),
    ANTHROPIC("anthropic"),
    GEMINI("gemini"),
    OPENAI_RESPONSES("openai_responses");

    private final String id;

    Protocol(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
