package com.example.myapplication3;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;

import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * create by heliquan at 2017年5月4日23:26:59
 */

/***
 * 高德定位
 */
public class MainActivity extends AppCompatActivity implements AMapLocationListener, SensorEventListener {
    //传感器
    private SensorManager sManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorGyroscope;
    private Sensor mSensorPressure;

    private static final int MY_PERMISSIONS_REQUEST_CALL_LOCATION = 1;
    public AMapLocationClient mlocationClient;
    public AMapLocationClientOption mLocationOption = null;

    private TextView locationText;
    private TextView jiasuduText;
    private TextView tuoluoyiText;
    private TextView qiyaText;

    private Button mapButton;;
    private Button sensorButton;
    private Button saveButton;

    private RadioGroup rdGroup;

    private String curCheck = "init";
    private String curFileName;
    private boolean recoding = false;

    private FileOutputStream fGps;
    private FileOutputStream fLinear;
    private FileOutputStream fAngular;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindView();
        initSensor();
        /**
         * 动态获取权限，Android 6.0 新特性，一些保护权限，除了要在AndroidManifest中声明权限，还要使用如下代码动态获取
         */
        if (Build.VERSION.SDK_INT >= 23) {
            int REQUEST_CODE_CONTACT = 101;
            String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
            //验证是否许可权限
            for (String str : permissions) {
                if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                    //申请权限
                    this.requestPermissions(permissions, REQUEST_CODE_CONTACT);
                    return;
                }
            }
        }
    }

    private void initSensor() {
        //检查版本是否大于M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_REQUEST_CALL_LOCATION);
            } else {
                //"权限已申请";
                showLocation();
            }
        }
        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorAccelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorGyroscope = sManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorPressure = sManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sManager.registerListener((SensorEventListener) this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
        sManager.registerListener((SensorEventListener) this, mSensorGyroscope, SensorManager.SENSOR_DELAY_UI);
        sManager.registerListener((SensorEventListener) this, mSensorPressure, SensorManager.SENSOR_DELAY_UI);

        if(mSensorAccelerometer == null){
            qiyaText.setText("加速度传感器不支持");
        }
        if(mSensorGyroscope == null){
            qiyaText.setText("角速度传感器不支持");
        }
        if(mSensorPressure == null){
            qiyaText.setText("气压传感器不支持");
        }
    }

    private void bindView() {
        jiasuduText = (TextView)  findViewById(R.id.tv_jiasudu);
        tuoluoyiText = (TextView)  findViewById(R.id.tv_tuoluoyi);
        qiyaText = (TextView)  findViewById(R.id.tv_qiya);
        locationText = (TextView) findViewById(R.id.tv_location);
        saveButton = (Button) findViewById(R.id.saveButton);
        rdGroup = (RadioGroup) findViewById(R.id.radioGroup);

        mapButton = (Button) findViewById(R.id.mapButton);
        sensorButton = (Button) findViewById(R.id.sensorButton);

        mapButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                //Intent是一种运行时绑定（run-time binding）机制，它能在程序运行过程中连接两个不同的组件。 
                //在存放资源代码的文件夹下下， 
                Intent i = new Intent(MainActivity.this , MapActivity.class);
                //启动 
                startActivity(i);
            }
        });
        sensorButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                //Intent是一种运行时绑定（run-time binding）机制，它能在程序运行过程中连接两个不同的组件。 
                //在存放资源代码的文件夹下下， 
                Intent i = new Intent(MainActivity.this , SensorActivity.class);
                //启动 
                startActivity(i);
            }
        });

        rdGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                RadioButton radbtn = (RadioButton) findViewById(i);
                curCheck = radbtn.getText().toString();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!recoding){
                    if(curCheck == "init"){
                        Toast.makeText(getApplicationContext(), "请先选择轨迹类型", Toast.LENGTH_LONG).show();
                        return;
                    }
                    recoding = !recoding;
                    saveButton.setText("保存");
                    rdGroup.setVisibility(View.INVISIBLE);

                    String curTime = System.currentTimeMillis()+"ms.txt";

                    try {
                        fGps = getApplicationContext().openFileOutput("Gps_"+curCheck + curTime,Context.MODE_APPEND);
                        fLinear = getApplicationContext().openFileOutput("Linear_"+curCheck + curTime,Context.MODE_APPEND);
                        fAngular = getApplicationContext().openFileOutput("Angular_"+curCheck + curTime,Context.MODE_APPEND);
                        showToast("开始记录数据");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                else{
                    recoding = !recoding;
                    saveButton.setText("开始");
                    rdGroup.setVisibility(View.VISIBLE);
                    try {
                        fGps.close();
                        fLinear.close();
                        fAngular.close();
                        showToast("记录完成，文件保存");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

            }
        });


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_CALL_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //"权限已申请"
                showLocation();
            } else {
                showToast("权限已拒绝,不能定位");

            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void showLocation() {
        try {
            mlocationClient = new AMapLocationClient(this);
            mLocationOption = new AMapLocationClientOption();
            mlocationClient.setLocationListener(this);
            //设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            mLocationOption.setInterval(1000);
            //设置定位参数
            mlocationClient.setLocationOption(mLocationOption);
            //启动定位
            mlocationClient.startLocation();
            showToast("显示定位OK");
        } catch (Exception e) {
            showToast("显示定位异常");
        }
    }

    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        try {
            if (amapLocation != null) {
                if (amapLocation.getErrorCode() == 0) {
                    //定位成功回调信息，设置相关消息

                    StringBuffer text = new StringBuffer();

                    double latitude = amapLocation.getLatitude();
                    double longitude = amapLocation.getLongitude();
                    double altitude = amapLocation.getAltitude();
                    float speed = amapLocation.getSpeed();
                    float bearing = amapLocation.getBearing();

                    text.append("纬度:" + latitude +"\n");
                    text.append("经度:" + longitude+"\n");
                    text.append("海拔:" + altitude+"\n");
                    text.append("速度:" + speed+"\n");
                    text.append("方向:" + bearing+"\n");

                    //获取定位时间
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date date = new Date(amapLocation.getTime());

                    long time = System.currentTimeMillis();
                    text.append("时间:"+time);
                    locationText.setText(text.toString());

                    String line =
                            time + "," +
                            latitude + "," +
                            longitude + "," +
                                    altitude + "," +
                                    speed + "," +
                                    bearing + "\n";
                    try{
                        if(recoding){
                            fGps.write(line.getBytes());
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }


                    // 停止定位
                    //mlocationClient.stopLocation();
                } else {
                    //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
                    Log.e("AmapError", "location Error, ErrCode:"
                            + amapLocation.getErrorCode() + ", errInfo:"
                            + amapLocation.getErrorInfo());
                }
            }
        } catch (Exception e) {
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 停止定位
        if (null != mlocationClient) {
            mlocationClient.stopLocation();
        }
    }

    /**
     * 销毁定位
     */
    private void destroyLocation() {
        if (null != mlocationClient) {
            /**
             * 如果AMapLocationClient是在当前Activity实例化的，
             * 在Activity的onDestroy中一定要执行AMapLocationClient的onDestroy
             */
            mlocationClient.onDestroy();
            mlocationClient = null;
        }
    }

    @Override
    protected void onDestroy() {
        destroyLocation();
        super.onDestroy();
    }

    private void showToast(String string) {
        Toast.makeText(MainActivity.this, string, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float[] accelerometerValues;
        float[] gyroscopeValues;
        float pressureValues;
        if (sensorEvent.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pressureValues = sensorEvent.values[0];
            qiyaText.setText("气压:\nx:"+pressureValues );
        }
        else if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = sensorEvent.values.clone();
            double curValue = magnitude(accelerometerValues[0], accelerometerValues[1], accelerometerValues[2]);   //计算当前的模

            long time = System.currentTimeMillis();
            String line =
                    time + "," +
                            accelerometerValues[0] + "," +
                            accelerometerValues[1] + "," +
                            accelerometerValues[2] + "\n";
            try{
                if(recoding){
                    fLinear.write(line.getBytes());
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            jiasuduText.setText("加速度:"+curValue);
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeValues = sensorEvent.values.clone();
            long time = System.currentTimeMillis();
            String line =
                    time + "," +
                            gyroscopeValues[0] + "," +
                            gyroscopeValues[1] + "," +
                            gyroscopeValues[2] + "\n";
            try{
                if(recoding){
                    fAngular.write(line.getBytes());
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            tuoluoyiText.setText("陀螺仪:\nx:"+gyroscopeValues[0] + "\ny:" + gyroscopeValues[1] + "\nz:" + gyroscopeValues[2]);
        }
    }

    //向量求模
    public double magnitude(float x, float y, float z) {
        return Math.sqrt(x * x + y * y + z * z);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}