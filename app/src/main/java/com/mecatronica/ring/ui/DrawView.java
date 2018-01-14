package com.mecatronica.ring.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.DrawableRes;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import com.mecatronica.ring.R;
import java.util.ArrayList;
import java.util.Collections;

public class DrawView extends View {
    private static final String TAG = DrawView.class.getSimpleName();
    private Point startMovePoint;
    @DrawableRes
    private int mResource = R.drawable.rect;
    private String mColor = "#FFFFFF";

    private int groupId = 2;
    private ArrayList<AnchorPoint> anchorPoints;
    private int anchorID = 0;
    private Paint paint;
    private int mStrokeWidth;

    public DrawView(Context context) {
        super(context);
        init(context);
    }

    public DrawView(Context context, @DrawableRes int resource) {
        super(context);
        mResource = resource;
        init(context);
    }

    public DrawView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public DrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    private void init(Context context) {
        setFocusable(true);

        paint = new Paint();
        mStrokeWidth = 2;

        Point coord1 = new Point();
        coord1.x = 20;
        coord1.y = 20;

        Point coord2 = new Point();
        coord2.x = 40;
        coord2.y = 20;

        Point coord3 = new Point();
        coord3.x = 40;
        coord3.y = 40;

        Point coord4 = new Point();
        coord4.x = 40;
        coord4.y = 20;

        anchorPoints = new ArrayList<>();
        anchorPoints.add(0,new AnchorPoint(context, mResource, coord1,0));
        anchorPoints.add(1,new AnchorPoint(context, mResource, coord2,1));
        anchorPoints.add(2,new AnchorPoint(context, mResource, coord3,2));
        anchorPoints.add(3,new AnchorPoint(context, mResource, coord4,3));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(Color.parseColor(mColor));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(mStrokeWidth);

        canvas.drawRect(getBounds(-2), paint);

        for (AnchorPoint anchor : anchorPoints) {
            Bitmap bitmap  = anchor.getBitmap();
            if (bitmap != null) canvas.drawBitmap(bitmap, anchor.getX(), anchor.getY(), new Paint());
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!isFocusable()) return false;

        int eventAction = event.getAction();

        int eventX = (int) event.getX();
        int eventY = (int) event.getY();

        switch (eventAction) {

        case MotionEvent.ACTION_DOWN:
            anchorID = -1;
            startMovePoint = new Point(eventX, eventY);
            for (AnchorPoint anchor : anchorPoints) {
                int centerX = anchor.getCenterX();
                int centerY = anchor.getCenterY();
                paint.setColor(Color.CYAN);
                // calculate the radius from the touch to the center of the anchor / Pitágoras {c1^2 + c1^2 = h^2}
                double radCircle = Math
                        .sqrt((double) (((centerX - eventX) * (centerX - eventX)) + (centerY - eventY)
                                * (centerY - eventY)));

                if (radCircle < anchor.getWidth() * 1.2f) {
                    anchorID = anchor.getID();
                    if (anchorID == 1 || anchorID == 3) {
                        groupId = 2;
                    } else {
                        groupId = 1;
                    }
                    break;
                }
            }
            break;

        case MotionEvent.ACTION_MOVE:
            if (anchorID > -1) {
                anchorPoints.get(anchorID).setX(eventX);
                anchorPoints.get(anchorID).setY(eventY);

                if (groupId == 1) {
                    anchorPoints.get(1).setX(anchorPoints.get(0).getX());
                    anchorPoints.get(1).setY(anchorPoints.get(2).getY());
                    anchorPoints.get(3).setX(anchorPoints.get(2).getX());
                    anchorPoints.get(3).setY(anchorPoints.get(0).getY());
                } else {
                    anchorPoints.get(0).setX(anchorPoints.get(1).getX());
                    anchorPoints.get(0).setY(anchorPoints.get(3).getY());
                    anchorPoints.get(2).setX(anchorPoints.get(3).getX());
                    anchorPoints.get(2).setY(anchorPoints.get(1).getY());
                }
            } else if (this.contains(new Point(eventX, eventY))) {
                if (startMovePoint != null) {
                    paint.setColor(Color.CYAN);
                    int diffX = eventX - startMovePoint.x;
                    int diffY = eventY - startMovePoint.y;
                    startMovePoint.x = eventX;
                    startMovePoint.y = eventY;
                    anchorPoints.get(0).addX(diffX);
                    anchorPoints.get(1).addX(diffX);
                    anchorPoints.get(2).addX(diffX);
                    anchorPoints.get(3).addX(diffX);
                    anchorPoints.get(0).addY(diffY);
                    anchorPoints.get(1).addY(diffY);
                    anchorPoints.get(2).addY(diffY);
                    anchorPoints.get(3).addY(diffY);
                }
            }

            break;
        case MotionEvent.ACTION_UP:
            break;
        }

        invalidate();
        return true;
    }

    public void setColor(String color) {
        if (color != null) mColor = color;
    }

    public void setStrokeWidth(int value) {mStrokeWidth = value;};

    public void setPosition(Rect rect) {
        //Log.d(TAG, "setPosition: " + rect.toString());
        setPosition(rect.left, rect.top, rect.right, rect.bottom);
    }

    public void setPosition(int left, int top, int right, int bottom){
        for (int i = 0; i < anchorPoints.size(); i++) {
            int x, y;
            switch (i) {
                case 0:
                    x = left;
                    y = top;
                    break;
                case 1:
                    x = right;
                    y = top;
                    break;
                case 2:
                    x = right;
                    y = bottom;
                    break;
                case 3:
                    x = left;
                    y = bottom;
                    break;
                default:
                    x = 0;
                    y = 0;
            }
            anchorPoints.get(i).setPosition(x, y);
        }
    }

    public Point getCenter() {
        float x = 0;
        float y = 0;

        for (AnchorPoint anchor : anchorPoints) {
            x += anchor.getCenterX();
            y += anchor.getCenterY();
        }

        x /= anchorPoints.size();
        y /= anchorPoints.size();

        return (new Point((int) x, (int) y));
    }

    public boolean contains(Point point) {
        return getBounds().contains(new Rect(point.x, point.y, point.x, point.y));
    }

    public Rect getBounds() {
        return getBounds(0);
    }

    public Rect getBounds(int inset) {
        ArrayList<Integer> compareInts = new ArrayList<>();
        int left, right, top, bottom;

        compareInts.clear();
        for (AnchorPoint anchor : anchorPoints) {
            compareInts.add(anchor.getCenterX());
        }
        left = Collections.min(compareInts) + inset;
        right = Collections.max(compareInts) - inset;

        compareInts.clear();
        for (AnchorPoint anchor : anchorPoints) {
            compareInts.add(anchor.getCenterY());
        }
        top = Collections.min(compareInts) + inset;
        bottom = Collections.max(compareInts) - inset;

        //Log.d(TAG, "getBounds: (Rect" + left + ", " + top + " - " + right + ", " + bottom + ")");
        return new Rect(left, top, right, bottom);
    }
}
