package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class TvButtonChoiceTest {
    @Test public void followsConfigAndServiceDefaults() {
        assertEquals("app:configured",TvButtonChoice.resolve("follow","configured",true,"live"));
        assertEquals("inputs",TvButtonChoice.resolve("follow",null,true,"live"));
        assertNull(TvButtonChoice.resolve("follow",null,false,"live"));
    }
    @Test public void overrideIsIndependentOfConfiguredLiveTv() {
        assertEquals("app:example",TvButtonChoice.resolve("app:example","configured",true,"tuner"));
        assertEquals("app:tuner",TvButtonChoice.resolve("live","configured",true,"tuner"));
        assertEquals("inputs",TvButtonChoice.resolve("inputs","configured",true,"tuner"));
    }
}
