package com.example.myapplication3;

import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication3.tools.FileWriter;

import android.content.Context;
import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


public class PredictActivity extends AppCompatActivity implements SensorEventListener {
    private Context mContext;

    private long startTime;
    private Button startButton;
    private TextView infoText;
    private ScrollView scrollView;
    private RouteView canvas;
    private final double scale =  13; //比例 1step -> 画布上2.5个单位长度


    private SensorManager sManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic; //地磁场传感器

    private float[] accelerometerValues = new float[3];
    private float[] magneticFieldValues = new float[3];
    private MediaPlayer mClock;

    private FileWriter fLinear;
    private FileWriter fOrientation;

    private int[] stepArr;
    private int curStep;
    private float[] degressArr;
    private int degreeSize;
    private final int MaxSize = 216000;//6hx60x60 = 36000x6 = 216000s

    private float[] oneSecondDegreeArr;
    private int oneSecondDegreeLen;
    private long oneSecondDegreeStartTime;

    private long lastStepTime;
    private boolean lastStepUp;
    private final int MinStepTime = 300;

    //直行区间总是>=5s
    private int waitIndex;
    private int goLen;//当前秒是否在直行(>=5),以及直行了多少秒了(总是大于等于5),因为5s内不算一段有效的直行
    private boolean lastIsGo;//上一秒是否在直行区间内(不是前五秒)
    private boolean recording;
	private float lastStepValue;

    private final int straightMinTime = 5;
    private final int limit = 15; //直行偏差在均值的+-15度以内
    private final float score = (float) 0.8; //5s中要有80%的点符合limit
    private float lastDegree = 0; //初始的轨迹偏转角

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predict);
        bindView();

        mContext = getApplicationContext();
        fLinear = new FileWriter(mContext);
        fOrientation = new FileWriter(mContext);

        oneSecondDegreeArr = new float[100];
        degressArr = new float[MaxSize];
        stepArr = new int[MaxSize];

        mClock = MediaPlayer.create(this, R.raw.clock);

        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorAccelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorMagnetic      = sManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);

        if(mSensorMagnetic == null){
            showToast("Error:地磁传感器不支持");
        }
        if(mSensorAccelerometer == null){
            showToast("Error:加速度传感器不支持");
        }

    }


    public void bindView(){
        startButton = findViewById(R.id.startButton);
        infoText = findViewById(R.id.infoText);
        scrollView = findViewById(R.id.scrollView);
        canvas = findViewById(R.id.canvas);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!recording){ // 开始
                    canvas.size = 0;
                    canvas.invalidate();
                    infoText.setText("");
                    startButton.setText("停止");
                    initVariable();
                    recording = true;
                    mClock.setLooping(true);
                    mClock.start();

                    startTime = System.currentTimeMillis();
                    fLinear.create(startTime + "_predict_Linear");
                    fOrientation.create(startTime + "_predict_Orientation");
                }
                else{ //结束
                    mClock.pause();
                    mClock.setLooping(false);
                    mClock.seekTo(0);
                    recording = false;
                    updateRoute();
                    startButton.setText("预测轨迹");
                    updateScroll();
                    fLinear.close();
                    fOrientation.close();
                }
            }
        });

    }

    public void initVariable() {
        lastStepTime = -MinStepTime;
        lastStepUp = false;
        lastStepValue = 10000;
        oneSecondDegreeLen = 0;
        waitIndex = straightMinTime;
        lastIsGo = false;

        curStep = 0;
        degreeSize = 0;
        lastDegree = 0;
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(!recording) return;

        long curTime = System.currentTimeMillis();
        startButton.setText((int)((curTime-startTime)/1000) + "s "+curStep*2+"m");
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = sensorEvent.values.clone();
            fLinear.append(accelerometerValues, recording);
            updateStep(curTime, magnitude(accelerometerValues));
        }
        else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticFieldValues = sensorEvent.values.clone();
            float[] angle = calculateOrientation();
            fOrientation.append(angle, recording);
            appendCurSecondDegree(curTime, angle);
        }

    }

    public void updateRouteCanvas(float curDegree, float distance){
        //infoText.append("\npaint=========>:" + curDegree + "," + distance);
        float changeDegress = diffDegree(curDegree, lastDegree);
        lastDegree = curDegree;
        canvas.appendLine(changeDegress, (float) (distance * scale));
    }


    private float[] calculateOrientation() {
        float[] values = new float[3];
        float[] R = new float[9];
        SensorManager.getRotationMatrix(R, null, accelerometerValues,
                magneticFieldValues);
        SensorManager.getOrientation(R, values);

        float[] angle = new float[3];
        angle[0] = (float) Math.toDegrees(values[0]);
        angle[1] = (float) Math.toDegrees(values[1]);
        angle[2] = (float) Math.toDegrees(values[2]);

        if(angle[0]<0) angle[0] += 360;
        if(angle[1]<0) angle[1] += 360;
        if(angle[2]<0) angle[2] += 360;

        return angle;
    }

    //向量求模
    public float magnitude(float[] values) {
        return  (float)Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
    }

    private void showToast(String string) {
        Toast.makeText(PredictActivity.this, string, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    public void updateScroll(){
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void updateStep(long t, float g) {
        boolean Up = (g > lastStepValue);
        //NOTE: 顶点判定
        if(lastStepUp && (!Up)){
            //NOTE: 时间和赋值判定
            //infoText.append(" G=" + lastStepValue + "\n");
            if((t-lastStepTime)>MinStepTime && lastStepValue>10.5){
                lastStepTime = t;
                curStep += 1;
            }
        }
        lastStepUp = Up;
        lastStepValue = g;
    }

    public void appendCurSecondDegree(long time, float[] oriValues) {
        // fOrient.append(oriValues);
        if(degreeSize==0 && oneSecondDegreeLen==0){
            oneSecondDegreeStartTime = time;
        }else if(time - oneSecondDegreeStartTime >= 1000){
            stepArr[degreeSize] = curStep;
            degressArr[degreeSize++] = averageOneSecond();
            updateRoute();
            oneSecondDegreeStartTime = time;
            oneSecondDegreeLen = 0;
        }
        oneSecondDegreeArr[oneSecondDegreeLen++] = oriValues[0];
    }

    public float transform(float standard, float needTransform){
        //转换needTransform到与standard的差距在180之内
        if(Math.abs(standard - needTransform) <= 180){
            return needTransform;
        }else if(needTransform > standard){
            return needTransform - 360;
        }else{
            return needTransform + 360;
        }
    }

    public float diffDegree(float a, float b){
        // 返回 -180 ~ 180
        float diff = a-b;
        while(diff<-180){
            diff += 360;
        }
        while(diff>180){
            diff -= 360;
        }
        return diff;
    }

    public float averageOneSecond(){
        float sum = 0;
        for(int i=0;i<oneSecondDegreeLen;i++){
            sum += transform(oneSecondDegreeArr[0], oneSecondDegreeArr[i]);
        }
        if(oneSecondDegreeLen == 0) return 0;
        // System.out.println("avg:" + sum/oneSecondDegreeLen);
        return sum/oneSecondDegreeLen;
    }

    /**
     * 计算end索引前面的size个点的平均值，不包括end
     *
     * @param end 结束的点，计算时不包含
     * @param size 点的个数
     * @return 返回平均值
     */
    public float averageDegreeArr(int end, int size) {
        if(end < size){
            //System.out.println("error in averageDegreeArr");
            return 0;
        }
        float sum = 0;
        for(int i=end-size;i<end;i++){
            sum += transform(degressArr[end-size], degressArr[i]);
        }
        return sum/size;
    }

    /**
     * 判断当前秒为终点的五秒的区间是否是直的
     * @return true/false
     */
    public boolean judgeStraight() {
        float average = averageDegreeArr(waitIndex, straightMinTime);
        int yes = 0;
        for(int i=waitIndex-straightMinTime;i<waitIndex;i++){
            if(Math.abs(diffDegree(degressArr[i], average)) <= limit){
                yes += 1;
            }
        }
        return yes/straightMinTime >= score;
    }

    /**
     * 每有了1s的角度数据后就判断是否在直行区间
     */
    public void updateRoute() {
        //如果结束了，上一次还是直行的话也要输出
        //
        //思路:
        //1. 等有了五秒数据的时候开始判断
        //2. 判断刚刚五秒是否有4个点在平均分附近,因为刚开始,所以直行记录直行了五秒钟golen
        //3. 继续看第六秒
        //   如果刚刚5s是直行,那么以该点为终点的5s判断是否是直的
        //          如果当前也直,那么len=6
        //          如果当前不直,那么len=5or6.7.8 结束并输出刚刚直行的长度,并且之后的三秒也不需要判断,因为如果算了,那么就跟刚刚的直行连接上了,那也就不算中断了
        //   如果刚刚不直,现在也不直,那就跳过
        //   如果现在5s直了,那就记录len=5
        if(waitIndex != degreeSize && recording){
            return;
        }
        //NOTE:此时waitIndex 等于 degreeSize ，也就是最后一个(当前处理的)点的索引+1
        boolean curIsGo = false;
        if(recording){
            curIsGo = judgeStraight();
        }

        if(curIsGo && lastIsGo){
            goLen += 1;
        }else if(curIsGo){
            goLen = 5;
        //当直行中断后的几秒并不需要判断,直接跳过,所以waitIndex + 4
        }else if(lastIsGo){
            float average = averageDegreeArr(waitIndex-1, goLen);
            int distance =  diffStep(waitIndex-1, goLen) * 2; //一圈为2米
            //System.out.println(String.format("直行：%d ~ %dS, 距离: %d 方向:%f",waitIndex-goLen-1, waitIndex-1, distance, average));
            infoText.append(String.format("直行: %3d ~ %3ds, 距离: %3dm 方向: %3f°\n",waitIndex-goLen-1, waitIndex-1, distance, average));
            updateRouteCanvas(average, distance);
            waitIndex += 4;
        }

        lastIsGo = curIsGo;
        waitIndex += 1;
    }

    private int diffStep(int end, int size) {
        Log.d("stepArr:", degreeSize + " " + end + " " + size + " " + stepArr[degreeSize-1]);
        if(end >= degreeSize)
            end = degreeSize - 1;
        if(end < size){
            size = end;
        }
        return stepArr[end] - stepArr[end-size];
    }

}
