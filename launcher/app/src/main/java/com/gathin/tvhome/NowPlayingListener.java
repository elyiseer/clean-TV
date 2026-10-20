package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing with notifications. Android only lists other apps' media sessions to a caller that
 * names an enabled notification listener of its own, so this exists for the Now playing card. It is
 * enabled only when asked for (build-and-install.sh with NOW_PLAYING=1); without that the card never shows.
 */
public class NowPlayingListener extends NotificationListenerService {
}
