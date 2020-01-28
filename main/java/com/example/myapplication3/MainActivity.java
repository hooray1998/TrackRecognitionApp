package com.example.myapplication3;

import com.example.myapplication3.compassView.CompassView;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.util.List;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tv_value1;
    private TextView tv_value2;
    private TextView tv_value3;
    private SensorManager sManager;
    private Sensor mSensorOrientation;
    private CompassView cView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cView = new CompassView(MainActivity.this);
        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorOrientation = sManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        sManager.registerListener(this, mSensorOrientation, SensorManager.SENSOR_DELAY_UI);
        //bindViews();
        setContentView(cView);
    }

    private void bindViews() {
        tv_value1 = (TextView) findViewById(R.id.tv_value1);
        tv_value2 = (TextView) findViewById(R.id.tv_value2);
        tv_value3 = (TextView) findViewById(R.id.tv_value3);
        cView = (CompassView) findViewById(R.id.v_compassView);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        cView.setDegree(event.values[0]);
        //tv_value1.setText("方位角：" + (float) (Math.round(event.values[0] * 100)) / 100);
        //tv_value2.setText("倾斜角：" + (float) (Math.round(event.values[1] * 100)) / 100);
        //tv_value3.setText("滚动角：" + (float) (Math.round(event.values[2] * 100)) / 100);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        sManager.unregisterListener(this);
    }
}

