package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputRowsTest {
    private static final String EXT = "com.sony.dtv.tvinput.external/.ExternalTvInputService/";

    private static InputRows.Input port(String hw, String label, boolean hdmi) {
        return new InputRows.Input(EXT + hw, null, label, null, hdmi, true, false);
    }

    private static InputRows.Input device(String id, String parentHw, String name) {
        return new InputRows.Input(EXT + id, EXT + parentHw, name, null, true, true, false);
    }

    /** Sony BRAVIA ATV3: HDMI 1-4 and Video, a PS5 and an Apple TV behind HDMI 2, listed out of order. */
    private static List<InputRows.Input> bravia() {
        return Arrays.asList(
                port("HW5", "Video", false),
                device("HDMI200008", "HW2", "Apple TV"),
                port("HW3", "HDMI 3/ARC", true),
                port("HW1", "HDMI 1", true),
                device("HDMI200004", "HW2", "PlayStation 5"),
                port("HW2", "HDMI 2", true),
                port("HW4", "HDMI 4", true));
    }

    private static List<String> texts(List<InputRows.Row> rows) {
        List<String> out = new ArrayList<>();
        for (InputRows.Row r : rows) out.add(r.text());
        return out;
    }

    @Test public void cecDevicesTakeTheirPortsRowInPortOrder() {
        assertEquals(Arrays.asList("HDMI 1", "Apple TV \u00B7 HDMI 2", "PlayStation 5 \u00B7 HDMI 2", "HDMI 3/ARC",
                "HDMI 4", "Video"), texts(InputRows.build(bravia(), false)));
    }

    @Test public void keepPortsListsThePortAfterItsDevices() {
        assertEquals(Arrays.asList("HDMI 1", "Apple TV \u00B7 HDMI 2", "PlayStation 5 \u00B7 HDMI 2", "HDMI 2",
                "HDMI 3/ARC", "HDMI 4", "Video"), texts(InputRows.build(bravia(), true)));
    }

    @Test public void devicesFirstPutsTheCecDevicesAheadOfThePorts() {
        List<InputRows.Row> rows = InputRows.devicesFirst(InputRows.build(bravia(), true));
        assertEquals(Arrays.asList("Apple TV \u00B7 HDMI 2", "PlayStation 5 \u00B7 HDMI 2", "HDMI 1", "HDMI 2",
                "HDMI 3/ARC", "HDMI 4", "Video"), texts(rows));
        assertTrue(rows.get(0).device);
        assertFalse(rows.get(2).device);
        assertEquals(EXT + "HDMI200008", rows.get(0).inputId);
    }

    @Test public void aDeviceRowShowsTheDeviceAsNameAndThePortUnderIt() {
        InputRows.Row appleTv = InputRows.build(bravia(), false).get(1);
        assertEquals("Apple TV", appleTv.name);
        assertEquals("HDMI 2", appleTv.port);
    }

    @Test public void aBarePortNumberGetsTheHdmiPrefix() {
        List<InputRows.Row> rows = InputRows.build(Collections.singletonList(port("HW1", "1", true)), false);
        assertEquals("HDMI 1", rows.get(0).text());
    }

    @Test public void theNameFromSettingsGoesFirstAndThePortAfterIt() {
        InputRows.Input named = new InputRows.Input(EXT + "HW1", null, "HDMI 1", "MacBook Pro", true, true, false);
        assertEquals("MacBook Pro \u00B7 HDMI 1", InputRows.build(Collections.singletonList(named), false).get(0).text());
    }

    @Test public void aPortsNameFromSettingsNamesAnUnnamedDeviceOnIt() {
        List<InputRows.Input> inputs = Arrays.asList(
                new InputRows.Input(EXT + "HW1", null, "HDMI 1", "Living room box", true, true, false),
                new InputRows.Input(EXT + "HDMI100004", EXT + "HW1", "", null, true, true, false));
        assertEquals(Collections.singletonList("Living room box \u00B7 HDMI 1"), texts(InputRows.build(inputs, false)));
    }

    /** Sony BRAVIA with HDMI 2 named "AppleTV" in Settings > Inputs and the Apple TV on it: two "AppleTV" tiles. */
    @Test public void keepPortsShowsANamedPortWithDevicesAsThePort() {
        List<InputRows.Input> inputs = Arrays.asList(
                new InputRows.Input(EXT + "HW2", null, "HDMI 2", "AppleTV", true, true, false),
                new InputRows.Input(EXT + "HDMI200004", EXT + "HW2", "AppleTV", null, true, true, false));
        assertEquals(Arrays.asList("AppleTV \u00B7 HDMI 2", "HDMI 2"), texts(InputRows.build(inputs, true)));
        assertEquals(Collections.singletonList("AppleTV \u00B7 HDMI 2"), texts(InputRows.build(inputs, false)));
    }

    @Test public void hiddenAndNonPassthroughInputsStayOut() {
        List<InputRows.Input> inputs = Arrays.asList(
                port("HW1", "HDMI 1", true),
                new InputRows.Input(EXT + "HW2", null, "HDMI 2", null, true, true, true),
                new InputRows.Input("tuner/HW0", null, "Channels", null, false, false, false));
        assertEquals(Collections.singletonList("HDMI 1"), texts(InputRows.build(inputs, false)));
    }

    @Test public void aHiddenDeviceLeavesItsPortListed() {
        List<InputRows.Input> inputs = Arrays.asList(
                port("HW1", "HDMI 1", true),
                new InputRows.Input(EXT + "HDMI100004", EXT + "HW1", "Apple TV", null, true, true, true));
        assertEquals(Collections.singletonList("HDMI 1"), texts(InputRows.build(inputs, false)));
    }

    @Test public void hiddenInputsMatchesIdNameOrTextIgnoringCase() {
        List<InputRows.Row> rows = InputRows.build(bravia(), true);
        assertEquals(Arrays.asList("HDMI 1", "Apple TV \u00B7 HDMI 2", "HDMI 2", "HDMI 3/ARC", "HDMI 4"),
                texts(InputRows.without(rows, Arrays.asList("video", " PlayStation 5 \u00B7 HDMI 2"))));
        assertEquals(6, InputRows.without(rows, Collections.singletonList(EXT + "HW4")).size());
        assertEquals(7, InputRows.without(rows, Collections.<String>emptyList()).size());
    }
}
