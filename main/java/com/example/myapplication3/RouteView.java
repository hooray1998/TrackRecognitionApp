package com.example.myapplication3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class RouteView extends View {
    private static final int MAX_SIZE = 100000;
    private static final boolean STATUS_ZOOM_IN = true;
    private static final boolean STATUS_ZOOM_OUT = false;
    private Paint mPaint;

    private float[] distanceList;
    private float[] degressList;
    public int size;
    private double lastFingerDis;
    private float totalRatio = 1;
    private float initDegree;
    private float lastDegress;

    public RouteView(Context context) {
        super(context);
        init();
    }


    public RouteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RouteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init(){
        size = 0;
        distanceList = new float[MAX_SIZE];
        degressList = new float[MAX_SIZE];
        mPaint = new Paint();
        initPaint();
    }

    private void initPaint() {
        mPaint.setAntiAlias(true);          //抗锯齿
        mPaint.setColor(Color.RED);//画笔颜色
        mPaint.setStyle(Paint.Style.FILL);  //画笔风格
        mPaint.setTextSize(36/totalRatio);             //绘制文字大小，单位px
        mPaint.setStrokeWidth(12/totalRatio);           //画笔粗细
    }

    public void appendLine(float distance, float degress){
        if(size==0){
            lastDegress = 0;
            initDegree = degress;
        }
        if(size == MAX_SIZE) return;
        distanceList[size] = distance;
        float d = degress - lastDegress;
        while(d<-180){
            d += 360;
        }
        while(d>180){
            d -= 360;
        }
        degressList[size] = d;
        lastDegress = degress;
        size += 1;
        invalidate();
    }

    //重写该方法，在这里绘图
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);


        //canvas.drawCircle(200, 200, 100, mPaint);           //画实心圆
        //canvas.drawCircle(0, 0, 100, mPaint); //画圆圈

        mPaint.setTextSize(36);             //绘制文字大小，单位px
        mPaint.setStrokeWidth(12);           //画笔粗细
        mPaint.setColor(Color.GRAY);//画笔颜色



        canvas.drawText("比例:  I——I " + 5 /totalRatio + "m", 50,50, mPaint);

        initPaint();


        canvas.translate(canvas.getWidth()/2, canvas.getHeight()/2); //将位置移动画纸的坐标点:150,150

        canvas.scale(totalRatio, totalRatio);
        //使用path绘制路径文字
        canvas.save();
        //canvas.rotate(90, 0, 0);
        for (int i = 0; i < size; i++) {
            canvas.drawLine(0,0, 0, -distanceList[i], mPaint);
            canvas.translate(0, -distanceList[i]);
            canvas.rotate(degressList[i]);
        }
        //canvas.rotate(360/count,0f,0f); //旋转画纸
        /*
        canvas.translate(-100, -100);
        Path path = new Path();
        path.addArc(new RectF(0,0,200,200), -180, 180);

        Paint citePaint = new Paint(mPaint);
        citePaint.setTextSize(14);
        citePaint.setStrokeWidth(1);
        canvas.drawTextOnPath("绘制表盘~", path, (float) (90*1.6), 30, citePaint);
        canvas.restore();

        Paint tmpPaint = new Paint(mPaint); //小刻度画笔对象
        tmpPaint.setStrokeWidth(1);
        tmpPaint.setTextSize(10);

        float  y=100;
        int count = 60; //总刻度数

        for(int i=0 ; i <count ; i++){
            if(i%5 == 0){
                canvas.drawLine(0f, y, 0, y+12f, mPaint);
                canvas.drawText(String.valueOf(i/5+1), -4f, y+25f, tmpPaint);

            }else{
                canvas.drawLine(0f, y, 0f, y +5f, tmpPaint);
            }
            canvas.rotate(360/count,0f,0f); //旋转画纸
        }

        //绘制指针
        tmpPaint.setColor(Color.GRAY);
        tmpPaint.setStrokeWidth(4);
        canvas.drawCircle(0, 0, 7, tmpPaint);
        tmpPaint.setStyle(Paint.Style.FILL);
        tmpPaint.setColor(Color.YELLOW);
        canvas.drawCircle(0, 0, 5, tmpPaint);
        canvas.drawLine(0, 10, 0, -65, mPaint);
        */
    }



    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2) {
                    // 当有两个手指按在屏幕上时，计算两指之间的距离
                    lastFingerDis = distanceBetweenFingers(event);
                }
                break;

            case MotionEvent.ACTION_MOVE:

                if (event.getPointerCount() == 2) {
                    // 有两个手指按在屏幕上移动时，为缩放状态
                    double fingerDis = distanceBetweenFingers(event);
                    boolean currentStatus;
                    if (fingerDis > lastFingerDis) {
                        currentStatus = STATUS_ZOOM_OUT;
                    } else {
                        currentStatus = STATUS_ZOOM_IN;
                    }
                    // 进行缩放倍数检查，最大只允许将图片放大4倍，最小可以缩小到初始化比例
                    if ((currentStatus == STATUS_ZOOM_OUT && totalRatio < 4)
                            || (currentStatus == STATUS_ZOOM_IN && totalRatio > 0.1)) {
                        float scaledRatio = (float) (fingerDis / lastFingerDis);
                        totalRatio = totalRatio * scaledRatio;
                        if (totalRatio > 10) {
                            totalRatio = 10;
                        } else if (totalRatio < 0.1) {
                            totalRatio = (float) 0.1;
                        }
                        // 调用onDraw()方法绘制图片
                        invalidate();
                        lastFingerDis = fingerDis;
                    }
                }
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 计算两个手指之间的距离。
     *
     * @param event
     */
    private double distanceBetweenFingers(MotionEvent event) {
        float disX = Math.abs(event.getX(0) - event.getX(1));
        float disY = Math.abs(event.getY(0) - event.getY(1));
        return Math.sqrt(disX * disX + disY * disY);
    }

}
