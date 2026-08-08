package com.bg7yoz.ft8cn.ft8listener;
/**
 * 监听音频的回调，当结束解码后，调用afterDecode来通知解码的消息
 * @author BGY70Z
 * @date 2023-03-20
 */

import com.bg7yoz.ft8cn.Ft8Message;

import java.util.ArrayList;

public interface OnFt8Listen {
    /**
     * 当开始监听时触发的事件
     * @param utc 当前的UTC时间
     */
    default void beforeListen(long utc) {
    }

    /**
     * 带解码 epoch 的开始回调。旧实现仍可只实现 beforeListen(long)。
     */
    default void beforeListen(long utc, long epoch) {
        beforeListen(utc);
    }

    /**
     *当解码结束后触发的事件
     * @param utc 当前周期的UTC时间
     * @param time_sec 此次平均的偏移时间（秒）
     * @param sequential 当前的时序
     * @param messages 消息列表
     */
    default void afterDecode(long utc,float time_sec,int sequential
            , ArrayList<Ft8Message> messages,boolean isDeep) {
    }

    /**
     * 带解码 epoch 的结果回调。过期结果由接收方丢弃。
     */
    default void afterDecode(long utc,float time_sec,int sequential
            , ArrayList<Ft8Message> messages,boolean isDeep, long epoch) {
        afterDecode(utc, time_sec, sequential, messages, isDeep);
    }

    /**
     * 每个已开始的解码任务恰好收到一次终态回调。
     */
    default void onDecodeFinished(long utc, long epoch, boolean cancelled, Throwable failure) {
    }
}
