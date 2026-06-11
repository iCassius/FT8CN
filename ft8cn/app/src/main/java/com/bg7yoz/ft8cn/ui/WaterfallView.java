package com.bg7yoz.ft8cn.ui;

import static android.graphics.Bitmap.Config.ARGB_8888;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.bg7yoz.ft8cn.Ft8Message;
import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.timer.UtcTimer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 瀑布图视图
 * 已修复：使用双缓冲滚动逻辑彻底解决重叠区域复制导致的“拖影”和多线程竞争导致的“闪烁”。
 */
public class WaterfallView extends View {
    private int blockHeight = 2;
    private float freq_width = 1;
    private final int cycle = 2;
    private final int symbols = 93;
    private int lastSequential = 0;
    
    // 双缓冲位图
    private Bitmap historyBitmap = null;
    private Bitmap pendingBitmap = null;
    private Canvas pendingCanvas = null;
    
    private final Paint linePaint = new Paint();
    private final Paint scrollPaint = new Paint();
    private final Paint touchPaint = new Paint();
    private final Paint fontPaint = new Paint();
    private final Paint messagePaint = new Paint();
    private final Paint textLinePaint = new Paint();
    private final Paint utcPaint = new Paint();
    private final Paint linearPaint = new Paint();
    private final Paint utcPainBack = new Paint();
    
    private float pathStart = 0;
    private float pathEnd = 0;
    private int touch_x = -1;
    private int freq_hz = -1;
    private boolean drawMessage = false;

    private final Rect scrollSrcRect = new Rect();
    private final Rect scrollDstRect = new Rect();
    private final Path messagePath = new Path();

    private long lastDrawTime = 0;
    private static final long DRAW_INTERVAL_MS = 100; // 提升至 10 FPS 保证流畅度，同时双缓冲解决闪烁

    public WaterfallView(Context context) {
        super(context);
    }

    public WaterfallView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WaterfallView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private final ArrayList<Ft8Message> messages = new ArrayList<>();

    private int dpToPixel(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        setClickable(true);
        blockHeight = Math.max(2, h / (symbols * cycle));
        freq_width = (float) w / 3000f;

        //与setWaveData（后台音频线程）共用一把锁：
        //回收/重建位图期间，后台线程可能正拿着旧位图绘制，会抛"recycled bitmap"异常导致闪退
        synchronized (this) {
            Bitmap oldHistory = historyBitmap;
            Bitmap oldPending = pendingBitmap;

            historyBitmap = Bitmap.createBitmap(w, h, ARGB_8888);
            pendingBitmap = Bitmap.createBitmap(w, h, ARGB_8888);
            pendingCanvas = new Canvas(pendingBitmap);

            historyBitmap.eraseColor(Color.BLACK);
            pendingBitmap.eraseColor(Color.BLACK);

            if (oldHistory != null) oldHistory.recycle();
            if (oldPending != null) oldPending.recycle();
        }

        linePaint.setColor(0xff990000);
        scrollPaint.setAntiAlias(false);
        scrollPaint.setFilterBitmap(false);

        touchPaint.setColor(0xff00ffff);
        touchPaint.setStrokeWidth(getResources().getDisplayMetrics().density);

        fontPaint.setTextSize(dpToPixel(10));
        fontPaint.setColor(0xff00ffff);
        fontPaint.setAntiAlias(true);
        fontPaint.setTextAlign(Paint.Align.LEFT);

        textLinePaint.setColor(0xff00ffff);
        textLinePaint.setAntiAlias(true);
        textLinePaint.setStrokeWidth(2);
        textLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

        messagePaint.setTextSize(dpToPixel(11));
        messagePaint.setColor(0xff00ffff);
        messagePaint.setAntiAlias(true);
        messagePaint.setStrokeWidth(0);
        messagePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        messagePaint.setTextAlign(Paint.Align.CENTER);
        messagePaint.setShadowLayer(10, 5, 5, Color.BLACK);

        utcPaint.setTextSize(dpToPixel(10));
        utcPaint.setColor(0xff00ffff);
        utcPaint.setAntiAlias(true);
        utcPaint.setTextAlign(Paint.Align.LEFT);

        utcPainBack.setTextSize(dpToPixel(10));
        utcPainBack.setColor(0xff000000);
        utcPainBack.setAntiAlias(true);
        utcPainBack.setStrokeWidth(dpToPixel(4));
        utcPainBack.setStyle(Paint.Style.FILL_AND_STROKE);
        utcPainBack.setTextAlign(Paint.Align.LEFT);

        pathStart = blockHeight * 2;
        pathEnd = Math.max(blockHeight * 90, dpToPixel(130));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (historyBitmap == null) return;
        
        // 关键：这里只画已经准备好的 historyBitmap，不再受后台写入影响
        synchronized (this) {
            canvas.drawBitmap(historyBitmap, 0, 0, null);
        }

        if (touch_x > 0) {
            freq_hz = Math.round(3000f * (float) touch_x / (float) getWidth());
            freq_hz = Math.max(100, Math.min(2900, freq_hz));
            String freqStr = String.format(Locale.ROOT, "%dHz", freq_hz);
            if (touch_x > getWidth() / 2) {
                fontPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(freqStr, touch_x - 10, 250, fontPaint);
            } else {
                fontPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(freqStr, touch_x + 10, 250, fontPaint);
            }
            canvas.drawLine(touch_x, 0, touch_x, getHeight(), touchPaint);
        }
    }

    public void setWaveData(int[] data, int sequential, List<Ft8Message> msgs) {
        if (historyBitmap == null || data == null || data.length == 0) return;

        synchronized (this) {//与onSizeChanged的位图重建互斥，见onSizeChanged中的说明
        // 1. 准备 Pending Canvas
        // 将旧的 history 整体向下搬移到 pending
        scrollSrcRect.set(0, 0, getWidth(), getHeight() - blockHeight);
        scrollDstRect.set(0, blockHeight, getWidth(), getHeight());
        pendingCanvas.drawBitmap(historyBitmap, scrollSrcRect, scrollDstRect, scrollPaint);

        // 2. 绘制分割线和时间戳
        if (sequential != lastSequential) {
            pendingCanvas.drawRect(0, 0, getWidth(), getResources().getDisplayMetrics().density, linePaint);
            String timeStr = UtcTimer.getTimeStr(UtcTimer.getSystemTime());
            float x = 50;
            float y = dpToPixel(15);
            pendingCanvas.drawText(timeStr, x, y, utcPainBack);
            pendingCanvas.drawText(timeStr, x, y, utcPaint);
        }
        lastSequential = sequential;

        // 3. 计算并绘制当前行颜色
        int[] colors = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            if (data[i] < 128) {
                colors[i] = 0xff000000 | (data[i] << 1);
            } else if (data[i] < 192) {
                colors[i] = 0xff0000ff | (((data[i] - 127)) << 10);
            } else {
                colors[i] = 0xff00ffff | (((data[i] - 127)) << 18);
            }
        }
        LinearGradient linearGradient = new LinearGradient(0, 0, getWidth(), 0, colors, null, Shader.TileMode.CLAMP);
        linearPaint.setShader(linearGradient);
        pendingCanvas.drawRect(0, 0, getWidth(), blockHeight, linearPaint);

        // 4. 绘制消息标记
        if (drawMessage && msgs != null) {
            drawMessage = false;
            for (Ft8Message msg : msgs) {
                if (msg.inMyCall()) {
                    messagePaint.setColor(0xffffb2b2);
                    textLinePaint.setColor(0xffffb2b2);
                } else if (msg.checkIsCQ()) {
                    messagePaint.setColor(0xffeeee00);
                    textLinePaint.setColor(0xffeeee00);
                } else {
                    messagePaint.setColor(0xff00ffff);
                    textLinePaint.setColor(0xff00ffff);
                }

                messagePath.reset();
                messagePath.moveTo(msg.freq_hz * freq_width, pathStart);
                messagePath.lineTo(msg.freq_hz * freq_width, pathEnd);
                String text = msg.getMessageText(true);
                pendingCanvas.drawTextOnPath(text, messagePath, 0, 0, messagePaint);
                
                if (GeneralVariables.checkQSLCallsign(msg.getCallsignFrom())) {
                    float text_len = messagePaint.measureText(text);
                    float text_start = ((pathEnd - pathStart) - text_len) / 2;
                    float text_high = dpToPixel(4);
                    pendingCanvas.drawLine(msg.freq_hz * freq_width + text_high, text_start + pathStart
                            , msg.freq_hz * freq_width + text_high, text_len + text_start + pathStart, textLinePaint);
                }
            }
        }

        // 5. 核心：原子化交换位图，确保 onDraw 看到的是完整的新帧
        Canvas historyCanvas = new Canvas(historyBitmap);
        historyCanvas.drawBitmap(pendingBitmap, 0, 0, null);
        }//synchronized

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDrawTime >= DRAW_INTERVAL_MS) {
            lastDrawTime = currentTime;
            postInvalidate();
        }
    }

    public void setTouch_x(int touch_x) {
        this.touch_x = touch_x;
        postInvalidate();
    }

    public void setDrawMessage(boolean drawMessage) {
        this.drawMessage = drawMessage;
    }

    public int getFreq_hz() {
        return freq_hz;
    }
}
