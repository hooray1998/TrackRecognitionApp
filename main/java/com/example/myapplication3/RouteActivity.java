package com.example.myapplication3;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.widget.TextView;

import android.util.Log;
public class RouteActivity extends AppCompatActivity {


    private SensorManager mSensorManager;

    private Sensor accelerometer; // 加速度传感器
    private Sensor magnetic; // 地磁场传感器

    private TextView azimuthAngle;
    private float lastTimestamp = 0;

    private float[] accelerometerValues = new float[3];
    private float[] magneticFieldValues = new float[3];

    private static final String TAG = "---RouteActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route);
        // 实例化传感器管理者
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // 初始化加速度传感器
        accelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // 初始化地磁场传感器
        magnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        azimuthAngle = (TextView) findViewById(R.id.tv_angle);
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        // 注册监听
        mSensorManager.registerListener(new MySensorEventListener(),
                accelerometer, Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(new MySensorEventListener(), magnetic,
                Sensor.TYPE_MAGNETIC_FIELD);
        super.onResume();
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        // 解除注册
        mSensorManager.unregisterListener(new MySensorEventListener());
        super.onPause();
    }

    // 计算方向
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

        if(angle[0]<0) angle[0] += 360;
        if(angle[1]<0) angle[1] += 360;
        if(angle[2]<0) angle[2] += 360;

        if(timestamp - lastTimestamp > 1000){
            lastTimestamp = timestamp;
            azimuthAngle.setText("\nx: " + Math.round(angle[0]) +
                                 "\ny: " + Math.round(angle[1]) +
                                 "\nz: " + Math.round(angle[2]));
        }
    }

    class MySensorEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // TODO Auto-generated method stub
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelerometerValues = event.values;
            }
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magneticFieldValues = event.values;
            }
            calculateOrientation(event.timestamp);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }

    }

}
