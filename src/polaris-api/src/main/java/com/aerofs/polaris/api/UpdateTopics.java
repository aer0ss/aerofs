package com.aerofs.polaris.api;

public abstract class UpdateTopics {

    public static final String UPDATE_TOPIC_PREFIX = "sf/";

    public static String getUpdateTopic(String root) {
        return UPDATE_TOPIC_PREFIX + root;
    }

    private UpdateTopics() {
        // to prevent instantiation by subclasses
    }
}
