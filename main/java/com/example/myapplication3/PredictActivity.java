package com.example.myapplication3;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Timer;

public class PredictActivity extends AppCompatActivity implements SensorEventListener {

    private Button startButton;
    private TextView infoText;
    private ScrollView scrollView;
    private RouteView canvas;

    private boolean recording;

    private SensorManager sManager;
    private Sensor mSensorGyroscope;
    private Sensor mSensorAccelerometer;
    private long lastGTimestamp;


    // 3*10个传感器数据
    private int groupSize = 10;
    private double[] coef;
    private double intercept;

    private int gSensorMaxSize = 30;
    private float[] gSensorData;
    private int gSensorDataSize = 0;

    private float[] sensorData;
    private int sensorDataSize = 0;
    private float curDegress;
    private float sumDegress;

    private boolean motiveState;
    private float lstValue = (float) 9.8;
    private long lastTimestamp;

    private Timer timer;
    private long lasttime;
    private float speed = 0;   //假设速度不变 m/s
    private final double scale =  13; //比例 1m -> 画布上2.5个单位长度

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predict);
        bindView();

        timer  = new Timer();

        recording = false;

        coef = new double[]{-1.828248346611905, -3.8638827651022396, 6.976062724386655, -1.114699278882068, -3.860645638219377, 3.6965536675783084, -1.1726930273202099, -4.35333009288784, 3.274199896698567, -0.44304002485394683, -3.2145565865058376, 2.384390465320379, -1.0228108887002891, -2.18221748636867, 2.074074198873901, -1.8594716307833599, -2.9128018323635, 2.4412958070792334, -2.4281774375919345, -4.193998621192318, 3.523527085554875, -2.374823708958425, -3.516786351052552, 4.942739855078073, -0.9114904248700529, -4.019354137326663, 3.441393663000954, -0.33809578005131447, -4.585292784844483, 6.241746896962011};
        intercept = 1.1123271699863773;
        sensorData = new float[groupSize*3];
        gSensorData = new float[gSensorMaxSize];


        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorGyroscope = sManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorAccelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sManager.registerListener(this, mSensorGyroscope, SensorManager.SENSOR_DELAY_UI);
        sManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);

        TextView sensorText = (TextView) findViewById(R.id.infoText);
        List<Sensor> allSensors = sManager.getSensorList(Sensor.TYPE_ALL);
        StringBuilder sb = new StringBuilder();

        sb.append("此手机有" + allSensors.size() + "个传感器，分别有：\n\n");
        for(Sensor s:allSensors){
            switch (s.getType()){
                case Sensor.TYPE_ACCELEROMETER:
                    sb.append(s.getType() + " 加速度传感器(Accelerometer sensor)" + "\n");
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    sb.append(s.getType() + " 陀螺仪传感器(Gyroscope sensor)" + "\n");
                    break;
                case Sensor.TYPE_LIGHT:
                    sb.append(s.getType() + " 光线传感器(Light sensor)" + "\n");
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    sb.append(s.getType() + " 磁场传感器(Magnetic field sensor)" + "\n");
                    break;
                case Sensor.TYPE_ORIENTATION:
                    sb.append(s.getType() + " 方向传感器(Orientation sensor)" + "\n");
                    break;
                case Sensor.TYPE_PRESSURE:
                    sb.append(s.getType() + " 气压传感器(Pressure sensor)" + "\n");
                    break;
                case Sensor.TYPE_PROXIMITY:
                    sb.append(s.getType() + " 距离传感器(Proximity sensor)" + "\n");
                    break;
                case Sensor.TYPE_TEMPERATURE:
                    sb.append(s.getType() + " 温度传感器(Temperature sensor)" + "\n");
                    break;
                case Sensor.TYPE_STEP_COUNTER:
                    sb.append(s.getType() + " 步数传感器(Temperature sensor)" + "\n");
                    break;
                case Sensor.TYPE_STEP_DETECTOR:
                    sb.append(s.getType() + " 步行传感器(Temperature sensor)" + "\n");
                    break;
                default:
                    break;
            }
        }
        sb.append("其余都是未知传感器");
        sensorText.setText(sb.toString());

        if(mSensorGyroscope == null){
            showToast("角速度传感器不支持");
        }
        if(mSensorAccelerometer == null){
            showToast("计步器传感器不支持");
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
                if(!recording){
                    canvas.size = 0;
                    sensorDataSize = 0;
                    lasttime = System.currentTimeMillis();
                    lastTimestamp = lasttime;
                    lastGTimestamp = lasttime;
                    canvas.invalidate();
                    sumDegress = 0;
                    startButton.setText("stop");
                }
                else{//结束
                    startButton.setText("start");
                    infoText.append("sum:" + sumDegress + "\n" + "\n");
                    updateScroll();
                    speed = 0;
                }
                recording = !recording;
            }
        });

    }

    public void updateScroll(){
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public void appendSensorData(float[] values) {
        sensorData[sensorDataSize + 0] = values[0];
        sensorData[sensorDataSize + 1] = values[1];
        sensorData[sensorDataSize + 2] = values[2];
        sensorDataSize += 3;
        if(sensorDataSize == 3*groupSize){
            curDegress = predictDegress();
            sumDegress += curDegress;
            infoText.append("=>" + curDegress + "\n");
            updateScroll();
            updateRouteCanvas(curDegress);
            sensorDataSize = 0;
        }
    }
    public void appendGSensorData(float value) {
        gSensorData[gSensorDataSize] = value;
        gSensorDataSize += 1;
        if(gSensorDataSize == gSensorMaxSize){
            calStep();
            infoText.append("S=>" + speed + "\n");
            gSensorDataSize = 0;
        }
    }

    public float predictDegress() {
        float degress = 0;
        for (int i = 0; i < 3*groupSize; i++) {
            degress += coef[i] * sensorData[i];
        }
        return (float) (degress + intercept);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(!recording) return;
        //infoText.append("one step");
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //appendSensorData(sensorEvent.values.clone());
            //startButton.setText(sensorEvent.values[0] + "");
            //infoText.append("----" + sensorEvent.values[0]);
            //if(sensorEvent.values[0] == 1.0f){
                //infoText.append("one step");
            //}

            float curValue = magnitude(sensorEvent.values.clone());   //计算当前的模
            //calStep(curValue);
            appendGSensorData(curValue);
        }
        else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            appendSensorData(sensorEvent.values.clone());
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    private void showToast(String string) {
        Toast.makeText(PredictActivity.this, string, Toast.LENGTH_LONG).show();
    }


    public void updateRouteCanvas(float changeDegress){
        long curtime = System.currentTimeMillis();
        long difftime = curtime - lasttime;
        lasttime = curtime;

        float distance = (float) ((speed * difftime/1000) * scale);

        canvas.appendLine(distance, changeDegress);
    }

    public void calStep() {
        /*
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
                    updateSpeed();
                    motiveState = true;
                }
            }
        }

         */
        //根据一段时间求步数
        
        //def f1(line):
            //return (line[1]**2 + line[2]**2 + line[3]**2)**0.5

        //vArr = [f1(line) for line in self.data]

        //newVArr = []
        //newVArr.append(vArr[0])
        //last = vArr[1]
        //up = True
        //lastStep = vArr[1] > vArr[0]
        boolean lastStep = gSensorData[1] > gSensorData[0];
        boolean up;
        float last = gSensorData[1];
        int newLen = 1;
        for(int i = 2; i < gSensorMaxSize; i++){
            up = gSensorData[i] > last;
            if(up != lastStep) {
                gSensorData[newLen++] = last;
            }
            last = gSensorData[i];
            lastStep = up;
        }
        //for v in vArr[2:]:
            //up = v > last
            //if up != lastStep:
                //newVArr.append(last)
            //last = v
            //lastStep = up


        //last = newVArr[0]
        //count = 0
        //for v in newVArr:
            //if abs(v - last) < 5:
                //count += 1
            //last = v
        //self.step = int((len(newVArr) - count) / 2)
        last = gSensorData[0];
        int newLen2 = 1;
        for(int i = 1; i < newLen;i++){
            if(Math.abs(gSensorData[i] - last) > 5){
                gSensorData[newLen2++] = last;
            }
            last = gSensorData[i];
        }

        int count = 0;
        if(newLen2 < 2){
            speed = 0;
            return;
        }

        //计算几个山峰
        lastStep = gSensorData[1] > gSensorData[0];
        last = gSensorData[1];
        for(int i = 2; i < gSensorMaxSize; i++){
            up = gSensorData[i] > last;
            if(!up && lastStep) {
                count ++;
            }
            last = gSensorData[i];
            lastStep = up;
        }
        long curTime = System.currentTimeMillis();
        infoText.append("step: => " + count);
        speed = (float) (1000.0 * count/(curTime - lastGTimestamp));
        lastGTimestamp = curTime;


    }

    public void updateSpeed() {
        long timestamp = System.currentTimeMillis();
        speed = (float) (1000.0/(timestamp - lastTimestamp));
        infoText.append("cur speed:" + speed +" "+(timestamp - lastTimestamp) + " ");
        lastTimestamp = timestamp;
    }
    //向量求模
    public float magnitude(float[] values) {
        return  (float)Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
    }
}
