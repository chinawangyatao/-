package com.routon.plsy.reader.sdk.demo.view;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.greenrobot.eventbus.EventBus;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.routon.idr.version.iDRVersion;
import com.routon.plsy.reader.sdk.common.ErrorCode;
import com.routon.plsy.reader.sdk.common.Info.ReaderParas;
import com.routon.plsy.reader.sdk.demo.R;
import com.routon.plsy.reader.sdk.demo.model.AppendLogEvent;
import com.routon.plsy.reader.sdk.demo.model.DeviceParamBean;
import com.routon.plsy.reader.sdk.demo.model.ReportReadIDCardEvent;
import com.routon.plsy.reader.sdk.demo.model.ReportStartReadcardResultEvent;
import com.routon.plsy.reader.sdk.demo.presenter.IReaderPresenter;
import com.routon.plsy.reader.sdk.demo.presenter.OnceReadCard;
import com.routon.plsy.reader.sdk.demo.presenter.ReaderPresenter;
import com.routon.plsy.reader.sdk.demo.presenter.SlaveReaderPresenter;
import com.routon.plsy.reader.sdk.intf.IReader;
import com.routon.plsy.reader.sdk.serial.SerialImpl;

/**
 * 
 * @author lihuili
 * 
 *         <p>
 * 		读卡SDK示例程序
 * 
 *         <p>
 * 
 *         <pre>
 * {@code
 * USB读卡器调用流程:
 *  
 *  //初始化USB设备对象
 *  private UsbDevPermissionMgr mUsbPermissionMgr;
 *  private UsbManager mUsbMgr;
 *  mUsbMgr = (UsbManager)mContext.getSystemService(Context.USB_SERVICE);
 *  mUsbPermissionMgr = new UsbDevPermissionMgr(mContext, mUsbCallBack);
 * 	
 *  //检查是否读卡器是否已连接
 *  boolean is_device_connected = mUsbPermissionMgr.init(getContext(), null);
 *  
 *  if(is_device_connected){
 *  	//已经连接读卡器, 打开读卡器
 *  	private IReader mReader;
 *  	mReader = new USBImpl();
 *  	UsbDevice usbDevice = mUsbPermissionMgr.getUsbDevice();
 *  	int ret = mReader.SDT_OpenPort(mUsbMgr, usbDevice);
 * 		if(ret >= ErrorCode.SUCCESS){
 *			//打开设备成功，找卡
 *			byte[] sn = new byte[8];
 * 			ret = mReader.SDT_FindIDCard(sn);                    
 *			if(ret == ErrorCode.SUCCESS){
 *      		//找卡成功，选卡
 *				ret = mReader.SDT_SelectIDCard(sn);
 *    			if(ret == ErrorCode.SUCCESS){
 *          		//选卡成功，读卡
 *       			IDCardInfo iDCardInfo = new IDCardInfo();
 *       			ret = mReader.RTN_ReadBaseMsg(iDCardInfo);
 *       			if (ErrorCode.SUCCESS == ret) {
 *              		//读卡成功，卡的基本信息、照片已经解析并存储到iDCardInfo中
 *       				Log.d("test", "读卡成功");
 *					}
 *   			}
 *			}
 *			
 *			//读卡结束，关闭读卡器
 *			mReader.SDT_ClosePort();
 *			
 *			//释放USB对象
 *			mUsbPermissionMgr.releaseMgr();
 *		}
 * 	}
 * }
 *         </pre>
 *
 */
public class PlsyReaderSDKDemoActivity extends Activity
		implements IReaderView, OnClickListener, OnItemSelectedListener {
	private final String TAG = "PlsyReaderSDKDemoActivity";
	private IReaderPresenter mReaderPresenter;
	private IReaderPresenter mSlaveReaderPresenter;
	private DeviceParamBean devParamBean = new DeviceParamBean();
	// Control begin----------------------------------
	private IDCardView mIDCardView;
	private CheckBox mCheckBoxReadCardCid; // 是否读卡体管理号
	private CheckBox mCheckBoxReadFp; // 是否读指纹
	private CheckBox mCheckBoxReadBCard; // 是否读B卡
	private CheckBox mCheckBoxReadACard; // 是否读A卡
	private CheckBox mCheckBoxDualChannel; // 是否开启双通道
	private CheckBox mCheckBoxUseCache; // 是否启用缓存
	private TextView mTextViewStatus;
	private LinearLayout layoutButtons;
	private LinearLayout layoutUSBManual;
	private LinearLayout layoutURReader;
	private LinearLayout layoutSingleReadButtons;
	private LinearLayout layoutSerialManual;
	private EditText etProgCode;
	private EditText etSerDevname;// 串口设备名
	private EditText etPlatformUrl; // 网络读卡器的平台参数，包括端口号
	private EditText etWriteData;
	private EditText etSectorNo;
	private EditText etSectorCnt;
	private TextView tvModuleStatus;
	private TextView tvConnStatus;
	private TextView tvConnType;
	private TextView tvConnLatency;
	private TextView tvLag;
	private Spinner spinnerConntype;
	private ArrayAdapter<String> mSpinnerAdapterConntype;
	private String[] arrayConntype;
	private Button buttonBack;
	private Button buttonStart;
	private Button buttonStop;
	private Button buttonStartSlave;
	private Button buttonStopSlave;
	private Button buttonOpen;
	private Button buttonChkProgCode;
	private Button buttonFind;
	private Button buttonSelect;
	private Button buttonReadBaseMsg;
	private Button buttonReadNewAppMsg;
	private Button buttonGetSamId;
	private Button buttonTransmit;
	private Button buttonSIMTest;
	private Button buttonClose;
	private Button buttonOnceRead;
	private Button btnBtSearch;
	private Button btnOpenUSReader;
	private Button btnCloseReader;
	private Button buttonOnceReadUS;
	private Button buttonTestLatency;
	private Button buttonTestLag;
	private Button buttonOpenSerial;
	private Button buttonReadCardID;
	private Button buttonWriteData;
	private Button buttonReadData;
	private Button buttonCloseSerial;
	
	private boolean isSingleRead = false;
	private boolean bNetworkIsEnabled = false;
	private TextView txtLog;
	private Spinner spinnerSelectDevice;
	private ArrayAdapter<String> mSpinnerAdapter;
	private String[] arrayDeviceType;
	private boolean isReqUSBPermission = false;
	private int mDeviceType = DeviceParamBean.DEV_TYPE_INNER_OR_USB;
	private final Handler mHandler = new Handler();
	private StringBuilder mNoticeSb = new StringBuilder();
	private IReader mReader;
	private String platformUrl;
	
	private final OnCheckedChangeListener mCheckBoxChangeListener = new OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			// TODO Auto-generated method stub
			if(buttonView == mCheckBoxReadCardCid) {
				mIDCardView.setIDCardCIDViewVisibility(isChecked ? View.VISIBLE : View.GONE);
			}
			
			devParamBean.setRead_fp(mCheckBoxReadFp.isChecked());
			devParamBean.setRead_b(mCheckBoxReadBCard.isChecked());
			devParamBean.setRead_a(mCheckBoxReadACard.isChecked());
			devParamBean.setRead_b_cid(mCheckBoxReadCardCid.isChecked());
			devParamBean.setDual_channel(mCheckBoxDualChannel.isChecked());
			devParamBean.setUse_cache(mCheckBoxUseCache.isChecked());
			mReaderPresenter.setDeviceParam(devParamBean);
		}
		
	};
	
	private HandlerThread mHandlerThread = new HandlerThread("handlerThread");
	private Handler workHandler;
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getResources().getString(R.string.app_name) + IReader.SDKVersion + "("
				+ new iDRVersion().getSvnRevision() + ")");
		setContentView(R.layout.activity_plsy_reader_sdkdemo);

		mIDCardView = (IDCardView) findViewById(R.id.viewIDCardView);
		mTextViewStatus = (TextView) findViewById(R.id.textViewStatus);

		mCheckBoxReadCardCid = (CheckBox) findViewById(R.id.checkbox_readCardCid);
		mCheckBoxReadCardCid.setOnCheckedChangeListener(mCheckBoxChangeListener/*new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				// TODO Auto-generated method stub
				mIDCardView.setIDCardCIDViewVisibility(isChecked ? View.VISIBLE : View.GONE);
			}
		}*/);
		// mCheckBoxReadCardCid.setVisibility(View.GONE);
		mCheckBoxReadFp = (CheckBox) findViewById(R.id.checkbox_readfp);
		mCheckBoxReadBCard = (CheckBox) findViewById(R.id.checkbox_readBCard);
		mCheckBoxReadACard = (CheckBox) findViewById(R.id.checkbox_readACard);
		mCheckBoxDualChannel = (CheckBox) findViewById(R.id.checkbox_dualChannel);
		mCheckBoxUseCache = (CheckBox) findViewById(R.id.checkbox_useCache);
		
		mCheckBoxReadFp.setOnCheckedChangeListener(mCheckBoxChangeListener);
		mCheckBoxReadBCard.setOnCheckedChangeListener(mCheckBoxChangeListener);
		mCheckBoxReadACard.setOnCheckedChangeListener(mCheckBoxChangeListener);
		mCheckBoxDualChannel.setOnCheckedChangeListener(mCheckBoxChangeListener);
		mCheckBoxUseCache.setOnCheckedChangeListener(mCheckBoxChangeListener);
		
		buttonBack = (Button) findViewById(R.id.buttonBack);
		buttonBack.setOnClickListener(this);
		buttonStart = (Button) findViewById(R.id.buttonStart);
		buttonStart.setOnClickListener(this);
		buttonStop = (Button) findViewById(R.id.buttonStop);
		buttonStop.setOnClickListener(this);
		buttonStart.setEnabled(true);
		buttonStop.setEnabled(false);

		buttonStartSlave = (Button) findViewById(R.id.buttonStartSlave);
		buttonStartSlave.setOnClickListener(this);
		buttonStopSlave = (Button) findViewById(R.id.buttonStopSlave);
		buttonStopSlave.setOnClickListener(this);
		buttonStartSlave.setEnabled(true);
		buttonStopSlave.setEnabled(false);
		
		layoutSerialManual = (LinearLayout) findViewById(R.id.layoutSerialManual);
		buttonOpenSerial = (Button) findViewById(R.id.buttonOpenSerial);
		buttonReadCardID = (Button) findViewById(R.id.buttonReadCardID);
		buttonWriteData = (Button) findViewById(R.id.buttonWriteData);
		buttonReadData = (Button) findViewById(R.id.buttonReadData);
		buttonCloseSerial = (Button) findViewById(R.id.buttonCloseSerial);
		etWriteData = (EditText) findViewById(R.id.etWriteData);
		etSectorNo = (EditText) findViewById(R.id.etSectorNo);
		etSectorCnt = (EditText) findViewById(R.id.etSectorCnt);
		buttonOpenSerial.setOnClickListener(this);
		buttonReadCardID.setOnClickListener(this);
		buttonWriteData.setOnClickListener(this);
		buttonReadData.setOnClickListener(this);
		buttonCloseSerial.setOnClickListener(this);

		layoutButtons = (LinearLayout) findViewById(R.id.layoutButtons);
		layoutUSBManual = (LinearLayout) findViewById(R.id.USBManual);
		layoutUSBManual.setVisibility(View.GONE);
		buttonOpen = (Button) findViewById(R.id.buttonOpen);
		buttonOpen.setOnClickListener(this);
		etProgCode = (EditText) findViewById(R.id.etProgCode);
		etSerDevname = (EditText) findViewById(R.id.etSerDevname);
		buttonChkProgCode = (Button) findViewById(R.id.buttonChkProgCode);
		buttonChkProgCode.setOnClickListener(this);
		// buttonOpen.setVisibility(View.GONE);
		buttonFind = (Button) findViewById(R.id.buttonFind);
		buttonFind.setOnClickListener(this);
		// buttonFind.setVisibility(View.GONE);
		buttonSelect = (Button) findViewById(R.id.buttonSelect);
		buttonSelect.setOnClickListener(this);
		// buttonSelect.setVisibility(View.GONE);
		buttonReadBaseMsg = (Button) findViewById(R.id.buttonReadBaseMsg);
		buttonReadBaseMsg.setOnClickListener(this);
		// buttonReadBaseMsg.setVisibility(View.GONE);

		buttonReadNewAppMsg = (Button) findViewById(R.id.buttonReadNewAppMsg);
		buttonReadNewAppMsg.setOnClickListener(this);
		
		buttonGetSamId = (Button) findViewById(R.id.buttonGetSamId);
		buttonGetSamId.setOnClickListener(this);
		buttonTransmit = (Button) findViewById(R.id.buttonTransmit);
		buttonTransmit.setOnClickListener(this);
		buttonSIMTest = (Button) findViewById(R.id.buttonSIMTest);
		buttonSIMTest.setOnClickListener(this);

		buttonClose = (Button) findViewById(R.id.buttonClose);
		buttonClose.setOnClickListener(this);
		// buttonClose.setVisibility(View.GONE);

		layoutSingleReadButtons = (LinearLayout) findViewById(R.id.singleReadButtons);
		layoutSingleReadButtons.setVisibility(View.GONE);
		buttonOnceRead = (Button) findViewById(R.id.buttonOnceRead);
		buttonOnceRead.setOnClickListener(this);
		buttonTestLatency= (Button) findViewById(R.id.buttonTestLatency);
		buttonTestLatency.setOnClickListener(this);
		buttonTestLag = (Button) findViewById(R.id.buttonTestLag);
		buttonTestLag.setOnClickListener(this);
		tvLag = (TextView) findViewById(R.id.tvLag);
		btnBtSearch = (Button) findViewById(R.id.btnBtSearch);
		btnBtSearch.setOnClickListener(this);

		txtLog = (TextView) findViewById(R.id.txtLog);
		txtLog.setMaxLines(16);
		spinnerSelectDevice = (Spinner) findViewById(R.id.spinner_select_device);
		arrayDeviceType = getResources().getStringArray(R.array.devices);
		String[] savedBtDev = getSavedBtDevieInfo();
		if (savedBtDev != null) {
			// lihuili 20210812 在华为荣耀10显示内容太宽,改成只添加蓝牙名称
			// arrayDeviceType[1] += "(" + (savedBtDev[0] + " " + savedBtDev[1])
			// + ")";
			arrayDeviceType[1] += "(" + savedBtDev[0] + ")";
		}
		mSpinnerAdapter = new ArrayAdapter<String>(this, R.layout.bt_search_listview_item, R.id.txtSearchListDeviceItem,
				arrayDeviceType);
		spinnerSelectDevice.setAdapter(mSpinnerAdapter);
		spinnerSelectDevice.setOnItemSelectedListener(this);
		spinnerSelectDevice.setSelection(0);

		layoutURReader = (LinearLayout) findViewById(R.id.NetworkReader);
		layoutURReader.setOnClickListener(this);
		btnOpenUSReader = (Button) findViewById(R.id.buttonOpenUSReader);
		btnOpenUSReader.setOnClickListener(this);
		btnCloseReader = (Button) findViewById(R.id.buttonCloseReader);
		btnCloseReader.setOnClickListener(this);
		etPlatformUrl = (EditText) findViewById(R.id.etPlatform);
		tvModuleStatus = (TextView) findViewById(R.id.tvModuleStatus);
		tvConnStatus = (TextView) findViewById(R.id.tvConnStatus);
		tvConnType = (TextView) findViewById(R.id.tvConnType);
		tvConnLatency = (TextView) findViewById(R.id.tvConnLatency);
		spinnerConntype = (Spinner) findViewById(R.id.spinner_conn_type);
		arrayConntype = getResources().getStringArray(R.array.conntypes);
		mSpinnerAdapterConntype = new ArrayAdapter<String>(this, R.layout.bt_search_listview_item,
				R.id.txtSearchListDeviceItem, arrayConntype);
		spinnerConntype.setAdapter(mSpinnerAdapterConntype);
		spinnerConntype.setOnItemSelectedListener(this);
		spinnerConntype.setSelection(1);
		
		buttonOnceReadUS = (Button) findViewById(R.id.buttonOnceReadUS);
		buttonOnceReadUS.setOnClickListener(this);

		if (mReaderPresenter == null) {
			mReaderPresenter = new ReaderPresenter(this);
		}

		// final Handler handler = new Handler();
		// handler.postDelayed(mRunnableStartReadcard, 5000);
		
		mHandlerThread.start();
		
		workHandler = new Handler(mHandlerThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				switch (msg.what) {
				case 1:
					testLantency();
					break;
				case 2:
					testLag();
					break;
				default:
					break;
				}
			}
		};
		
		if(!isRrocessRunning("cspw")) buttonTestLag.setVisibility(View.INVISIBLE);
	}

	private void saveBtDeviceInfo(String name, String mac) {
		Editor edit = getSharedPreferences("currbluetooth", Context.MODE_PRIVATE).edit();
		edit.putString("btName", name);
		edit.putString("btDevice", mac);
		edit.commit();
	}

	private String[] getSavedBtDevieInfo() {
		SharedPreferences sp = getSharedPreferences("currbluetooth", Context.MODE_PRIVATE);
		String[] btDev = new String[2];
		btDev[0] = sp.getString("btName", "");
		btDev[1] = sp.getString("btDevice", "");
		if (TextUtils.isEmpty(btDev[1])) {
			return null;
		}
		return btDev;
	}

	private final Runnable mRunnableStartReadcard = new Runnable() {
		public void run() {
			if (mDeviceType == DeviceParamBean.DEV_TYPE_INNER_OR_USB) {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "startReadcard");
				// mCheckBoxReadFp.setChecked(true);
				devParamBean.setRead_fp(mCheckBoxReadFp.isChecked());
				devParamBean.setRead_b(mCheckBoxReadBCard.isChecked());
				devParamBean.setRead_a(mCheckBoxReadACard.isChecked());
				devParamBean.setRead_b_cid(mCheckBoxReadCardCid.isChecked());
				devParamBean.setDevice_type(mDeviceType);
				devParamBean.setDual_channel(mCheckBoxDualChannel.isChecked());
				mReaderPresenter.startReadcard(devParamBean);
			} else if (mDeviceType == DeviceParamBean.DEV_TYPE_BT) {
				launchBleDeviceSearch();
			}
			buttonStart.setEnabled(false);
			buttonStop.setEnabled(true);
		}
	};

	private void launchBleDeviceSearch() {
		Intent intent = new Intent();
		intent.setClass(this, BtDeviceSearchActivity.class);
		startActivityForResult(intent, 110);
	}

	private OnceReadCard mOnceReadCard;

	@Override
	public Context getContext() {
		return getApplicationContext();
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		// 退出前要安全释放资源
		uninit();
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		// 如果不是因为请求USB权限导致的Pause，才释放资源
		Log.d(TAG, "onPause isReqUSBPermission?" + isReqUSBPermission);
		if (isReqUSBPermission) {
			isReqUSBPermission = false;
		} else {

		}
		super.onPause();
		
		unregisterNetworkStatus();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		registerNetworkStatus();
	}

	private void uninit() {
		// 退出前要安全释放资源
		if (mReaderPresenter != null) {
			mReaderPresenter.stopReadcard();
			mReaderPresenter.release();
			mReaderPresenter = null;
		}

		if (mSlaveReaderPresenter != null) {
			mSlaveReaderPresenter.stopReadcard();
			mSlaveReaderPresenter.release();
			mSlaveReaderPresenter = null;
		}
		
		if(mHandlerThread != null)
			mHandlerThread.quit();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if (v == buttonBack) {
			if(buttonStop.isEnabled())
				onClick(buttonStop);
			else if(btnCloseReader.isEnabled()) {
				onClick(btnCloseReader);
			}
			//finish();
			System.exit(0);
		} else if (v == buttonStart) {
			buttonStart.setEnabled(false);
			buttonStop.setEnabled(true);
			isSingleRead = false;
			txtLog.setText("");
			devParamBean.setDevice_type(mDeviceType);
			devParamBean.setRead_fp(mCheckBoxReadFp.isChecked());
			devParamBean.setRead_b(mCheckBoxReadBCard.isChecked());
			devParamBean.setRead_a(mCheckBoxReadACard.isChecked());
			devParamBean.setRead_b_cid(mCheckBoxReadCardCid.isChecked());
			devParamBean.setDual_channel(mCheckBoxDualChannel.isChecked());
			devParamBean.setUse_cache(mCheckBoxUseCache.isChecked());
			switch (mDeviceType) {
			case DeviceParamBean.DEV_TYPE_SERIAL:
				devParamBean.setUser_obj(etSerDevname.getText().toString());
			case DeviceParamBean.DEV_TYPE_INNER_OR_USB:
			case DeviceParamBean.DEV_TYPE_USB: {
				mReaderPresenter.startReadcard(devParamBean);
			}
				break;
			case DeviceParamBean.DEV_TYPE_USB_SERIAL:
				spinnerConntype.setEnabled(false);
				//buttonStart.setEnabled(false);
				//tvConnStatus.setText("连接状态：正在连接中");
				devParamBean.setTransferType(spinnerConntype.getSelectedItemPosition());
				/*if(spinnerConntype.getSelectedItemPosition() == 1 && !bNetworkIsEnabled) {
					appendLog(AppendLogEvent.LOG_CODE_ANY, "请先连接设备的网络");
					return;
				}*/
					
				mReaderPresenter.startReadcard(devParamBean);
				break;
			case DeviceParamBean.DEV_TYPE_BT: {
				String[] savedBtDev = getSavedBtDevieInfo();
				if (savedBtDev != null) {
					final BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(savedBtDev[1]);
					devParamBean.setUser_obj(device);
					mReaderPresenter.startReadcard(devParamBean);
				} else {
					launchBleDeviceSearch();
				}
			}
				break;
			default:
				break;
			}
		} else if (v == buttonStop) {
			mReaderPresenter.stopReadcard();
			updateStatus(getText(R.string.readcard_hint).toString());
			buttonStart.setEnabled(true);
			buttonStop.setEnabled(false);
			btnOpenUSReader.setEnabled(true);
		} else if (v == buttonOnceRead) {
			OnceReadCard onceReadCard = new OnceReadCard();
			onceReadCard.setReaderView(this);
			onceReadCard.ReadCard(this);
		} else if (v == buttonOpen) {
			if (mOnceReadCard != null) {
				mOnceReadCard.CloseDevice();
			}
			mOnceReadCard = new OnceReadCard();
			mOnceReadCard.setReaderView(this);
			mOnceReadCard.OpenDevice(getContext());
		} else if (v == buttonChkProgCode) {
			if (mOnceReadCard != null) {
				mOnceReadCard.CheckProgramCode(etProgCode.getText().toString());
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
			// mReaderPresenter.sendCmd(IReaderPresenter.CMD_FIND);
		} else if (v == buttonFind) {
			if (mOnceReadCard != null) {
				mOnceReadCard.FindIDCard();
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
			// mReaderPresenter.sendCmd(IReaderPresenter.CMD_FIND);
		} else if (v == buttonSelect) {
			if (mOnceReadCard != null) {
				mOnceReadCard.SelectIDCard();
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
			// mReaderPresenter.sendCmd(IReaderPresenter.CMD_SELECT);
		} else if (v == buttonReadBaseMsg) {
			if (mOnceReadCard != null) {
				mOnceReadCard.ReadBaseMsg();
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
			// mReaderPresenter.sendCmd(IReaderPresenter.CMD_READBASEMSG);
		} else if (v == buttonReadNewAppMsg) {
			if (mOnceReadCard != null) {
				mOnceReadCard.ReadNewAppMsg();
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
			// mReaderPresenter.sendCmd(IReaderPresenter.CMD_READBASEMSG);
		} else if (v == buttonClose) {
			if (mOnceReadCard != null) {
				mOnceReadCard.CloseDevice();
				mOnceReadCard = null;
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
		} else if (v == btnBtSearch) {
			launchBleDeviceSearch();
		} else if (v == buttonStartSlave) {
			switch (mDeviceType) {
			case DeviceParamBean.DEV_TYPE_SERIAL:
			case DeviceParamBean.DEV_TYPE_USB: {
				mSlaveReaderPresenter = new SlaveReaderPresenter(this);

				devParamBean.setDevice_type(mDeviceType);
				devParamBean.setUser_obj(etSerDevname.getText().toString());
				// 通通不允许,因为从通道是被动接受读卡信息
				devParamBean.setRead_fp(false);
				devParamBean.setRead_b(false);
				devParamBean.setRead_a(false);
				devParamBean.setRead_b_cid(false);
				devParamBean.setDual_channel(false);

				mSlaveReaderPresenter.startReadcard(devParamBean);
				buttonStartSlave.setEnabled(false);
				buttonStopSlave.setEnabled(true);
			}
				break;
			default:
				appendLog(AppendLogEvent.LOG_CODE_ANY, "从设备必须指定为串口或USB");
				break;
			}
		} else if (v == buttonStopSlave) {
			if (mSlaveReaderPresenter != null) {
				mSlaveReaderPresenter.stopReadcard();
				mSlaveReaderPresenter = null;
				buttonStartSlave.setEnabled(true);
				buttonStopSlave.setEnabled(false);
			}
		} else if (v == btnOpenUSReader) {
			/*if(spinnerConntype.getSelectedItemPosition() == 1 && !bNetworkIsEnabled) {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先连接设备的网络");
				return;
			}*/
			isSingleRead = true;
			Log.d(TAG, "btnOpenUSReader click");
			
			if (mOnceReadCard != null) {
				mOnceReadCard.CloseDevice();
			}
			
			devParamBean.setDevice_type(mDeviceType);
			devParamBean.setRead_fp(mCheckBoxReadFp.isChecked());
			devParamBean.setRead_b(mCheckBoxReadBCard.isChecked());
			devParamBean.setRead_a(mCheckBoxReadACard.isChecked());
			devParamBean.setRead_b_cid(mCheckBoxReadCardCid.isChecked());
			devParamBean.setTransferType(spinnerConntype.getSelectedItemPosition());
			devParamBean.setUse_cache(mCheckBoxUseCache.isChecked());
			
			mOnceReadCard = new OnceReadCard();
			mOnceReadCard.setReaderView(this);
			btnOpenUSReader.setEnabled(false);
			if(mOnceReadCard.OpenUSDevice(getContext(), devParamBean)) {
				/*buttonOnceReadUS.setEnabled(true);
				btnCloseReader.setEnabled(true);
				buttonStart.setEnabled(false);
				buttonStop.setEnabled(false);*/
			}
		} else if (v == btnCloseReader) {
			if (mOnceReadCard != null) {
				if(mOnceReadCard.CloseDevice()) {
					btnOpenUSReader.setEnabled(true);
					buttonOnceReadUS.setEnabled(false);
					btnCloseReader.setEnabled(false);
					buttonStart.setEnabled(true);
					buttonStop.setEnabled(true);
				}
				mOnceReadCard = null;
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
		} else if (v == buttonOnceReadUS) {
			if (mOnceReadCard != null) {
				devParamBean.setRead_fp(mCheckBoxReadFp.isChecked());
				
				mOnceReadCard.ReadCard(devParamBean);
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
		} else if(v == buttonTestLatency) {
			buttonTestLatency.setEnabled(false);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Message message = Message.obtain();
					message.what = 1;
					workHandler.sendMessage(message);
				}
			});
		} else if (v == buttonGetSamId) {
			if (mOnceReadCard != null) {
				mOnceReadCard.getSamId();
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
		} else if (v == buttonTransmit) {
			if (mOnceReadCard != null) {
				mOnceReadCard.transmit();
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
		} else if(v == buttonSIMTest) {
			if (mOnceReadCard != null) {
				mOnceReadCard.testSuperSIMCard();
			} else {
				appendLog(AppendLogEvent.LOG_CODE_ANY, "请先打开读卡器");
			}
		} else if(v == buttonTestLag) {
			buttonTestLag.setEnabled(false);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Message message = Message.obtain();
					message.what = 2;
					workHandler.sendMessage(message);
				}
			});
		} else if(v == buttonOpenSerial) {
			mReader = new SerialImpl();
			mReader.setDebug(true);
			mReader.setUsingCache(false);
			int ret = mReader.SDT_OpenPort(etSerDevname.getText().toString(), 9600);
			Log.d(TAG, "DEV_TYPE_SERIAL SDT_OpenPort ret=" + ret);

			if (ret == ErrorCode.SUCCESS) {
				buttonOpenSerial.setEnabled(false);
				buttonReadCardID.setEnabled(true);
				buttonWriteData.setEnabled(true);
				buttonReadData.setEnabled(true);
				buttonCloseSerial.setEnabled(true);
			}
		} else if(v == buttonReadCardID) {
			byte[] id = new byte[8];
			int ret = mReader.RTN_TypeAReadCID(id);
			if (ret > 0) {
				etWriteData.setText(toHexStringNoSpace(id, ret));
			} else {
				etWriteData.setText("读卡ID失败");
			}
		} else if(v == buttonWriteData) {
			byte[] data = hexString2Bytes(etWriteData.getText().toString());
			if(data != null) {
				int ret = mReader.RTN_ISO15693WriteSector(Byte.parseByte(etSectorNo.getText().toString()), Byte.parseByte(etSectorCnt.getText().toString()), data);
				if (ret == ErrorCode.SUCCESS) {
					etWriteData.setText("写入数据成功");
				}
			}
		} else if(v == buttonReadData) {
			byte[] data = new byte[256];
			int ret = mReader.RTN_ISO15693ReadSector(Byte.parseByte(etSectorNo.getText().toString()), Byte.parseByte(etSectorCnt.getText().toString()), data);
			if (ret > 0) {
				etWriteData.setText(toHexStringNoSpace(data, ret));
			} else {
				etWriteData.setText("读取数据失败：" + ret);
			}
		} else if(v == buttonCloseSerial) {
			if(mReader != null) mReader.SDT_ClosePort();
			
			buttonOpenSerial.setEnabled(true);
			buttonReadCardID.setEnabled(false);
			buttonWriteData.setEnabled(false);
			buttonReadData.setEnabled(false);
			buttonCloseSerial.setEnabled(false);
		}
	}
	
	private void testLantency() {
		if(platformUrl == null || platformUrl.length() == 0) platformUrl = "idcard.wanlogin.com";
		String delay = new String();
		String loss = new String();
		Process process = null;
		try {
			process = Runtime.getRuntime().exec("/system/bin/ping -c 4 " + platformUrl);
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String str = new String();
			while((str = bufferedReader.readLine()) != null) {
				//Log.d(TAG, "testLantency:" + str);
				if(str.contains("avg")) {
					int i = str.indexOf("/", 20);
					int j = str.indexOf("/", i + 1);
					delay = str.substring(i + 1, j);
				} else if(str.contains("packets")) { //4 packets transmitted, 4 received, 0% packet loss, time 3006ms
					int i = str.indexOf("received,");
					int j = str.indexOf(" packet ");
					loss = str.substring(i + 9, j);
				}
			}
			
			final String ms = delay.isEmpty() ? "网络未连接" : delay + "ms," + loss + " loss";
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if(ms.length() > 0)
						tvConnLatency.setText("网络延迟：" + ms);
					else
						tvConnLatency.setText("网络延迟：");
					
					buttonTestLatency.setEnabled(true);
				}
			});
		} catch (IOException e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	private void testLag() {
		devParamBean.setUser_obj(etSerDevname.getText().toString());
		devParamBean.setDevice_type(mDeviceType);
		devParamBean.setRead_fp(mCheckBoxReadFp.isChecked());
		devParamBean.setRead_b(mCheckBoxReadBCard.isChecked());
		devParamBean.setRead_a(mCheckBoxReadACard.isChecked());
		devParamBean.setRead_b_cid(mCheckBoxReadCardCid.isChecked());
		devParamBean.setUse_cache(mCheckBoxUseCache.isChecked());
		
		mOnceReadCard = new OnceReadCard();
		mOnceReadCard.setReaderView(this);
		
		final byte[] latency = mOnceReadCard.testCspwLag(devParamBean);
		
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if(latency != null) {
					tvLag.setVisibility(View.VISIBLE);
					if(latency[0] >= 3) {
						tvLag.setText("测试通过");
					} else {
						tvLag.setText("测试不通过，等级：" + latency[0]);
					}
				} else {
					tvLag.setText("测试失败，请检查网络连接");
				}
				
				buttonTestLag.setEnabled(true);
			}
		});
		
		
	}
	
	private boolean isRrocessRunning(String processName) {
		ActivityManager activityManager = (ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);
		List<ActivityManager.RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
		for(ActivityManager.RunningAppProcessInfo info : processInfos){
			if(info.processName.equalsIgnoreCase(processName)) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (110 == requestCode && resultCode == Activity.RESULT_OK) {
			String name = data.getStringExtra("name");
			String address = data.getStringExtra("address");
			saveBtDeviceInfo(name, address);
			// lihuili 20210812 在华为荣耀10显示内容太宽,改成只添加蓝牙名称
			arrayDeviceType[1] = "蓝牙 (" + name + ")";
			// arrayDeviceType[1] = "蓝牙 (" + (name + " " + address) + ")";
			mSpinnerAdapter.notifyDataSetChanged();
			if (buttonStart.isEnabled()) {
				onClick(buttonStart);
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void updateStatus(String status) {
		mTextViewStatus.setText(status);
	}

	@Override
	public void onReadIDCardSuccessed(ReportReadIDCardEvent event) {
		// TODO Auto-generated method stub
		if (event != null) {
			/*if (event.getiDCardInfo() != null) {
				Log.d(TAG, "name is " + event.getiDCardInfo().name);
			}*/
			mIDCardView.updateIDCardInfo(event.getiDCardInfo(), event.getCid(), true);
			updateStatus("读卡成功");
		}
	}

	@Override
	public void onReadIDCardFailed() {
		// TODO Auto-generated method stub
		updateStatus("读卡失败");
	}

	@Override
	public void onCardRemoved() {
		// TODO Auto-generated method stub
		mIDCardView.updateIDCardInfo(null, null, false);
		updateStatus("卡已离开,请放卡");
	}

	private String getDate() { // 得到的是一个日期：格式为：yyyy-MM-dd HH:mm:ss.SSS
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		return sdf.format(new Date());// 将当前日期进行格式化操作
	}

	private void writeDevInfo(String devinfo, String writePath) {
		File file = new File(writePath);
		if (file.exists()) {
			file.delete();
		}
		try {
			FileOutputStream fos = new FileOutputStream(writePath);
			fos.write(devinfo.getBytes());
			fos.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void appendLog(int code, String log) {
		// TODO Auto-generated method stub
		// Log.d(TAG, "appendLog code is " + code + ";log is " + log);
		if (code == AppendLogEvent.LOG_CODE_SAMID) {
			// String termsn = DeviceInfo.getTermSnMd5();
			String samId = log.replace("-", "").replace(".", "");
			samId = samId.substring(0, 22);
			// String devinfo = termsn + "," + samId + "\n";
			String devinfo = samId + "\n";
			//writeDevInfo(devinfo, Environment.getExternalStorageDirectory().getPath() + "/devinfo.txt");
		} else if (code == AppendLogEvent.LOG_CODE_REQ_USB_PERMISSION) {
			isReqUSBPermission = true;
		}

		//String notice = txtLog.getText().toString();
		// 首次输出,直接填充
		if (TextUtils.isEmpty(mNoticeSb)) {
			//notice += getDate() + ":" + log;
			mNoticeSb.append(getDate()).append(':').append(log);
		} else {
			if (mNoticeSb.substring((getDate() + ":").length()).startsWith(log)) {
				return;
			}
			//notice = getDate() + ":" + log + "\n" + notice;
			mNoticeSb.insert(0, getDate() + ":" + log + "\n");
			//String[] lines = notice.split("\n");
			
			//int num = lines.length;
			while (mNoticeSb.toString().split("\n").length > 15) {
				//notice = notice.substring(0, notice.lastIndexOf("\n"));
				//mNoticeSb.subSequence(start, end)
				mNoticeSb = mNoticeSb.delete(mNoticeSb.lastIndexOf("\n") - 1, mNoticeSb.length() - 1);
			}
		}

		//txtLog.setText(notice);
		
		txtLog.setText(mNoticeSb.toString());
		txtLog.scrollTo(0, 0);
	}

	@Override
	public void onGotStartReadcardResult(int error_code, boolean has_inner_reader) {
		// TODO Auto-generated method stub
		String errMsg = "onGotStartReadcardResult " + error_code;
		if(error_code <= ErrorCode.ErrCodeSerial_E_UNSUPPORTED_READ_MODE) {
			switch (error_code) {
			case ErrorCode.ErrCodeSerial_E_UNSUPPORTED_READ_MODE:
				errMsg += "，不支持的串口读卡模式";
				break;
			case ErrorCode.ErrCodeSerial_E_AUTH_CODE_INVALID:
				errMsg += "，授权码无效";
				break;
			case ErrorCode.ErrCodeSerial_E_AUTH_CODE_BE_USED:
				errMsg += "，授权码已被使用";
				break;
			case ErrorCode.ErrCodeSerial_E_AUTH_CODE_BE_TAKED:
				errMsg += "，授权码已被占用";
				break;
			case ErrorCode.ErrCodeSerial_E_AUTH_CODE_EXPIRED:
				errMsg += "，授权码已过期";
				break;
			case ErrorCode.ErrCodeSerial_E_AUTH_TIMEOUT:
				errMsg += "，授权认证超时";
				break;
			case ErrorCode.ErrCodeSerial_E_SOFTWARE_NEED_UPDATE:
				errMsg += "，读卡软件需要升级";
				break;
			case ErrorCode.ErrCodeSerial_E_CONNECT_FAILED:
				errMsg += "，连接服务器失败";
				break;
			default:
				break;
			}
			
		}
		appendLog(AppendLogEvent.LOG_CODE_ANY, errMsg);
		if (error_code >= ErrorCode.SUCCESS) {
			updateStatus("请放卡");
			mCheckBoxUseCache.setEnabled(false);
			if(spinnerSelectDevice.getSelectedItemPosition() == 4) {
				if(isSingleRead) {
					buttonStart.setEnabled(false);
					buttonStop.setEnabled(false);
					btnOpenUSReader.setEnabled(false);
					buttonOnceReadUS.setEnabled(true);
					btnCloseReader.setEnabled(true);
				} else {
					buttonStart.setEnabled(false);
					buttonStop.setEnabled(true);
					btnOpenUSReader.setEnabled(false);
					buttonOnceReadUS.setEnabled(false);
					btnCloseReader.setEnabled(false);
				}
				
			} else {
				buttonStart.setEnabled(false);
				buttonStop.setEnabled(true);
			}
		} else if(error_code == ErrorCode.ErrCodeSerial_E_CONNECT_FAILED) {		//iDR610-G
			updateStatus("连接中断，无法读卡");
			buttonStart.setEnabled(false);
			buttonStop.setEnabled(true);
		} else {
			updateStatus("启动读卡失败(" + error_code + ")");
			onClick(buttonStop);
		}
	}

	@Override
	public void onReadACardSuccessed(byte[] card_sn) {
		// TODO Auto-generated method stub
		if (card_sn != null) {
			mIDCardView.updateACardInfo(card_sn, true);
			updateStatus("读A卡成功");
		}
	}

	@Override
	public void onReaderStopped() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onReaderStopped:停止成功");
		// onClick(buttonStop);
		buttonStart.setEnabled(true);
		buttonStop.setEnabled(false);

		mCheckBoxUseCache.setEnabled(true);
		updateStatus("读卡停止");
		tvConnStatus.setText("连接状态：服务器断开连接");
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		// TODO Auto-generated method stub
		if(parent.getId() == R.id.spinner_select_device) {
			layoutUSBManual.setVisibility(View.GONE);
			layoutSingleReadButtons.setVisibility(View.GONE);
			layoutSerialManual.setVisibility(View.GONE);
			btnBtSearch.setVisibility(View.GONE);
			etSerDevname.setVisibility(View.GONE);
			layoutURReader.setVisibility(View.GONE);
			buttonStartSlave.setVisibility(View.VISIBLE);
			buttonStopSlave.setVisibility(View.VISIBLE);
			buttonOnceReadUS.setVisibility(View.GONE);
			mCheckBoxReadACard.setVisibility(View.VISIBLE);
			mCheckBoxDualChannel.setVisibility(View.VISIBLE);
			mCheckBoxReadACard.setEnabled(true);
			mCheckBoxDualChannel.setEnabled(true);
			mDeviceType = position;
			switch (position) {
			case DeviceParamBean.DEV_TYPE_INNER_OR_USB: {
				layoutUSBManual.setVisibility(View.VISIBLE);
				layoutSingleReadButtons.setVisibility(View.VISIBLE);
			}
				break;
			case DeviceParamBean.DEV_TYPE_BT: {
				btnBtSearch.setVisibility(View.VISIBLE);
			}
				break;
			case DeviceParamBean.DEV_TYPE_SERIAL: {
				etSerDevname.setVisibility(View.VISIBLE);
				layoutSerialManual.setVisibility(View.VISIBLE);
			}
				break;
			case DeviceParamBean.DEV_TYPE_USB_SERIAL: {
				layoutURReader.setVisibility(View.VISIBLE);
				buttonStartSlave.setVisibility(View.GONE);
				buttonStopSlave.setVisibility(View.GONE);
				
				btnOpenUSReader.setVisibility(View.VISIBLE);
				buttonOnceReadUS.setVisibility(View.VISIBLE);
				btnCloseReader.setVisibility(View.VISIBLE);
				
				buttonOnceReadUS.setEnabled(false);
				btnCloseReader.setEnabled(false);
				
				mCheckBoxReadACard.setChecked(false);
				mCheckBoxReadACard.setEnabled(false);
				mCheckBoxDualChannel.setChecked(false);
				mCheckBoxDualChannel.setEnabled(false);
			}
				break;
			default:
				break;
			}
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
		// TODO Auto-generated method stub
	}

	private String toHexStringNoSpace(byte[] buffer, int length) {

		String bufferString = "";
		int len = length;
		if (buffer != null) {
			if (len > buffer.length) {
				len = buffer.length;
			}
		}

		for (int i = 0; i < len; i++) {

			String hexChar = Integer.toHexString(buffer[i] & 0xFF);
			if (hexChar.length() == 1) {
				hexChar = "0" + hexChar;
			}

			bufferString += hexChar.toUpperCase();
		}

		return bufferString;
	}
	
	private static int hex2Dec(char hexChar) {
        if (hexChar >= '0' && hexChar <= '9') {
            return hexChar - '0';
        } else if (hexChar >= 'A' && hexChar <= 'F') {
            return hexChar - 'A' + 10;
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * hexString转byteArr
     * <p>例如：</p>
     * hexString2Bytes("00A8") returns { 0, (byte) 0xA8 }
     *
     * @param hexString 十六进制字符串
     * @return 字节数组
     */
    public static byte[] hexString2Bytes(String hexString) {
        if (hexString.isEmpty()) return null;
        int len = hexString.length();
        if (len % 2 != 0) {
            hexString = "0" + hexString;
            len = len + 1;
        }
        char[] hexBytes = hexString.toUpperCase().toCharArray();
        byte[] ret = new byte[len >> 1];
        for (int i = 0; i < len; i += 2) {
            ret[i >> 1] = (byte) (hex2Dec(hexBytes[i]) << 4 | hex2Dec(hexBytes[i + 1]));
        }
        return ret;
    }

	@Override
	public void updateParasUI(ReaderParas paras) {
		// TODO Auto-generated method stub
		if(paras == null) {
			etPlatformUrl.setText(null);
			tvConnStatus.setText("连接状态：无设备");
			tvConnType.setText("连接类型：无设备");
			tvModuleStatus.setText("Cat1状态：无设备");
			return;
		}

		if(paras.port > 0) {
			buttonTestLatency.setEnabled(true);
			this.platformUrl = paras.platformIP;
			
			etPlatformUrl.setText(paras.platformUrl);
		} else {
			etPlatformUrl.setText(null);
		}
			
		String connStatus = "连接状态：";
		switch (paras.isConnected) {
		case 0:
			connStatus += "服务器连接正常";
			break;
		case 1:
			connStatus += "服务器断开连接";
			break;
		case 2:
			connStatus += "正在连接中";
			break;
		case 3:
			connStatus += "未配置授权信息";
			break;
		case 4:
			connStatus += "登录无效";
			break;
		case 5:
			connStatus += "正在升级";
			break;
		case 6:
			connStatus += "服务器通信超时";
			break;
		case 7:
			connStatus += "设备已在用";
			break;
		case 8:
			connStatus += "有效期过期";
			break;
		case 97:
			connStatus += "服务器断开连接";
			break;
		case 98:
			connStatus += "正在升级";
			break;
		case 99:
			connStatus += "未知错误";
			break;
		case 100:
			connStatus += "服务器连接失败";
			break;
		default:
			connStatus += "未知";
			break;
		}
		tvConnStatus.setText(connStatus);
		
		switch (paras.connectType) {
		case 1:
			tvConnType.setText("连接类型：USB");
			break;
		case 2:
			tvConnType.setText("连接类型：CAT1");
			break;
		default:
			break;
		}
		switch (paras.cat1Status) {
		case -1:
			tvModuleStatus.setText("Cat1状态：无模块");
			break;
		case 0:
			tvModuleStatus.setText("Cat1状态：正常");
			break;
		case 1:
			tvModuleStatus.setText("Cat1状态：SIM卡出错");
			break;
		case 2:
			tvModuleStatus.setText("Cat1状态：信号弱");
			break;
		case 3:
			tvModuleStatus.setText("Cat1状态：联网出错");
			break;
		default:
			tvModuleStatus.setText("Cat1状态：未知");
			break;
		}
	}
	
	private Object networkCallback = null;
	
	private boolean isEnableToNetwork(Context context, NetworkCapabilities networkCapabilities) {
		if(Build.VERSION.SDK_INT < 24) {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if(cm == null) return false;
				
			NetworkInfo networkInfo = cm.getActiveNetworkInfo();
			return networkInfo != null && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI || networkInfo.getType() == ConnectivityManager.TYPE_MOBILE);
		} else {
			return networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
					&& networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
		}
	}
	
	private void registerNetworkStatus() {
		if(Build.VERSION.SDK_INT > 17) {
			Context context = getContext();
			if(context != null) {
				ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				networkCallback = new ConnectivityManager.NetworkCallback() {
					@Override
					public void onCapabilitiesChanged(Network network, final NetworkCapabilities networkCapabilities) {
						super.onCapabilitiesChanged(network, networkCapabilities);
						
						mHandler.post(new Runnable() {
							
							@Override
							public void run() {
								// TODO Auto-generated method stub
								if(isEnableToNetwork(getContext(), networkCapabilities)) {
									bNetworkIsEnabled = true;
								} else {
									bNetworkIsEnabled = false;
								}
							}
						});
					}
				};
				connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), (ConnectivityManager.NetworkCallback)networkCallback);
			}
		}
		
	}
	
	private void unregisterNetworkStatus() {
		Context context = getContext();
		if(context != null && networkCallback != null) {
			ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			connectivityManager.unregisterNetworkCallback((ConnectivityManager.NetworkCallback)networkCallback);
		}
	}
}