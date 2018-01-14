package com.mecatronica.ring;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.camera2.params.Face;
import android.util.AttributeSet;
import android.util.Size;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.abs;

public class DrawingView extends View{

    private float BOX_STROKE_WIDTH = 10.0f;
    private float TEXT_SIZE = 80.0f;
    private boolean hasFace = false;
    private List<Rect> detectedFaces = new ArrayList();
    private Paint drawingPaint;
    private Size sensorSize;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private int contador =0;
    private int deviceRotation;
    Rect outBounds= new Rect(30000, 30000, 0, 0);
    private boolean fullScreen, signalingLeft, signalingTop, signalingRight, signalingBottom;
    private BluetoothListener bl = null;

    private String TAG = "Camera2SelfieDrawing";

    public DrawingView(Context context) {
        this(context, null);
    }

    public DrawingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        fullScreen = true;
        hasFace = false;
        drawingPaint = new Paint();
        drawingPaint.setTextSize(TEXT_SIZE);
    }



    private void computeFaces(Face[] f) {
        detectedFaces.clear();
        outBounds.set(30000, 30000, 0, 0);
        int w = this.getWidth();
        int h = this.getHeight();

        for (int i = 0; i < f.length; i++) {
            float xScale = 1.0f;
            float yScale = 1.0f;
            int left = 0, top = 0, right = 0, bottom = 0;
            Rect r = f[i].getBounds();

            if (deviceRotation == 0) {
                xScale = (float) w / sensorSize.getHeight();
                yScale = (float) h / sensorSize.getWidth();

                left = sensorSize.getHeight() - r.bottom;
                top = r.left;
                right = sensorSize.getHeight() - r.top;
                bottom = r.right;
            } else if (deviceRotation == 1) {
                xScale = (float) w / sensorSize.getWidth();
                yScale = (float) h / sensorSize.getHeight();

                left = r.left;
                top = r.top;
                right = r.left + abs((r.bottom - r.top));
                bottom = r.top + abs((r.right - r.left));
            } else if (deviceRotation == 3) {
                xScale = (float) w / sensorSize.getWidth();
                yScale = (float) h / sensorSize.getHeight();

                right = sensorSize.getWidth() - r.left;
                bottom = sensorSize.getHeight() - r.top;
                left = right - abs(r.left - r.right);
                top = bottom - abs(r.top - r.bottom);
            }

            left = (int) (left * xScale);
            top = (int) (top * yScale);
            right = (int) (right * xScale);
            bottom = (int) (bottom * yScale);

            detectedFaces.add(new Rect(left, top, right, bottom));

            if (outBounds.left > left) outBounds.left = left;
            if (outBounds.top > top) outBounds.top = top;
            if (outBounds.right < right) outBounds.right = right;
            if (outBounds.bottom < bottom) outBounds.bottom = bottom;
        }

        expandRect(outBounds, 20, w, h);

        signalingLeft   = outBounds.exactCenterX() / (w / 2.0f) > 1.10f;
        signalingRight  = outBounds.exactCenterX() / (w / 2.0f) < 0.90f;
        signalingTop    = outBounds.exactCenterY() / (h / 2.0f) > 1.10f;
        signalingBottom = outBounds.exactCenterY() / (h / 2.0f) < 0.90f;

        if (bl != null) {
            if (signalingLeft)
                bl.sendMessage("i");
            if (signalingRight)
            bl.sendMessage("c");
            if (signalingTop)
            bl.sendMessage("l");
            if (signalingBottom)
            bl.sendMessage("f");
        }
/*

        desligar s leds

*/
    }

    public void expandRect(Rect r, int valor, int w, int h){
        if (r.left < r.right){
            if (r.left - valor > 0)
                r.left = r.left - valor;
            else
                r.left = 0;

            if (r.top - valor > 0)
                r.top = r.top - valor;
            else
                r.top = 0;

            if (r.right + valor < w)
                r.right = r.right + valor;
            else
                r.right = w;

            if (r.bottom + valor < h)
                r.bottom = r.bottom + valor;
            else
                r.bottom = h;
        }
        else {
            if (r.left + valor < h)
                r.left = r.left + valor;
            else
                r.left = h;

            if (r.top + valor < h)
                r.top = r.top + valor;
            else
                r.top = h;

            if (r.right - valor > 0)
                r.right = r.right - valor;
            else
                r.right = 0;

            if (r.bottom - valor > 0)
                r.bottom = r.bottom - valor;
            else
                r.bottom = 0;
        }
    }

    public void setHaveFace(boolean h, Face[] f, Size ss) {
        hasFace = h;
        sensorSize = ss;

        if (f != null)
            computeFaces(f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawingPaint.setColor(Color.GREEN);
        if (hasFace) {
            drawingPaint.setStyle(Paint.Style.FILL);
            canvas.drawText("Faces detected: " + detectedFaces.size(), 20, 120, drawingPaint);
            canvas.drawText("Device rotation : " + deviceRotation, 20, 190, drawingPaint);

            for (int i = 0; i < detectedFaces.size(); i++) {
                Rect r = detectedFaces.get(i);

                drawingPaint.setStrokeWidth(BOX_STROKE_WIDTH);
                drawingPaint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(r.left, r.top, r.right, r.bottom, drawingPaint);
            }
            drawSignnaling(canvas);
        } else {
            canvas.drawText("Faces detected: 0", 20, 120, drawingPaint);
            canvas.drawColor(Color.TRANSPARENT);
        }
        super.onDraw(canvas);
    }

    private void drawSignnaling(Canvas canvas) {
        drawingPaint.setColor(Color.WHITE);
        if (outBounds.left != 30000 && detectedFaces.size() > 1)
            canvas.drawRect(outBounds.left, outBounds.top, outBounds.right, outBounds.bottom, drawingPaint);

        if (deviceRotation == 0) {
            if (signalingLeft)
                canvas.drawText("<<", outBounds.left - 150, outBounds.exactCenterY(), drawingPaint);

            if (signalingRight)
                canvas.drawText(">> ", outBounds.right + 50, outBounds.exactCenterY(), drawingPaint);

            if (signalingTop) {
                canvas.save();
                canvas.rotate(-90, outBounds.exactCenterX(), outBounds.top - 50);
                canvas.drawText(">>", outBounds.exactCenterX(), outBounds.top - 50, drawingPaint);
                canvas.restore();
            }

            if (signalingBottom) {
                canvas.save();
                canvas.rotate(90, outBounds.exactCenterX(), outBounds.bottom + 50);
                canvas.drawText(">>", outBounds.exactCenterX(), outBounds.bottom + 50, drawingPaint);
                canvas.restore();
            }
        }
        else if (deviceRotation == 1) {
            if (signalingLeft)
                canvas.drawText("<<", outBounds.left -150, outBounds.exactCenterY(), drawingPaint);

            if (signalingRight)
                canvas.drawText(">>", outBounds.right + 50, outBounds.exactCenterY(), drawingPaint);

            if (signalingTop) {
                canvas.save();
                canvas.rotate(-90, outBounds.exactCenterX(), outBounds.top - 50);
                canvas.drawText(">>", outBounds.exactCenterX(), outBounds.top - 50, drawingPaint);
                canvas.restore();
            }
            if (signalingBottom) {
                canvas.save();
                canvas.rotate(90, outBounds.exactCenterX(), outBounds.bottom + 100);
                canvas.drawText(">>", outBounds.exactCenterX(), outBounds.bottom + 100, drawingPaint);
                canvas.restore();
            }
        }
    }

    public void setBluetoothListener(BluetoothListener b){
        bl = b;
    }
}

