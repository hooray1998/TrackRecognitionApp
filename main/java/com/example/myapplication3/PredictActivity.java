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

import java.util.Timer;

public class PredictActivity extends AppCompatActivity implements SensorEventListener {

    private Button startButton;
    private TextView infoText;
    private ScrollView scrollView;
    private RouteView canvas;

    private boolean recording;

    private SensorManager sManager;
    private Sensor mSensorGyroscope;


    // 3*10个传感器数据
    private int groupSize = 10;
    private double[] coef;
    private double intercept;

    private float[] sensorData;
    private int sensorDataSize = 0;
    private float curDegress;
    private float sumDegress;


    private Timer timer;
    private long lasttime;
    private final float speed = 3;   //假设速度不变 m/s
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
        sensorData = new float[30];


        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorGyroscope = sManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sManager.registerListener(this, mSensorGyroscope, SensorManager.SENSOR_DELAY_UI);
        if(mSensorGyroscope == null){
            showToast("角速度传感器不支持");
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
                    canvas.invalidate();
                    sumDegress = 0;
                    startButton.setText("stop");
                }
                else{//结束
                    startButton.setText("start");
                    infoText.append("sum:" + sumDegress + "\n" + "\n");
                    updateScroll();
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
        if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
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


}
