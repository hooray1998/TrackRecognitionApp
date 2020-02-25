package com.example.myapplication3;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication3.tools.FileWriter;

import java.util.HashMap;
import java.util.Map;

public class RecordActivity extends AppCompatActivity implements SensorEventListener {

    private Context mContext;
    //传感器
    private SensorManager sManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorGyroscope;
    private Sensor mSensorMagnetic; //地磁场传感器

    private TextView infoText;
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
    private boolean recording = false;
    private long recordTimestamp ;

    private Map<String, Integer> countArray;
    private long curAppStartTime;

    private FileWriter fLinear;
    private FileWriter fAngular;
    private FileWriter fOrientation;

    private float runTime;
    private MediaPlayer mEnd;
    private MediaPlayer mClock;


    public AudioManager mAudioManager;
    private int maxVolume;
    private int lastVolume;
    private boolean isDestroy;
    private Thread volumeChangeThread;
    private boolean startListenVolume;
    private Handler handler;

    public RecordActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);

        isDestroy = false;
        startListenVolume = false;
        // 获得AudioManager对象
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);//音乐音量,如果要监听铃声音量变化，则改为AudioManager.STREAM_RING
        handler=new Handler(){
            @Override
            public void handleMessage(Message msg) {
                saveButton.performClick();
            }
        };
        //电源键监听
        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        final IntentFilter filter2 = new IntentFilter(Intent.ACTION_SCREEN_ON);
        registerReceiver(mBatInfoReceiver, filter);
        registerReceiver(mBatInfoReceiver, filter2);
        bindView();
        initSensor();
        mEnd = MediaPlayer.create(this, R.raw.shoot);
        mClock = MediaPlayer.create(this, R.raw.ready_run);

        curAppStartTime = System.currentTimeMillis();

        mContext = getApplicationContext();
        countArray = new HashMap();
        countArray.put("直行", 0);
        countArray.put("左转", 0);
        countArray.put("左掉头", 0);
        countArray.put("右转", 0);
        countArray.put("右掉头", 0);

        fLinear = new FileWriter(mContext);
        fAngular = new FileWriter(mContext);
        fOrientation = new FileWriter(mContext);

        /**
         * 动态获取存储权限，Android 6.0 新特性，一些保护权限，除了要在AndroidManifest中声明权限，还要使用如下代码动态获取
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
        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorAccelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorGyroscope = sManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorMagnetic = sManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sManager.registerListener(this, mSensorGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        sManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_FASTEST);

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
        jiasuduText = findViewById(R.id.tv_jiasudu);
        tuoluoyiText = findViewById(R.id.tv_tuoluoyi);
        fangxiangText = findViewById(R.id.tv_fangxiang);

        infoText = findViewById(R.id.tv_info);

        saveButton = findViewById(R.id.saveButton);
        rdGroup = findViewById(R.id.radioGroup);


        rdGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                RadioButton radbtn = findViewById(i);
                curCheck = radbtn.getText().toString();
                saveButton.setText("开始"+curCheck);
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!recording){
                    if(curCheck == "init"){
                        Toast.makeText(getApplicationContext(), "请先选择轨迹类型", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if(!startListenVolume) onVolumeChangeListener();

                    recording = !recording;
                    recordTimestamp = System.currentTimeMillis();
                    saveButton.setText("保存");
                    rdGroup.setVisibility(View.INVISIBLE);
                    mClock.start();

                    String filenameHead = curAppStartTime + "_" + curCheck + "_" + countArray.get(curCheck) + "_";
                    fLinear.create(filenameHead + "Linear");
                    fAngular.create(filenameHead + "Angular");
                    fOrientation.create(filenameHead + "Orientation");
                }
                else{
                    mClock.pause();
                    mClock.seekTo(0);
                    recording = !recording;
                    saveButton.setText("开始"+curCheck);
                    rdGroup.setVisibility(View.VISIBLE);

                    if(!fAngular.empty) {
                        countArray.put(curCheck, countArray.get(curCheck) + 1);
                        mEnd.start();
                    }
                    String countInfo = String.format("直行    : %d\n左转    : %d\n右转    : %d\n左掉头: %d\n右掉头: %d\n",
                            countArray.get("直行"),
                            countArray.get("左转"),
                            countArray.get("右转"),
                            countArray.get("左掉头"),
                            countArray.get("右掉头")
                    );
                    infoText.setText(countInfo);

                    fLinear.close();
                    fAngular.close();
                    fOrientation.close();
                }

            }
        });
    }


    @Override
    protected void onDestroy() {
        isDestroy = true;
        super.onDestroy();
    }

    private void showToast(String string) {
        Toast.makeText(RecordActivity.this, string, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(recording){
            runTime = (float) ((System.currentTimeMillis() - recordTimestamp - 3200)/1000.0);
            if(runTime <= 0){
                if(runTime < -3) runTime = -2; // 防止显示数字4
                saveButton.setText("倒计时: "+ (int)(1 - runTime) + "s");
            }
            else
                saveButton.setText(curCheck + " "+ (int)(runTime) + "s");
        }
        if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticFieldValues = sensorEvent.values.clone();
            calculateOrientation(sensorEvent.timestamp);
        }
        else if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = sensorEvent.values.clone();
            double curValue = magnitude(accelerometerValues[0], accelerometerValues[1], accelerometerValues[2]);   //计算当前的模
            fLinear.append(accelerometerValues, recording && (runTime > 0));
            jiasuduText.setText("\n加速度:" + Format(curValue, "%04.1f") + "\nx:"+Format(accelerometerValues[0], "%04.1f") + "\ny:" + Format(accelerometerValues[1], "%04.1f") + "\nz:" + Format(accelerometerValues[2], "%04.1f"));
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeValues = sensorEvent.values.clone();
            fAngular.append(gyroscopeValues, recording && (runTime > 0));
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

        fOrientation.append(angle, recording && (runTime > 0));

        if(timestamp - lastTimestamp > 1000){
            lastTimestamp = timestamp;
            fangxiangText.setText("方向:\nx: " + Math.round(angle[0]) +
                    "\ny: " + Math.round(angle[1]) +
                    "\nz: " + Math.round(angle[2]));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            home.addCategory(Intent.CATEGORY_HOME);
            startActivity(home);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onVolumeChangeListener()
    {

        volumeChangeThread = new Thread()
        {
            public void run()
            {
                while (!isDestroy)
                {
                    // 监听的时间间隔
                    try
                    {
                        Thread.sleep(100);
                    } catch (InterruptedException e)
                    {
                        infoText.setText("error in onVolumeChangeListener Thread.sleep(20) " + e.getMessage());
                    }
                    int curVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if (curVolume > lastVolume)
                    {
                        if(!recording) handler.sendEmptyMessage(0);
                    }
                    else if (curVolume < lastVolume)
                    {
                        if(recording) handler.sendEmptyMessage(0);
                    }
                    lastVolume = curVolume;

                }
            }
        };
        volumeChangeThread.start();
    }

    private final BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if(Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.i("fasd","电源键++++");
                if(recording) handler.sendEmptyMessage(0);
                //System.out.println("电源键监听");
            }
            if(Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.i("fasd","电源键----");
                if(recording) handler.sendEmptyMessage(0);
                //System.out.println("电源键监听");
            }
        }
    };
}
