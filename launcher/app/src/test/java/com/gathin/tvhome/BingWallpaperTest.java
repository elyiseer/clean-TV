package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class BingWallpaperTest {

    @Test
    public void parseImageUrlsBuildsAbsoluteBingUrls() {
        String json = "{\"images\":["
                + "{\"url\":\"/th?id=OHR.One_EN-US123.jpg&rf=LaDigue_1920x1080.jpg&pid=hp\"},"
                + "{\"url\":\"https://www.bing.com/th?id=OHR.Two\"},"
                + "{\"url\":\"\"}"
                + "]}";
        List<String> urls = BingWallpaper.parseImageUrls(json);
        assertEquals(2, urls.size());
        assertEquals(
                "https://www.bing.com/th?id=OHR.One_EN-US123.jpg&rf=LaDigue_1920x1080.jpg&pid=hp",
                urls.get(0));
        assertEquals("https://www.bing.com/th?id=OHR.Two", urls.get(1));
    }

    @Test
    public void parseImageUrlsCapsAtSixAndIgnoresJunk() {
        StringBuilder sb = new StringBuilder("{\"images\":[");
        for (int i = 0; i < 12; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"url\":\"/th?id=").append(i).append("\"}");
        }
        sb.append("]}");
        assertEquals(6, BingWallpaper.parseImageUrls(sb.toString()).size());
        assertTrue(BingWallpaper.parseImageUrls("{}").isEmpty());
        assertTrue(BingWallpaper.parseImageUrls(null).isEmpty());
        assertTrue(BingWallpaper.parseImageUrls("not-json").isEmpty());
    }

    @Test
    public void choiceConstantMatchesSettingsWiring() {
        assertEquals("bing", BingWallpaper.CHOICE);
        assertTrue(Config.isCachedWallpaperChoice(BingWallpaper.CHOICE));
    }
}
