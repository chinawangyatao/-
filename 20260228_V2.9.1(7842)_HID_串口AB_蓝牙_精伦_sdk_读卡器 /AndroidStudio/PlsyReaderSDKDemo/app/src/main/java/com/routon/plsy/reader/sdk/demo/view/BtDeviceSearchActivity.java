package com.routon.plsy.reader.sdk.demo.view;

import java.util.ArrayList;
import java.util.List;

import android.Manifest.permission;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.routon.plsy.reader.sdk.demo.R;

public class BtDeviceSearchActivity extends Activity implements OnClickListener, OnItemClickListener, OnScrollListener{
    private EditText etxtFilter;
    private Button btnReSearch;
    private Button btnReturn;
    private ListView mIDList;
    private ArrayAdapter<String> adapter;
    
    private List<String> listData = new ArrayList<String>();
    private List<String> searchData = new ArrayList<String>();
    private LocationManager mLocationMgr;
    private BluetoothAdapter mBtAdapter;
    private Handler uiHandler = new Handler();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_btdevice_search);
        etxtFilter = (EditText) findViewById(R.id.etxtDevFilter);
        btnReSearch = (Button) findViewById(R.id.btnBtDevReSearch);
        btnReturn = (Button) findViewById(R.id.btnReturn);
        btnReSearch.setOnClickListener(this);
        btnReturn.setOnClickListener(this);
        etxtFilter.addTextChangedListener(txtWatcher);
        
        mIDList = (ListView) findViewById(R.id.listIDsInfo);
        
        adapter = new ArrayAdapter<String>(this, R.layout.bt_search_listview_item, R.id.txtSearchListDeviceItem, listData);
        mIDList.setOnScrollListener(this);
        mIDList.setOnItemClickListener(this);
        mIDList.setAdapter(adapter);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        if(Build.VERSION.SDK_INT < 21){
            // android 5.0 以下不支持BLE
        }

        mLocationMgr = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        BluetoothManager btMgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = btMgr.getAdapter();
        if(null == mBtAdapter){
            listData.add("Not support bluetooth.");
            adapter.notifyDataSetInvalidated();
            unregisterReceiver(receiver);
        }else{
            if(Build.VERSION.SDK_INT >= 23){ // 6.0及以上需要动态申请定位权限
                int chkPermission = checkSelfPermission(permission.ACCESS_FINE_LOCATION);
                if(PackageManager.PERMISSION_GRANTED != chkPermission){
                    boolean should = shouldShowRequestPermissionRationale(permission.ACCESS_FINE_LOCATION);
                    if(should){
                        Toast.makeText(this, "需要申请定位权限用于蓝牙搜索", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[]{permission.ACCESS_FINE_LOCATION}, 111);
                }else{
                    onRequestPermissionsResult(111, new String[]{permission.ACCESS_FINE_LOCATION}, new int[]{PackageManager.PERMISSION_GRANTED});
                }
            }else{
                onRequestPermissionsResult(111, new String[]{permission.ACCESS_FINE_LOCATION}, new int[]{PackageManager.PERMISSION_GRANTED});
            }
        }
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(runnableStopScan);
        if(mBtAdapter != null){
            unregisterReceiver(receiver);
            mBtAdapter.cancelDiscovery();
        }
        super.onDestroy();
    }
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(Build.VERSION.SDK_INT >= 23){
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

        if(111 == requestCode){
            if(grantResults!= null && grantResults[0] == PackageManager.PERMISSION_GRANTED){
               startScan(); 
            }else{
                listData.add("request location permission failed.");
                adapter.notifyDataSetInvalidated();
            }
        }
    }
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                int extra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if(BluetoothAdapter.STATE_ON == extra){
                    startScan();
                }else if(BluetoothAdapter.STATE_OFF == extra){
                    btnReSearch.clearAnimation();
                }
            }else if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String devInfo = device.getName() + "\r\n" + device.getAddress();
                if(searchData.contains(devInfo)){
                    return ;
                }
                
                String filter = etxtFilter.getText().toString();
                if(!TextUtils.isEmpty(filter)){
                    if(devInfo.toLowerCase().contains(filter.toLowerCase())){
                        listData.add(devInfo);
                    }
                }else{
                    listData.add(devInfo);
                }
                searchData.add(devInfo);
                adapter.notifyDataSetChanged();
            }
        }
    };
    
    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.btnBtDevReSearch){
            startScan();
        }else if(v.getId() == R.id.btnReturn){
            finish();
        }
    }
    
    private void startScan(){
        if(!mBtAdapter.isEnabled()){
            mBtAdapter.enable();
            return ;
        }

        boolean isLocationOpened = mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if(!isLocationOpened){
            listData.add("请打开位置信息或GPS后重试");
            adapter.notifyDataSetInvalidated();
            return ;
        }
        
        listData.clear();
        searchData.clear();
        adapter.notifyDataSetInvalidated();
        
        mBtAdapter.cancelDiscovery();
        mBtAdapter.startDiscovery();
        
        
        Animation bulletAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        bulletAnimation.setDuration(1300);
        bulletAnimation.setRepeatMode(Animation.RESTART);
        bulletAnimation.setRepeatCount(Animation.INFINITE);
        bulletAnimation.setFillAfter(true);
        btnReSearch.startAnimation(bulletAnimation);
        uiHandler.removeCallbacks(runnableStopScan);
        uiHandler.postDelayed(runnableStopScan, 30000);
    }
    private Runnable runnableStopScan = new Runnable() {
        
        @Override
        public void run() {
            btnReSearch.clearAnimation();
            mBtAdapter.cancelDiscovery();
        }
    };
    
    private TextWatcher txtWatcher = new TextWatcher() {
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
        
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
        
        @Override
        public void afterTextChanged(Editable s) {
            new Handler().post(new Runnable() {
                
                @Override
                public void run() {
                    String filter = etxtFilter.getText().toString();
                    listData.clear();
                    if(!TextUtils.isEmpty(filter)){
                        for(String item:searchData){
                            if(item.toLowerCase().contains(filter.toLowerCase())){
                                listData.add(item);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }else{
                        listData.addAll(searchData);
                    }
                }
            });
        }
    };

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        String []info = listData.get(position).split("\r\n");
        if(info != null && info.length == 2){
            if(BluetoothAdapter.checkBluetoothAddress(info[1])){
                
                BluetoothDevice btDev = mBtAdapter.getRemoteDevice(info[1]);
                if(btDev.getType() == BluetoothDevice.TRANSPORT_LE && Build.VERSION.SDK_INT < 21){
                    Toast.makeText(this, "Android-" + Build.VERSION.SDK_INT + " 不支持BLE设备", Toast.LENGTH_SHORT).show();
                    return ;
                }
                
                Intent data = new Intent();
                data.putExtra("name", info[0]);
                data.putExtra("address", info[1]);
                setResult(Activity.RESULT_OK, data);
                finish();
            }
        }else{
            // Do nothing.
        }
    }
    
    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if(scrollState == OnScrollListener.SCROLL_STATE_IDLE){
             if(mIDList.getLastVisiblePosition() == mIDList.getCount() - 1 ){
             }
        }
    }
    
    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
    }
}
