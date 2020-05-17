package com.example.myapplication3;

import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication3.tools.FileWriter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;

public class ProcessActivity extends AppCompatActivity {
    private Button startButton;
    private TextView infoText;
    private ScrollView scrollView;
    private RouteView canvas;
    private final double scale =  0.3; //比例 1step -> 画布上2.5个单位长度
    private float lastDegree = 0; //初始的轨迹偏转角


    private long startTime;

    // 先读取方向传感器,更新到
    private float[] oneSecondDegreeArr;
    private int oneSecondDegreeLen;
    private long oneSecondDegreeEndTime;

    private float[] degreeArr;
    private int degreeArrSize;

    // 再读取加速度数据,得到每秒累计的步数,以及每秒最大的加速度值,用来判断是否停止了
    private double[] topArr;
    private double topMaxValue; //每秒最大的g值
    private int[] stepArr;
    private int stepArrSize;
    private int curStep;
    private float lastStepValue;
    private long lastStepTime; // 上一次记录步数的时间,用来判断两步之间的时间间隔要大于0.3s
    private boolean lastStepUp;

    // 用来判断停止的时间区间
    private int waitStopIndex;
    private boolean lastIsStop;//上一秒是否在直行区间内(不是前五秒)
    private int stopLen;//当前秒是否在直行(>=5),以及直行了多少秒了(总是大于等于5),因为5s内不算一段有效的直行
    private int stopCount;
    private int[] stopArr;
    private int stopArrSize;

    //直行区间总是>=5s
    private int waitIndex; // 监听的时刻,如果当前秒不监听,则跳过
    private boolean lastIsGo;//上一秒是否在直行区间内(不是前五秒)
    private int goLen;//当前秒是否在直行(>=5),以及直行了多少秒了(总是大于等于5)
    private int goCount;
    private int goDistance;

    // 方向判断和步数判断的一些常量
    private final int MinStopTime       = 3    ; //最短的停止有效时间,单位秒
    private final double stopScore        = 0.6  ; //MinStopTime 秒的时间内要有超过 3x0.6s的秒数停止=>这三秒算作有效的停止区间
    private final int MinStepTime     = 300  ; //每一圈之间的最短间隔,单位毫秒ms
    private final double MinStepValue = 10.5 ; //最小的峰值
    private final int MinGoTime       = 5    ; //最短的直行有效时间,单位秒s
    private final int limit           = 25   ; //直行偏差在均值的+-15度以内
    private final double score        = 0.8  ; //5s中要有80%的点符合limit
    private final int MaxSize = 216000;//6hx60x60 = 36000x6 = 216000s  数组的最大长度

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_process);
        bindView();


        oneSecondDegreeArr = new float[100];
        degreeArr = new float[MaxSize];
        stepArr = new int[MaxSize];
        topArr = new double[MaxSize];
        stopArr = new int[MaxSize];
    }

    public void bindView(){
        startButton = findViewById(R.id.startButton);
        infoText = findViewById(R.id.infoText);
        scrollView = findViewById(R.id.scrollView);
        canvas = findViewById(R.id.canvas);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseFile();
            }
        });

    }

    private static final String TAG1 = "FileChoose";
    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Choose File"), CHOOSE_FILE_CODE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "亲，木有文件管理器啊-_-!!", Toast.LENGTH_SHORT).show();
        }
    }

    private static final int CHOOSE_FILE_CODE = 0;

    @Override
// 文件选择完之后，自动调用此函数
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == CHOOSE_FILE_CODE) {
                Uri uri = data.getData();
                String path = getPath(getApplicationContext(), uri);
                Toast.makeText(this, "文件路径"+path+"###", Toast.LENGTH_SHORT).show();
                predictRoute(path);
                updateScroll();
            }
        } else {
            Log.e(TAG1, "onActivityResult() error, resultCode: " + resultCode);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }



    public void initVariable() {
        lastDegree = 0;
        canvas.size = 0;

        topMaxValue = 0;
        lastStepTime = -MinStepTime;
        lastStepUp = false;
        lastStepValue = 10000;
        oneSecondDegreeLen = 0;
        waitIndex = MinGoTime;
        lastIsGo = false;

        waitStopIndex = MinStopTime;
        lastIsStop = false;
        stopLen = 0;
        stopCount = 0;

        curStep = 0;
        degreeArrSize = 0;
        stepArrSize = 0;
        goLen = 0;
        goCount = 0;
        goDistance = 0;

        stopArrSize = 0;
    }

    public void predictRoute(String pathname){
        infoText.append("\nStart:" + pathname + "    ==========");
        initVariable();
        readOrientFile(pathname);
        readLinearFile(pathname);
        //让数据对齐,两种数组的大小一致
        if(stepArrSize > degreeArrSize) stepArrSize = degreeArrSize;
        else degreeArrSize = stepArrSize;//防止出现错误

        //统计停顿的时间
        for(int i = 0;i < degreeArrSize; i++){
            cleanData(i);
        }
        unionStopArr();

        updateArray();

        // 去除前
        for(int i = 0;i < degreeArrSize; i++){
            updateRoute(i);
        }
        // 去除后

        infoText.append(String.format("\n%s-%s(%.1f分钟)" , new SimpleDateFormat("[MM-dd]HH:mm:ss").format(startTime), new SimpleDateFormat("HH:mm:ss").format(oneSecondDegreeEndTime),(float)(oneSecondDegreeEndTime-startTime)/60000));
        infoText.append(String.format("\nGo%d %d 米", goCount, goDistance));
        infoText.append("\nEnd: =====================================");

    }

    public void readOrientFile(String pathname) {
        pathname = pathname.replace("Linear.txt", "Orientation.txt");
        File downFolder = getApplicationContext().getExternalFilesDir( Environment.DIRECTORY_DOWNLOADS );
        try {
            FileInputStream fileInputStream = new FileInputStream( new File(pathname) );

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            String line = null;
            while((line = bufferedReader.readLine()) != null)
            {
                String[] data = line.split(",");
                float[] values = new float[3];
                values[0] = Float.parseFloat(data[1]);
                values[1] = Float.parseFloat(data[2]);
                values[2] = Float.parseFloat(data[3]);
                appendCurSecondDegree(Long.parseLong(data[0]), values);
            }
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void readLinearFile(String pathname) {
        pathname = pathname.replace("Orientation.txt", "Linear.txt");
        File downFolder = getApplicationContext().getExternalFilesDir( Environment.DIRECTORY_DOWNLOADS );
        try {
            FileInputStream fileInputStream = new FileInputStream( new File(pathname) );

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            String line = null;
            while((line = bufferedReader.readLine()) != null)
            {
                String[] data = line.split(",");
                float[] values = new float[3];
                values[0] = Float.parseFloat(data[1]);
                values[1] = Float.parseFloat(data[2]);
                values[2] = Float.parseFloat(data[3]);

                float g = mang(values);
                updateStep(Long.parseLong(data[0]), g);
            }
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void updateStep(long t, float g) {
        boolean Up = (g > lastStepValue);
        // 初始化当前秒的结束时间
        if(stepArrSize==0) oneSecondDegreeEndTime = startTime + 1000;
        //NOTE: 极值点判定
        if(lastStepUp && (!Up)){
            //NOTE: 时间和赋值判定
            if((t-lastStepTime)>MinStepTime && lastStepValue>MinStepValue){
                lastStepTime = t;
                curStep += 1;
            }
        }

        // 当前这秒时间结束,将上一秒的数据更新
        if(t >= oneSecondDegreeEndTime){
            // 追加top数组和step数组, top用来判断停止的时间段
            topArr[stepArrSize] = topMaxValue;
            stepArr[stepArrSize++] = curStep;
            oneSecondDegreeEndTime += 1000;
            topMaxValue = 0;
        }

        // 更新当前秒的最大值
        if(g>topMaxValue) topMaxValue = g;
        lastStepUp = Up;
        lastStepValue = g;
    }


    public void appendCurSecondDegree(long curTime, float[] oriValues) {
        if(degreeArrSize==0 && oneSecondDegreeLen==0){
            startTime = curTime;
            oneSecondDegreeEndTime = curTime + 1000;
        }else if(curTime >= oneSecondDegreeEndTime){
            degreeArr[degreeArrSize++] = averageOneSecond();
            oneSecondDegreeEndTime += 1000;
            oneSecondDegreeLen = 0;
        }
        oneSecondDegreeArr[oneSecondDegreeLen++] = oriValues[0];
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

    public float diffDegree(float a, float b){
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

    public float averageOneSecond(){
        float sum = 0;
        for(int i=0;i<oneSecondDegreeLen;i++){
            sum += transform(oneSecondDegreeArr[0], oneSecondDegreeArr[i]);
        }
        if(oneSecondDegreeLen == 0) return 0;
        return sum/oneSecondDegreeLen;
    }

    /**
     * 计算end索引前面的size个点的平均值，不包括end
     *
     * @param end 结束的点，计算时不包含
     * @param size 点的个数
     * @return 返回平均值
     */
    public float averageDegreeArr(int end, int size) {
        if(end < size){
            infoText.append("\nerror in averageDegreeArr " + end + "," + size);
            return 0;
        }
        float sum = 0;
        for(int i=end-size;i<end;i++){
            sum += transform(degreeArr[end-size], degreeArr[i]);
        }
        return sum/size;
    }

    public boolean judgeStop() {
        int yes = 0;
        for(int i=waitStopIndex-MinStopTime;i<waitStopIndex;i++){
            if(topArr[i] < MinStepValue){
                yes += 1;
            }
        }
        return yes/MinStopTime >= stopScore;
    }

    /**
     * 判断当前秒为终点的五秒的区间是否是直的
     * @return true/false
     */
    public boolean judgeStraight() {
        float average = averageDegreeArr(waitIndex, MinGoTime);
        int yes = 0;
        for(int i=waitIndex-MinGoTime;i<waitIndex;i++){
            if(Math.abs(diffDegree(degreeArr[i], average)) <= limit){
                yes += 1;
            }
        }
        return yes/MinGoTime >= score;
    }

    //TODO: 使用独立的变量,防止下面的步骤乱掉
    public void cleanData(int size) {
        if(waitStopIndex != size){
            return;
        }
        //NOTE:此时waitStopIndex 等于 size ，也就是最后一个(当前处理的)点的索引+1
        boolean curIsStop = false;
        curIsStop = judgeStop();

        if(curIsStop && lastIsStop){
            stopLen += 1;
        }else if(curIsStop){
            stopLen = MinStopTime;
            //当直行中断后的几秒并不需要判断,直接跳过,所以waitStopIndex + 4
        }else if(lastIsStop){
            stopArr[stopArrSize] = waitStopIndex-stopLen-1;
            stopArr[stopArrSize+1] = waitStopIndex-1;
            stopArrSize += 2;
            stopCount += 1;
            waitStopIndex += MinStopTime-1;
        }

        lastIsStop = curIsStop;
        waitStopIndex += 1;
    }

    /**
     * 每有了1s的角度数据后就判断是否在直行区间
     */
    public void updateRoute(int size) {
        //如果结束了，上一次还是直行的话也要输出
        //
        //思路:
        //1. 等有了五秒数据的时候开始判断
        //2. 判断刚刚五秒是否有4个点在平均分附近,因为刚开始,所以直行记录直行了五秒钟golen
        //3. 继续看第六秒
        //   如果刚刚5s是直行,那么以该点为终点的5s判断是否是直的
        //          如果当前也直,那么len=6
        //          如果当前不直,那么len=5or6.7.8 结束并输出刚刚直行的长度,并且之后的三秒也不需要判断,因为如果算了,那么就跟刚刚的直行连接上了,那也就不算中断了
        //   如果刚刚不直,现在也不直,那就跳过
        //   如果现在5s直了,那就记录len=5
        if(waitIndex != size){
            return;
        }
        //NOTE:此时waitIndex 等于 size ，也就是最后一个(当前处理的)点的索引+1
        boolean curIsGo = false;
        curIsGo = judgeStraight();

        if(curIsGo && lastIsGo){
            goLen += 1;
        }else if(curIsGo){
            goLen = MinGoTime;
            //当直行中断后的几秒并不需要判断,直接跳过,所以waitIndex + 4
        }else if(lastIsGo){
            float average = averageDegreeArr(waitIndex-1, goLen);
            int distance =  diffStep(waitIndex-1, goLen) * 2; //一圈为2米
            if(distance>30){
                infoText.append(String.format("\n%3d~%3ds | %3dm | %5.1f° | %3d | %2.1f m/s\n",waitIndex-goLen-1, waitIndex-1, distance, average, goLen, (float)distance/goLen));
                updateRouteCanvas(average, distance);
            }
            goCount += 1;
            goDistance += distance;
            waitIndex += MinGoTime-1;
        }

        lastIsGo = curIsGo;
        waitIndex += 1;
    }

    private int diffStep(int end, int size) {
        if(end >= degreeArrSize)
            end = degreeArrSize - 1;
        if(end < size){
            infoText.append("\nerror in diffStep " + end + "," + size);
            size = end;
        }
        return stepArr[end] - stepArr[end-size];
    }
    private float mang(float[] values){
        return (float)Math.sqrt(values[0]*values[0] + values[1]*values[1] + values[2]*values[2]);
    }

    /**
     * 合并较相近的停顿片段
     * 比如1-20s是停顿,22-50s也是停顿,会通过向两边增长20%的判断相连的方式将他们合并
     * stopArr刚开始存储的是clean算法得出的停顿区间
     * 合并之后的区间也放到了stopArr中了
     */
    private void unionStopArr(){
        int start = 0;
        int end = 0;
        double len = 0;
        double last = -100; //上一段区间延伸之后达到的距离

        int newStopSize = -2;
        for (int i = 0; i < stopArrSize; i+=2) {
            start = stopArr[i];
            end = stopArr[i+1];
            len = (end - start);
            if ((start - len*0.2) <= last) {
                stopArr[i] = stopArr[i-2];
                len = stopArr[i+1] - stopArr[i];
                stopArr[newStopSize + 1] = end;
            }
            else{
                newStopSize += 2;
                stopArr[newStopSize] = start;
                stopArr[newStopSize + 1] = end;
            }
            last = stopArr[i+1] + len * 0.2;
        }
        stopArrSize = newStopSize + 2;

        for (int i = 0; i < stopArrSize; i+=2) {
            start = stopArr[i];
            end = stopArr[i+1];
            infoText.append(String.format("\n%d~%ds | %d\n", start,end , end - start));
        }
    }

    private void updateArray(){
        for (int i = 0; i < stepArrSize; i++) {
        }
        // step数组删除的方法
        for (int i = stopArrSize - 2; i >= 0; i-=2) {

            int start = stopArr[i];
            int end = stopArr[i+1] + 1;
            int diff = end - start;

            int stepDiff = stepArr[end] - stepArr[start];
            stepArrSize -= diff;
            // 删除step
            for (int j = start; j < stepArrSize; j++) {
                stepArr[j] = stepArr[j+diff] - stepDiff;
            }

            //删除degree
            degreeArrSize -= diff;
            for (int j = start; j < degreeArrSize; j++) {
                degreeArr[j] = degreeArr[j+diff];
            }

        }
        for (int i = 0; i < stepArrSize; i++) {
        }
    }

    public void updateRouteCanvas(float curDegree, float distance){
        //infoText.append("\npaint=========>:" + curDegree + "," + distance);
        float changeDegress = diffDegree(curDegree, lastDegree);
        lastDegree = curDegree;
        canvas.appendLine(changeDegress, (float) (distance * scale));
    }




    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
    public void updateScroll(){
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
}
