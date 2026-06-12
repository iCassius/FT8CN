package com.bg7yoz.ft8cn.callsign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.AppExecutors;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * 呼号前缀匹配规则测试，防止 DXCC/归属地显示再次回归。
 * callsigns 表存的是 CTY.DAT 的前缀：查询必须前缀匹配并取最长命中；
 * "="开头的条目是完整呼号，精确匹配且优先于前缀命中。
 */
@RunWith(AndroidJUnit4.class)
public class CallsignDatabaseTest {
    private static CallsignDatabase database;

    @BeforeClass
    public static void setUpDatabase() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        database = new CallsignDatabase(context, null, null, 1);
        //InitDatabase 在 diskIO 单线程池里异步导入 CTY.DAT，提交一个空任务并等待，确保导入完成
        AppExecutors.getInstance().diskIO().submit(() -> {
        }).get(120, TimeUnit.SECONDS);
    }

    @Test
    public void prefixMatch_fullCallsignNotInTable() {
        //表里只有前缀没有完整呼号，精确匹配查询在这里必然查不到——这是 DXCC 回归的根因
        CallsignInfo info = database.getCallInfo("BG7YOZ");
        assertNotNull("前缀匹配失效：完整呼号不在表里，必须按前缀命中", info);
        assertEquals("BY", info.DXCC);
        assertTrue(info.CountryNameEn.contains("China"));
        //CTY.DAT 经度西经为正，东经为负；数据层必须返回原始值，取反只在显示层做
        assertTrue("数据层不应该对经度取反", info.Longitude < 0);
    }

    @Test
    public void prefixMatch_japanAndUsa() {
        CallsignInfo ja = database.getCallInfo("JA1ABC");
        assertNotNull(ja);
        assertTrue(ja.CountryNameEn.contains("Japan"));

        CallsignInfo us = database.getCallInfo("K1ABC");
        assertNotNull(us);
        assertEquals("United States", us.CountryNameEn);
    }

    @Test
    public void longestPrefixWins() {
        //BV9P（Pratas Island）比 BV（Taiwan China）长，必须取最长命中
        CallsignInfo pratas = database.getCallInfo("BV9PAB");
        assertNotNull(pratas);
        assertEquals("Pratas Island", pratas.CountryNameEn);

        CallsignInfo taiwan = database.getCallInfo("BV1AA");
        assertNotNull(taiwan);
        assertEquals("Taiwan China", taiwan.CountryNameEn);
    }

    @Test
    public void exactEntryStillMatches() {
        //cty.dat 里 "=BG8FUL/0" 是完整呼号条目（分区修饰已被解析器剥除），必须能精确命中
        CallsignInfo info = database.getCallInfo("BG8FUL/0");
        assertNotNull(info);
    }

    @Test
    public void unknownPrefixReturnsNull() {
        //Q 开头的业余前缀未分配；查不到必须返回 null（调用方约定判空）
        assertNull(database.getCallInfo("QQ1QQ"));
    }
}
