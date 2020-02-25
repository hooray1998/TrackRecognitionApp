package com.example.myapplication3;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/**
 * create by zhuyafei at 2020年1月30日23:26:59
 */

/***
 * 高德定位
 */
public class MainActivity extends AppCompatActivity {

    private Context mContext;

    private Button recordButton;
    private Button predictButton;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindView();

        mContext = getApplicationContext();

    }


    private void bindView() {

        recordButton = findViewById(R.id.recordButton);
        predictButton = findViewById(R.id.predictButton);

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, RecordActivity.class);
                startActivity(intent);
            }
        });

        predictButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, PredictActivity.class);
                startActivity(intent);
            }
        });


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}