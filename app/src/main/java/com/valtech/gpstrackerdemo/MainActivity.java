package com.valtech.gpstrackerdemo;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends Activity implements ServiceCallbacks {
    private MapView mMapView = null;
    private BaiduMap mBaiduMap;
    private LocationClient mLocationClient = null;
    private BDLocationListener listener = new MyLocationListener();
    private Button goBack;
    private Button inputLocation;
    private BDLocation location;

    private final int REQUEST_LOCATION_PERMISSION = 1;
    private final int REQUEST_PHONESTATE_PERMISSION = 1;

    private MyMqttService myMqttService;
    private boolean bound = false;

    List<LatLng> points = new ArrayList();

    @Override
    protected void onStart() {
        super.onStart();
        // bind to Service
        Intent intent = new Intent(this, MyMqttService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from service
        if (bound) {
            myMqttService.setCallbacks(null); // unregister
            unbindService(serviceConnection);
            bound = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocationClient = new LocationClient(this);
        mLocationClient.registerLocationListener(listener);

        //获取地图控件引用
        mMapView = (MapView) findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(new MapStatus.Builder().zoom(16.0f).build()));//默认缩放

        requestLocationPermission();
        requestPhoneStatePermission();

        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);

        //开启连接MQTT服务
        MyMqttService.startService(this);

        //定位的方法
        findLocation();

        goBack = (Button) findViewById(R.id.goBack);
        location = new BDLocation();
        goBack.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                findLocation();
            }
        });

        inputLocation = (Button) findViewById(R.id.inputLocation);
        inputLocation.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                openInputLocationDlg(MainActivity.this);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mLocationClient.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @AfterPermissionGranted(REQUEST_LOCATION_PERMISSION)
    public void requestLocationPermission() {
        String[] perms = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
        if(EasyPermissions.hasPermissions(this, perms)) {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
        else {
            EasyPermissions.requestPermissions(this, "Please grant the location permission", REQUEST_LOCATION_PERMISSION, perms);
        }
    }

    @AfterPermissionGranted(REQUEST_PHONESTATE_PERMISSION)
    public void requestPhoneStatePermission() {
        String[] perms = {Manifest.permission.READ_PHONE_STATE};
        if(EasyPermissions.hasPermissions(this, perms)) {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
        else {
            EasyPermissions.requestPermissions(this, "Please grant the phone state permission", REQUEST_PHONESTATE_PERMISSION, perms);
        }
    }

    //定位方法封装
    public void findLocation() {
        if (mLocationClient.isStarted()) {
            mLocationClient.stop();
        }
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true);// 打开gps
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(0);
        mLocationClient.setLocOption(option);
        // 开始定位
        mLocationClient.start();
    }

    public void openInputLocationDlg(Activity activity) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.input_location_dialog);

        final EditText et = dialog.findViewById(R.id.et);
        et.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText et2 = dialog.findViewById(R.id.et2);
        et2.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Button btnok = (Button) dialog.findViewById(R.id.btnok);
        btnok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double longitudeVal = Double.valueOf(et.getText().toString());
                Double latitudeVal = Double.valueOf(et2.getText().toString());

                MapStatus.Builder builder = new MapStatus.Builder();
                LatLng latLng = new LatLng(latitudeVal, longitudeVal);
                builder.target(latLng).zoom(16.0f);
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));

                MyLocationData locData = new MyLocationData.Builder()
                        .accuracy(location.getRadius())
                        .direction(100).latitude(latitudeVal)
                        .longitude(longitudeVal).build();
                mBaiduMap.setMyLocationData(locData);

                dialog.dismiss();
            }
        });

        Button btncn = (Button) dialog.findViewById(R.id.btncn);
        btncn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    class MyLocationListener implements BDLocationListener {

        @Override
        public void onReceiveLocation(BDLocation location) {
            // TODO Auto-generated method stub

            // map view 销毁后不在处理新接收的位置
            if (location == null || mMapView == null) {
                return;
            }

            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    .direction(100).latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .build();

            mBaiduMap.setMyLocationData(locData);

            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
            MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
            mBaiduMap.animateMapStatus(u);
            points.add(ll);
        }
    }

    @Override
    public void drawTrack(Double longitudeVal, Double latitudeVal) {
        System.out.println("== In activity == 经度 = " + longitudeVal);
        System.out.println("== In activity == 纬度 = " + latitudeVal);
        LatLng ll = new LatLng(latitudeVal, longitudeVal);

        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(location.getRadius())
                .direction(100).latitude(latitudeVal)
                .longitude(longitudeVal).build();
        mBaiduMap.setMyLocationData(locData);

        points.add(ll);
        //设置折线的属性
        OverlayOptions mOverlayOptions = new PolylineOptions()
                .width(10)
                .color(0xAAFF0000)
                .points(points);
        //在地图上绘制折线
        //mPloyline 折线对象
        Overlay mPolyline = mBaiduMap.addOverlay(mOverlayOptions);

    }

    /** Callbacks for service binding, passed to bindService() */
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // cast the IBinder and get MyService instance
            MyMqttService.LocalBinder binder = (MyMqttService.LocalBinder) service;
            myMqttService = binder.getService();
            bound = true;
            myMqttService.setCallbacks(MainActivity.this); // register
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };

}
