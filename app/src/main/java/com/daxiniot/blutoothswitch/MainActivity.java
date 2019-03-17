package com.daxiniot.blutoothswitch;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnLongClickListener, CompoundButton.OnCheckedChangeListener{

    //首先声明sharedPreference与它的editor对象，必须在onCreate函数中实例化
    private SharedPreferences mPreferences;
    //声明sharedPreference的editor
    private SharedPreferences.Editor mEditor;
    private SwitchCompat mSwitch1;
    private SwitchCompat mSwitch2;
    private SwitchCompat mSwitch3;
    private SwitchCompat mSwitch4;
    private TextView mConnectionStatusTv;
    private BluetoothAdapter mBluetoothAdapter ;
    private long mExitTime=0;

    /**
     * 蓝牙通信socket
     */
    private BluetoothSocket mSocket;
    /**
     * 蓝牙设备集合
     */
    private List<MyDevice> mDevices;
    /**
     * 设备列表控件适配器
     */
    private ArrayAdapter<MyDevice> mAdapter;
    /**
     * 蓝牙设备列表对话框
     */
    private AlertDialog mDeviceListDialog;

    /**
     * 广播监听器：负责接收搜索到蓝牙的广播
     */
    private BroadcastReceiver mDeviceFoundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //更新UI
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String name = device.getName();
                String address = device.getAddress();
                boolean bonded = false;
                Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
                //检查设备是否已配对
                for (BluetoothDevice tempDevice : bondedDevices) {
                    if (tempDevice.getAddress().equals(address)) {
                        bonded = true;
                    }
                }
                //刷新设备显示列表
                MyDevice myDevice = new MyDevice();
                myDevice.setName(name);
                myDevice.setAddress(address);
                myDevice.setBonded(bonded);
                mDevices.add(myDevice);
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //获取当前Activity的sharedPreference,onCreate函数之前不能调用getPreference
        mPreferences = getPreferences(Activity.MODE_PRIVATE);
        //创建preference的editor对象
        mEditor = mPreferences.edit();

        mConnectionStatusTv = findViewById(R.id.tv_connection_status);
        TextView txt1 = findViewById(R.id.txt1);
        TextView txt2 = findViewById(R.id.txt2);
        TextView txt3 = findViewById(R.id.txt3);
        TextView txt4 = findViewById(R.id.txt4);
        txt1.setText(mPreferences.getString(Constants.TXT1_NAME_KEY, Constants.TXT1_NAME_VALUE));
        txt2.setText(mPreferences.getString(Constants.TXT2_NAME_KEY, Constants.TXT2_NAME_VALUE));
        txt3.setText(mPreferences.getString(Constants.TXT3_NAME_KEY, Constants.TXT3_NAME_VALUE));
        txt4.setText(mPreferences.getString(Constants.TXT4_NAME_KEY, Constants.TXT4_NAME_VALUE));
        txt1.setOnLongClickListener(this);
        txt2.setOnLongClickListener(this);
        txt3.setOnLongClickListener(this);
        txt4.setOnLongClickListener(this);

        mSwitch1 = findViewById(R.id.switch1);
        mSwitch2 = findViewById(R.id.switch2);
        mSwitch3 = findViewById(R.id.switch3);
        mSwitch4 = findViewById(R.id.switch4);
        mSwitch1.setOnCheckedChangeListener(this);
        mSwitch2.setOnCheckedChangeListener(this);
        mSwitch3.setOnCheckedChangeListener(this);
        mSwitch4.setOnCheckedChangeListener(this);
        //初始化蓝牙设备列表对话框
        initDeviceDialog();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //注册设备发现广播接收器
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mDeviceFoundReceiver, intentFilter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.search_device) {
            startDiscoveryDevice();
        }
        return true;
    }

    /**
     * 初始化蓝牙设备列表对话框
     */
    private void initDeviceDialog() {
        View DialogView = getLayoutInflater().inflate(R.layout.dialog_scan_device, null);
        mDeviceListDialog = new AlertDialog.Builder(MainActivity.this).setView(DialogView).create();
        Button cancelScanBtn = (Button) DialogView.findViewById(R.id.btn_cancel_scan);
        cancelScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDeviceListDialog.dismiss();
            }
        });
        ListView deviceListView = (ListView) DialogView.findViewById(R.id.lvw_devices);
        //初始化蓝牙列表数据
        mDevices = new ArrayList<>();
        mAdapter = new ArrayAdapter<MyDevice>(MainActivity.this,
                android.R.layout.simple_list_item_1, mDevices);
        deviceListView.setAdapter(mAdapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mBluetoothAdapter.cancelDiscovery();
                mDeviceListDialog.dismiss();
                final String address = mDevices.get(i).getAddress();
                final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.show();
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                try {
                    //连接成功
                    mSocket = device.createRfcommSocketToServiceRecord(uuid);
                    mSocket.connect();
                    Toast.makeText(MainActivity.this,"连接成功",Toast.LENGTH_SHORT).show();
                    mConnectionStatusTv.setText("连接状态：已连接");
                } catch (IOException e) {
                    //连接失败
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this,"连接失败",Toast.LENGTH_SHORT).show();
                }
                progressDialog.dismiss();
            }
        });
    }

    /**
     * 开始扫描蓝牙设备
     */
    private void startDiscoveryDevice() {
        mDevices.clear();
        mAdapter.notifyDataSetChanged();
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
        mDeviceListDialog.show();
    }

    /**
     * 向蓝牙模块发送数据
     *
     * @param message 数据
     */
    public void writeData(final String message) {
        new Thread() {
            @Override
            public void run() {
                try {
                    OutputStream os = mSocket.getOutputStream();
                    os.write(message.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    @Override
    public boolean onLongClick(View v) {
        final TextView txtView = (TextView) v;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog, null);
        final EditText txtName = dialogView.findViewById(R.id.et_name);
        Button okButton = dialogView.findViewById(R.id.btn_ok);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
        final AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setView(dialogView)
                .create();
        dialog.show();
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = txtName.getText().toString();
                dialog.dismiss();
                txtView.setText(name);
                switch (txtView.getId()) {
                    case R.id.txt1:
                        mEditor.putString(Constants.TXT1_NAME_KEY, name);
                        break;
                    case R.id.txt2:
                        mEditor.putString(Constants.TXT2_NAME_KEY, name);
                        break;
                    case R.id.txt3:
                        mEditor.putString(Constants.TXT3_NAME_KEY, name);
                        break;
                    case R.id.txt4:
                        mEditor.putString(Constants.TXT4_NAME_KEY, name);
                        break;
                }
                mEditor.commit();

            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        return true;
    }



    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.switch1:
                if (isChecked) {
                    //turn on
                    writeData("1");
                } else {
                    //turn off
                    writeData("2");
                }
                break;
            case R.id.switch2:
                if (isChecked) {
                    //turn on
                    writeData("3");
                } else {
                    //turn off
                    writeData("4");
                }
                break;
            case R.id.switch3:
                if (isChecked) {
                    //turn on
                    writeData("5");
                } else {
                    //turn off
                    writeData("6");
                }
                break;
            case R.id.switch4:
                if (isChecked) {
                    //turn on
                    writeData("7");
                } else {
                    //turn off
                    writeData("8");
                }
                break;
        }
    }

    /**
     * 重写返回按钮方法
     */
    @Override
    public void onBackPressed() {
        //super.onBackPressed();
            if((System.currentTimeMillis()-mExitTime) > 2000){
                Toast.makeText(MainActivity.this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                mExitTime = System.currentTimeMillis();
            } else {
                finish();
                System.exit(0);
            }

    }

    /**
     * 退出应用时注销监听器
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mDeviceFoundReceiver);
    }
}
