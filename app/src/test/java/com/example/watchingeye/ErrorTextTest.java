package com.example.watchingeye;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ErrorTextTest {

    @Test
    public void explainsTheCommonPluginRefusals() {
        assertTrue(MainViewModel.describeFailure("Extend", 400).contains("1-43200"));
        assertTrue(MainViewModel.describeFailure("Extend", 403).contains("Enable Public API"));
        assertTrue(MainViewModel.describeFailure("Extend", 403).contains("lock"));
        assertTrue(MainViewModel.describeFailure("Status", 401).contains("Emby API key"));
        assertTrue(MainViewModel.describeFailure("Status", 404).contains("plugin installed"));
        assertTrue(MainViewModel.describeFailure("Status", 500).contains("HTTP 500"));
    }
}
