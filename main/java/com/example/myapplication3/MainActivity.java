package com.example.myapplication3;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.RadioGroup.OnCheckedChangeListener;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * create by zhuyafei at 2020年1月30日23:26:59
 */

/***
 * 高德定位
 */
public class MainActivity extends AppCompatActivity implements AMapLocationListener, SensorEventListener {

    private Context mContext;
    //传感器
    private SensorManager sManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorGyroscope;
    private Sensor mSensorMagnetic; //地磁场传感器

    private static final int MY_PERMISSIONS_REQUEST_CALL_LOCATION = 1;
    public AMapLocationClient mlocationClient;
    public AMapLocationClientOption mLocationOption = null;

    private TextView locationText;
    private TextView jiasuduText;
    private TextView tuoluoyiText;
    private TextView fangxiangText;

    private Button saveButton;

    private RadioGroup rdGroup;

    private float lastTimestamp = 0;

    private float[] accelerometerValues = new float[3];
    private float[] magneticFieldValues = new float[3];
    private float[] gyroscopeValues = new float[3];


    private String curCheck = "init";
    private String curFileName;
    private boolean recoding = false;

    private FileWriter fGps;
    private FileWriter fLinear;
    private FileWriter fAngular;
    private FileWriter fOrientation;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindView();
        initSensor();

        mContext = getApplicationContext();

        fGps = new FileWriter(mContext);
        fLinear = new FileWriter(mContext);
        fAngular = new FileWriter(mContext);
        fOrientation = new FileWriter(mContext);

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
        mSensorMagnetic = sManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sManager.registerListener((SensorEventListener) this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
        sManager.registerListener((SensorEventListener) this, mSensorGyroscope, SensorManager.SENSOR_DELAY_UI);
        sManager.registerListener((SensorEventListener) this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);

        if(mSensorAccelerometer == null){
            jiasuduText.setText("加速度传感器不支持");
        }
        if(mSensorGyroscope == null){
            tuoluoyiText.setText("角速度传感器不支持");
        }
        if(mSensorMagnetic == null){
            fangxiangText.setText("地磁传感器不支持");
        }
    }

    private void bindView() {
        jiasuduText = (TextView)  findViewById(R.id.tv_jiasudu);
        tuoluoyiText = (TextView)  findViewById(R.id.tv_tuoluoyi);
        fangxiangText = (TextView)  findViewById(R.id.tv_fangxiang);

        locationText = (TextView) findViewById(R.id.tv_location);

        saveButton = (Button) findViewById(R.id.saveButton);
        rdGroup = (RadioGroup) findViewById(R.id.radioGroup);


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

                    fGps.create("Gps_" + curCheck);
                    fLinear.create("Linear_" + curCheck);
                    fAngular.create("Angular_" + curCheck);
                    fOrientation.create("Orientation_" + curCheck);
                }
                else{
                    recoding = !recoding;
                    saveButton.setText("开始");
                    rdGroup.setVisibility(View.VISIBLE);

                    fGps.close();
                    fLinear.close();
                    fAngular.close();
                    fOrientation.close();
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
                    double latitude = amapLocation.getLatitude();
                    double longitude = amapLocation.getLongitude();
                    double altitude = amapLocation.getAltitude();
                    float speed = amapLocation.getSpeed();
                    float bearing = amapLocation.getBearing();

                    StringBuffer text = new StringBuffer();
                    text.append("纬度:" + latitude +"\n");
                    text.append("经度:" + longitude+"\n");
                    text.append("速度:" + speed+"\n");
                    text.append("方向:" + bearing+"\n");
                    text.append("时间:"+System.currentTimeMillis());
                    locationText.setText(text.toString());

                    String line = latitude + "," + longitude + "," + altitude + "," + speed + "," + bearing;
                    fGps.append(line, recoding);

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
        if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticFieldValues = sensorEvent.values.clone();
            calculateOrientation(sensorEvent.timestamp);
        }
        else if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = sensorEvent.values.clone();
            double curValue = magnitude(accelerometerValues[0], accelerometerValues[1], accelerometerValues[2]);   //计算当前的模

            fLinear.append(accelerometerValues, recoding);

            jiasuduText.setText("\n加速度:" + Format(curValue, "%04.1f") + "\nx:"+Format(accelerometerValues[0], "%04.1f") + "\ny:" + Format(accelerometerValues[1], "%04.1f") + "\nz:" + Format(accelerometerValues[2], "%04.1f"));
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeValues = sensorEvent.values.clone();
            fAngular.append(gyroscopeValues, recoding);
            tuoluoyiText.setText("\n角速度:"  + "\nx:"+Format(gyroscopeValues[0], "%04.1f") + "\ny:" + Format(gyroscopeValues[1], "%04.1f") + "\nz:" + Format(gyroscopeValues[2], "%04.1f"));
        }
    }

    //向量求模
    public double magnitude(float x, float y, float z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public String Format(double number, String pat){
        return String.format(pat, number);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

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

        fOrientation.append(angle, recoding);

        if(timestamp - lastTimestamp > 1000){
            lastTimestamp = timestamp;
            fangxiangText.setText("\nx: " + Math.round(angle[0]) +
                    "\ny: " + Math.round(angle[1]) +
                    "\nz: " + Math.round(angle[2]));
        }
    }
}