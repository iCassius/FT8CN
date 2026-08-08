package com.bg7yoz.ft8cn.log;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WavelogStationIdTest {
    @Test
    public void stationIdMatchesExactNumericOrQuotedToken() {
        assertTrue(ThirdPartyService.responseContainsStationId(
                "{\"station_id\":1}", "1"));
        assertTrue(ThirdPartyService.responseContainsStationId(
                "{\"station_profile_id\":\"1\"}", "1"));
        assertFalse("ID 1 must not match numeric ID 123",
                ThirdPartyService.responseContainsStationId(
                        "[{\"station_id\":123}]", "1"));
        assertFalse("ID 1 must not match quoted ID 123",
                ThirdPartyService.responseContainsStationId(
                        "[{\"station_id\":\"123\"}]", "1"));
    }
}
