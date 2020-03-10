package com.example.myapplication3;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PredictActivity extends AppCompatActivity implements SensorEventListener {

    private Button startButton;
    private TextView infoText;
    private ScrollView scrollView;
    private RouteView canvas;

    private boolean recording;

    private SensorManager sManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic; //地磁场传感器
    //private float[] orientationArray;

    private float[] accelerometerValues = new float[3];
    private float[] magneticFieldValues = new float[3];


    private boolean motiveState = true;
    private float lstValue = (float) 9.8;

    private Timer timer;
    private Handler handler;
    private final double scale =  13; //比例 1step -> 画布上2.5个单位长度
    private float sumStandard;
    private float sumDegress = 0;
    private int sumCount = 0;
    private float[] degressArr;
    private float[] stepArr;
    private int degressSize = 0;
    private final int MaxSize = 1000;
    private int goStart = 0;
    private int goEnd = 0;
    private boolean go = true;
    private final float LIMIT = 15;
    private float lastGoOrient;
    private float curValue;
    private int allStep;

    private float lastChangeDegress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predict);
        bindView();

        handler = new Handler(){
            @Override
            public void handleMessage(Message msg){
                if(msg.what == 666){
                    //TODO: 将sum数据append到gData中
                    //infoText.append("=>" + msg.obj + "\n");
                    startButton.setText("停止 -步数："+allStep+"/"+msg.obj+"s");

                }
            }
        };


        recording = false;

        degressArr = new float[MaxSize];
        stepArr = new float[MaxSize];


        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        mSensorAccelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorMagnetic      = sManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        //case Sensor.TYPE_STEP_COUNTER:
        //case Sensor.TYPE_STEP_DETECTOR:

        sManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
        sManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);

        TextView sensorText = (TextView) findViewById(R.id.infoText);
        List<Sensor> allSensors = sManager.getSensorList(Sensor.TYPE_ALL);
        StringBuilder sb = new StringBuilder();
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
                    degressSize = 0;
                    allStep = 0;
                    infoText.setText("");
                    canvas.invalidate();
                    startButton.setText("停止");
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {

                            if(degressSize == MaxSize) return;
                            stepArr[degressSize] = allStep;
                            degressArr[degressSize++] = sumDegress/sumCount;
                            sumCount = 0;
                            sumDegress = 0;

                            Message msg = new Message();
                            msg.what = 666;
                            msg.obj = degressSize;
                            handler.sendMessage(msg);
                        }
                    }, 1000, 1000);
                }
                else{ //结束
                    startButton.setText("预测轨迹");
                    timer.cancel();
                    calAllDegress();
                    updateScroll();
                }
                recording = !recording;
            }
        });

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //infoText.append("one step");
        if(!recording) return;
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = sensorEvent.values.clone();
            //TODO: 根据加速度预测步数, 阈值可调
            curValue = (magnitude(accelerometerValues));
            calStep();
        }
        else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticFieldValues = sensorEvent.values.clone();
            calculateOrientation(sensorEvent.timestamp);
        }

    }

    //TODO:
    //1. 使用步数而不是speed x time
    //2. 使用当前方向
    public void updateRouteCanvas(float changeDegress, int goStartIndex, int goEndIndex){
        float distance = (float) ( (stepArr[goEndIndex] -stepArr[goStartIndex]) * scale);

        //infoText.append("\npaint=========>:" + changeDegress + "," + distance);
        canvas.appendLine(changeDegress, distance);
    }

    public void calStep() {
        double range = 5;   //设定一个精度范围
        //向上加速的状态
        if (motiveState == true) {
            if (curValue >= lstValue) lstValue = curValue;
            else {
                //检测到一次峰值
                if (Math.abs(curValue - lstValue) > range) {
                    motiveState = false;
                }
            }
        }
        //向下加速的状态
        if (motiveState == false) {
            if (curValue <= lstValue) lstValue = curValue;
            else {
                if (Math.abs(curValue - lstValue) > range) {
                    //检测到一次峰值
                    allStep += 1;
                    //startButton.setText("STOP:" + allStep);
                    motiveState = true;
                }
            }
        }

    }

    private void calculateOrientation(long timestamp) {
        float[] values = new float[3];
        float[] R = new float[9];
        SensorManager.getRotationMatrix(R, null, accelerometerValues,
                magneticFieldValues);
        SensorManager.getOrientation(R, values);

        float[] angle = new float[3];
        angle[0] = (float) Math.toDegrees(values[0]);
        angle[1] = (float) Math.toDegrees(values[1]);
        angle[2] = (float) Math.toDegrees(values[2]);

        //if(angle[0]<0) angle[0] += 360;
        //if(angle[1]<0) angle[1] += 360;
        //if(angle[2]<0) angle[2] += 360;

        //updateRouteCanvas(angle[0]);
        if(sumCount == 0) sumStandard = angle[0];
        sumDegress += transform(sumStandard, angle[0]);
        sumCount += 1;
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

    public float diffDegress(float a, float b){
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

    public float calGoOrientAvg(){
        float sum = 0;
        for(int i=goStart;i<goEnd;i++){
            sum += transform(degressArr[goStart], degressArr[i]);
        }
        return sum/(goEnd - goStart);
        //return degressArr[(goEnd-goStart)/2];
    }

    public float calChangeDegress(int index) {
        float sum = 0;
        for (int i = index - 3; i < index; i++) {
            float newd = transform(degressArr[index-3], degressArr[i]);
            sum += newd;
        }
        sum = sum / 3;
        return diffDegress(sum, lastGoOrient);

    }

    public void calAllDegress(){
        if(degressSize<=3) return;

        goStart = 0;
        goEnd = 0;
        go = true;
        lastGoOrient = 666;
        for (int i=0; i<degressSize;i++){
            infoText.append(String.format("%2d : %f\n",  i, degressArr[i]));
        }
        for (int i=3; i<degressSize;i++){
            if(Math.abs(diffDegress(degressArr[i], degressArr[i-3])) < LIMIT){//该3s为直线行驶
                if(!go){//如果正在拐弯，认为拐弯结束
                    float diff = calChangeDegress(i);
                    if(Math.abs(diff)>30){//如果总拐弯度数大于30，认为是有效的拐弯

                        infoText.append(String.format("%2d ~ %2d 直行 方向%f°\n",goStart, goEnd, lastGoOrient));
                        updateRouteCanvas(lastChangeDegress, goStart, goEnd);
                        goStart = i-1;
                        infoText.append(String.format("%2d ~ %2d", goEnd, goStart));
                    }
                }


                go = true;
            }
            else {//当前剧烈转弯
                if(go){//如果正在直行，认为开始转弯了
                    goEnd = i-1;

                    float avgOrient = calGoOrientAvg();
                    float diffGo = diffDegress(avgOrient, lastGoOrient);

                    if(lastGoOrient != 666){
                        infoText.append(String.format(" %s转 %f°\n", (diffGo>0)?'右':'左', Math.abs(diffGo)));
                        lastChangeDegress = diffGo;
                    }
                    else{
                        lastChangeDegress = 0;
                    }
                    lastGoOrient = avgOrient;
                }
                else{//认为转弯在继续

                }
                go = false;
            }
        }
        if(go) {
            goEnd = degressSize;
            float avgOrient = calGoOrientAvg();
            float diffGo = diffDegress(avgOrient, lastGoOrient);

            if (lastGoOrient != 666) {
                infoText.append(String.format(" %s转 %f°\n", (diffGo > 0) ? '右' : '左', Math.abs(diffGo)));
                lastChangeDegress = diffGo;
            }
            else{
                lastChangeDegress = 0;
            }
            lastGoOrient = avgOrient;

            infoText.append(String.format("%2d ~ %2d 直行 方向%f°\n", goStart, goEnd, lastGoOrient));
            updateRouteCanvas(lastChangeDegress, goStart, goEnd-1);
        }else{//最后仍在转弯

            float avgOrient = calGoOrientAvg();
            lastGoOrient = avgOrient;

            infoText.append(String.format("%2d ~ %2d 直行 方向%f°\n", goStart, goEnd, lastGoOrient));
            updateRouteCanvas(lastChangeDegress, goStart, goEnd);
        }

//NOTE:
//每次遇到剧烈转弯的时候计算之前直行的角度,以及与上一次直行的差值作为上一次转弯的角度
        //如果不是真实的转弯需要再次计算一次
        //如果是真实的转弯输出直行的范围，和转弯的时间段

    }
}
