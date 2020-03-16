package com.example.myapplication3;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication3.tools.FileWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;
import java.util.ArrayList;
import java.util.Random;

import devlight.io.library.ArcProgressStackView;

public class RecordActivity extends AppCompatActivity implements SensorEventListener {

    private Context mContext;
    //传感器
    private SensorManager sManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorGyroscope;
    private Sensor mSensorMagnetic; //地磁场传感器

    private TextView infoText;
    private TextView[] countText;
    private TextView jiasuduText;
    private TextView tuoluoyiText;
    private TextView fangxiangText;
    private Button saveButton;
    private RadioGroup rdGroup;
    private RoundCornerProgressBar[] accProgress;
    private RoundCornerProgressBar[] gyrProgress;
    private ArrayList<ArcProgressStackView.Model> models;
    private ArcProgressStackView arcProgressStackView;

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

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
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

        Log.d("===========>","--------------------------------------------------");
        String state = Environment.getExternalStorageState();
        File downFolder = mContext.getExternalFilesDir( Environment.DIRECTORY_DOWNLOADS );
        Log.d("===========>","--------------------------------------------------"+state);
        if(state.equals("mounted")){
            Log.d( "++++++++++>", "state="+ state + ";\nexternalFiles=" + downFolder);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream( new File( downFolder, "测试文件存储能力.txt" ) );
                fileOutputStream.write("甜甜小仙女".getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            showToast("不能使用外部存储目录");
            return;
        }
        Log.d("===========>","--------------------------------------------------");

        fLinear = new FileWriter(mContext);
        fAngular = new FileWriter(mContext);
        fOrientation = new FileWriter(mContext);

        initSensor();
    }

    private void initSensor() {
        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorAccelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorGyroscope = sManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorMagnetic = sManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sManager.registerListener(this, mSensorGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        sManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);

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
        countText = new TextView[5];
        countText[0] = findViewById(R.id.countTv1);
        countText[1] = findViewById(R.id.countTv2);
        countText[2] = findViewById(R.id.countTv3);
        countText[3] = findViewById(R.id.countTv4);
        countText[4] = findViewById(R.id.countTv5);

        saveButton = findViewById(R.id.saveButton);
        rdGroup = findViewById(R.id.radioGroup);
        accProgress = new RoundCornerProgressBar[3];
        gyrProgress = new RoundCornerProgressBar[3];
        accProgress[0] = findViewById(R.id.acc_px);
        accProgress[1] = findViewById(R.id.acc_py);
        accProgress[2] = findViewById(R.id.acc_pz);
        gyrProgress[0] = findViewById(R.id.gyr_px);
        gyrProgress[1] = findViewById(R.id.gyr_py);
        gyrProgress[2] = findViewById(R.id.gyr_pz);

        int bgColors[] = new int[]{Color.GRAY, Color.GRAY, Color.GRAY};
        int mStartColors[] = new int[]{Color.RED, Color.GREEN, Color.BLUE};

        models = new ArrayList<>();
        models.add(new ArcProgressStackView.Model("X", 25, bgColors[0], mStartColors[0]));
        models.add(new ArcProgressStackView.Model("Y", 50, bgColors[1], mStartColors[1]));
        models.add(new ArcProgressStackView.Model("Z", 75, bgColors[2], mStartColors[2]));

        arcProgressStackView = findViewById(R.id.apsv);
        arcProgressStackView.setModels(models);


        for (int i = 0; i < 3; i++) {
            accProgress[i].setMax(25);
            gyrProgress[i].setMax(5); // (+-)0.001 ~ 1.0

            //gyrProgress[i].setProgress((float) 0.3);
        }

        accProgress[1].setMax(100);
        accProgress[0].setProgress(10);
        accProgress[1].setReverse(true);
        accProgress[1].setProgress(-20);

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
                    countText[0].setText("x" + countArray.get("直行"));
                    countText[1].setText("x" + countArray.get("左转"));
                    countText[2].setText("x" + countArray.get("左掉头"));
                    countText[3].setText("x" + countArray.get("右转"));
                    countText[4].setText("x" + countArray.get("右掉头"));

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
            UpdateProgressBar(0, accelerometerValues);
            double curValue = magnitude(accelerometerValues[0], accelerometerValues[1], accelerometerValues[2]);   //计算当前的模
            fLinear.append(accelerometerValues, recording && (runTime > 0));
            jiasuduText.setText("\n加速度:" + Format(curValue, "%04.1f") + "\nx:"+Format(accelerometerValues[0], "%04.1f") + "\ny:" + Format(accelerometerValues[1], "%04.1f") + "\nz:" + Format(accelerometerValues[2], "%04.1f"));
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeValues = sensorEvent.values.clone();
            UpdateProgressBar(1, gyroscopeValues);
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
        UpdateProgressBar(2, angle);

        fOrientation.append(angle, recording && (runTime > 0));

        if(timestamp - lastTimestamp > 1000){
            lastTimestamp = timestamp;
            fangxiangText.setText("\n方向:\nx: " + Math.round(angle[0]) +
                    "\ny: " + Math.round(angle[1]) +
                    "\nz: " + Math.round(angle[2]));
        }
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
                if(!recording) handler.sendEmptyMessage(0);
                //System.out.println("电源键监听");
            }
        }
    };

    private void UpdateProgressBar(int sensor, float[] values){
        switch (sensor){
            case 0:
                setProgressValue(accProgress[0], values[0]);
                setProgressValue(accProgress[1], values[1]);
                setProgressValue(accProgress[2], values[2]);
                break;
            case 1:
                setProgressValue(gyrProgress[0], values[0]);
                setProgressValue(gyrProgress[1], values[1]);
                setProgressValue(gyrProgress[2], values[2]);
                break;
            case 2:
                setProgressValue(0, values[0]);
                setProgressValue(1, values[1]);
                setProgressValue(2, values[2]);
                arcProgressStackView.invalidate();
                break;

        }
    }

    private void setProgressValue(RoundCornerProgressBar progress, float value) {
        if(value < 0){
            //progress.setBackgroundColor(Color.GRAY);
            progress.setReverse(true);
            progress.setProgress(-value);
        }
        else{
            //progress.setBackgroundColor(Color.BLACK);
            progress.setReverse(false);
            progress.setProgress(value);
        }
    }
    private void setProgressValue(int which, float value) {
        models.get(which).setProgress(value*100/360);
    }
}
