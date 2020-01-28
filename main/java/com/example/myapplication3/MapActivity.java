package com.example.myapplication3;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;

public class MapActivity extends Activity {

    private MapView mapView;

    private AMap aMap;

    @Override
    protected void onResume() {
        super.onResume();
        // 重新绘制加载地图
        mapView.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        initView();
        // 创建地图
        mapView.onCreate(savedInstanceState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停地图的绘制
        mapView.onPause();
    }


    /**
     * 重写此方法，在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
     *
     * @param outState
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    private void initView() {
        // 实例化地图控件
        mapView = (MapView) findViewById(R.id.id_gaode_location_map);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 销毁地图
        mapView.onDestroy();
    }
}
