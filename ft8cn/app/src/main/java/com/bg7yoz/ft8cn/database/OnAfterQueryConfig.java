package com.bg7yoz.ft8cn.database;

/**
 * 配置信息读取完毕的回调
 * @author BGY70Z
 * @date 2023-03-20
 */
public interface OnAfterQueryConfig {
    void doOnBeforeQueryConfig(String KeyName);
    void doOnAfterQueryConfig(String KeyName,String Value);

    /** Called once after every config row and related cached data have been loaded. */
    default void doOnConfigLoadComplete() {
        // Optional for existing callers that only consume individual config rows.
    }
}
