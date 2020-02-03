package com.example.myapplication3.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class linear implements SensorEventListener{
    //传感器
    private SensorManager sManager;
    private Sensor mSensorAccelerometer;
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
