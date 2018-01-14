package com.mecatronica.ring.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;

public class AnchorPoint {

    private Bitmap mBitmap = null;
    private Point mPoint;
    private int mId;

    public AnchorPoint(Context context, @DrawableRes int resourceId, Point point, int id) {
        this.mId = id;
        if (resourceId != 0) mBitmap = createBitmapFromDrawable(context.getDrawable(resourceId));
        this.mPoint = point;
    }

    private Bitmap createBitmapFromDrawable(Drawable drawable) {
        Canvas canvas = new Canvas();
        mBitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(mBitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return mBitmap;
    }

    public int getWidth() {
        return mBitmap != null ? mBitmap.getWidth() : 0;
    }

    public int getHeight() {
        return mBitmap != null ? mBitmap.getHeight() : 0;
    }

    public int getCenterX() {
        return (int) (mPoint.x + getWidth() / 2f);
    }

    public int getCenterY() {
        return (int) (mPoint.y + getHeight() / 2f);
    }

    public void setPosition(int x, int y) {
        mPoint.x = (int) (x - getWidth() / 2f);
        mPoint.y = (int) (y - getHeight() / 2f);
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public int getX() {
        return mPoint.x;
    }

    public int getY() {
        return mPoint.y;
    }

    public int getID() {
        return mId;
    }

    public void setX(int x) {
        mPoint.x = x;
    }

    public void setY(int y) {
        mPoint.y = y;
    }

    public void addY(int y){
        mPoint.y += y;
    }

    public void addX(int x){
        mPoint.x += x;
    }
}
