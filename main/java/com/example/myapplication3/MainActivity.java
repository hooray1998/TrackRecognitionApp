package com.example.myapplication3;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;


/**
 * create by heliquan at 2017年5月4日23:26:59
 */
public class MainActivity extends AppCompatActivity {

    private Button mapButton;;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
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
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private void initView() {
        mapButton = (Button) findViewById(R.id.mapButton);
    }

}
