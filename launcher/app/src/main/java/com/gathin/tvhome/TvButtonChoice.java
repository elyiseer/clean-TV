package com.gathin.tvhome;

/** Only the physical TV key has an on-TV override. A null target leaves existing handling alone. */
final class TvButtonChoice {
    static String resolve(String choice, String configuredApp, boolean defaultInputs, String liveApp) {
        if ("follow".equals(choice)) return configuredApp != null ? "app:" + configuredApp : defaultInputs ? "inputs" : null;
        if ("live".equals(choice)) return "app:" + liveApp;
        return choice;
    }
}
