package com.mecatronica.ring.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;

public class SignView extends View {
    private String mColor = "#FFDD00";
    private Paint paint;
    private boolean mSignalingLeft = false;
    private boolean mSignalingRight = false;
    private boolean mSignalingTop = false;
    private boolean mSignalingBottom = false;
    private Rect mOutBounds = new Rect(0,0,0,0);
    private final float TEXT_SIZE = 80.0f;
    private final int BOX_STROKE_WIDTH = 5;
    private static final String TAG = SignView.class.getSimpleName();

    public SignView(Context context) {
        super(context);
        init(context);
    }

    public SignView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public SignView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setFocusable(false);

        paint = new Paint();
    }

    public void setSignalingLeft(boolean value){ this.mSignalingLeft = value;};

    public void setSignalingRight(boolean value){ this.mSignalingRight = value;};

    public void setSignalingTop(boolean value){ this.mSignalingTop = value;};

    public void setSignalingBottom(boolean value){ this.mSignalingBottom = value;};

    public void setRect(Rect value){this.mOutBounds.set(value);};

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(Color.parseColor(mColor));
        paint.setTextSize(TEXT_SIZE);
        paint.setStrokeWidth(BOX_STROKE_WIDTH);
        paint.setStyle(Paint.Style.STROKE);

        if (!this.isEmpty()) {
            canvas.drawRect(getBounds(-20), paint);

            if (mSignalingLeft) {
                if (mOutBounds.left < mOutBounds.right)
                    canvas.drawText(">>", mOutBounds.right + 40, mOutBounds.exactCenterY() + 15, paint);
                else
                    canvas.drawText(">>", mOutBounds.left + 40, mOutBounds.exactCenterY() + 15, paint);
            }
            if (mSignalingRight) {
                if (mOutBounds.left < mOutBounds.right)
                    canvas.drawText("<<", mOutBounds.left - 120, mOutBounds.exactCenterY() + 15, paint);
                else
                    canvas.drawText("<<", mOutBounds.right - 120, mOutBounds.exactCenterY() + 15, paint);
            }

            if (mSignalingTop) {
                canvas.save();
                canvas.rotate(-90, mOutBounds.exactCenterX(), mOutBounds.top);
                canvas.drawText(">>", mOutBounds.exactCenterX() + 40, mOutBounds.top + 26, paint);
                canvas.restore();
            }

            if (mSignalingBottom) {
                canvas.save();
                canvas.rotate(90, mOutBounds.exactCenterX(), mOutBounds.bottom);
                canvas.drawText(">>", mOutBounds.exactCenterX() + 40, mOutBounds.bottom + 26, paint);
                canvas.restore();
            }
        }
        else
            canvas.drawColor(Color.TRANSPARENT);
        super.onDraw(canvas);
    }

    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    public boolean isEmpty() {
        return (mOutBounds.left - mOutBounds.right - mOutBounds.top - mOutBounds.bottom == 0);
    }
    public Rect getBounds(int inset) {
        ArrayList<Integer> compareInts = new ArrayList<>();
        int left, right, top, bottom;

        compareInts.clear();
        compareInts.add(mOutBounds.left);
        compareInts.add(mOutBounds.right);
        left = Collections.min(compareInts) + inset;
        right = Collections.max(compareInts) - inset;

        compareInts.clear();
        compareInts.add(mOutBounds.top);
        compareInts.add(mOutBounds.bottom);
        top = Collections.min(compareInts) + inset;
        bottom = Collections.max(compareInts) - inset;

        return new Rect(left, top, right, bottom);
    }

    public void Clear(){
        mSignalingLeft = false;
        mSignalingRight = false;
        mSignalingTop = false;
        mSignalingBottom = false;
        mOutBounds.setEmpty();
        Sinalize();
        return;
    }

    public void Flash(){
        mSignalingLeft = true;
        mSignalingRight = true;
        mSignalingTop = true;
        mSignalingBottom = true;
        Sinalize();
        return;
    }

    public void Sinalize(){
        return;
    }
}

