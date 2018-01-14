package com.mecatronica.ring;

public class StringItem {
    private static final int NO_IMAGE_PROVIDED = -1;
    private String mAction;
    private String mDescripcion;
    private int mImageResourceId = NO_IMAGE_PROVIDED;


    public StringItem(String action, String description) {
        mAction = action;
        mDescripcion = description;
    }

    public StringItem(String action, String description, int imageResId ) {
        mAction = action;
        mDescripcion = description;
        mImageResourceId = imageResId;
    }

    public String getDescripcion() {
        return mDescripcion;
    }

    public String getAction() {
        return mAction;
    }

    public int getImageResourceId() {
        return mImageResourceId;
    }

    public void setImageResourceId(int imageResId) {
        mImageResourceId = imageResId;
    }

    public boolean hasImage() {
        return mImageResourceId != NO_IMAGE_PROVIDED;
    }

}