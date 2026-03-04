package com.routon.plsy.reader.sdk.demo.presenter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.greenrobot.eventbus.EventBus;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.routon.plsy.device.sdk.DeviceInfo;
import com.routon.plsy.device.sdk.DeviceModel;
import com.routon.plsy.device.sdk.SystemProperties;
import com.routon.plsy.reader.sdk.ble.BleImpl;
import com.routon.plsy.reader.sdk.bt.BTImpl;
import com.routon.plsy.reader.sdk.ccid.CCIDImpl;
import com.routon.plsy.reader.sdk.common.Common;
import com.routon.plsy.reader.sdk.common.CommonImpl;
import com.routon.plsy.reader.sdk.common.ErrorCode;
import com.routon.plsy.reader.sdk.common.Info;
import com.routon.plsy.reader.sdk.common.Info.IDCardInfo;
import com.routon.plsy.reader.sdk.common.Info.MoreAddrInfo;
import com.routon.plsy.reader.sdk.common.Info.ReaderParas;
import com.routon.plsy.reader.sdk.common.Info.SAMIDInfo;
import com.routon.plsy.reader.sdk.common.ReaderType;
import com.routon.plsy.reader.sdk.demo.model.AppendLogEvent;
import com.routon.plsy.reader.sdk.demo.model.DeviceParamBean;
import com.routon.plsy.reader.sdk.demo.model.ReportCardRemovedEvent;
import com.routon.plsy.reader.sdk.demo.model.ReportReadACardEvent;
import com.routon.plsy.reader.sdk.demo.model.ReportReadIDCardEvent;
import com.routon.plsy.reader.sdk.demo.model.ReportReadcardThreadExitEvent;
import com.routon.plsy.reader.sdk.demo.model.ReportStartReadcardResultEvent;
import com.routon.plsy.reader.sdk.demo.presenter.UsbDevPermissionMgr.UsbDevPermissionMgrCallback;
import com.routon.plsy.reader.sdk.demo.view.IReaderView;
import com.routon.plsy.reader.sdk.intf.IReader;
import com.routon.plsy.reader.sdk.serial.SerialImpl;
import com.routon.plsy.reader.sdk.usb.USBImpl;
import com.routon.plsy.reader.sdk.usb.USBSerialReader;
import com.routon.plsy.reader.sdk.usb.USBSerialReader.ReaderCallback;

/**
 * 实现读卡任务的核心类
 * 
 * @author lihuili
 * 
 *         <br>
 * 		读卡SDK调用流程 <br>
 * 		1. 打开读卡器：请参考processInit()了解打开蓝牙读卡器/智能终端内置读卡器/外接USB读卡器的用法 <br>
 * 		2.
 *         读卡：请参考ReadBCardThread了解仅读B卡的调用方法；请参考ReadABCardThread了解同时读A卡和B卡的调用方法
 *         <br>
 * 		3. 关闭读卡器：请参考postStopReadCard()了解关闭读卡器的调用方法 <br>
 * 		<em> 注意:<br>
 *         1.
 *         如果是USB读卡器，初始化时需要申请USB权限，退出需要释放注册的广播。可参考UsbDevPermissionMgr.java<br>
 *         2. 如果是蓝牙读卡器，需要先搜索到蓝牙设备，再打开读卡器。<br>
 *         </em>
 *
 */
public class ReaderTask {
	private final String TAG = "ReaderTask";
	private static ReaderTask INSTANCE = null;
	private String mMcuVer = "";
	private final int DEV_TYPE_SERIAL = 0;
	private final int DEV_TYPE_BT = 1;
	private final int DEV_TYPE_BLE = 2;
	private final int DEV_TYPE_USB_HID = 3;
	private final int DEV_TYPE_USB_BB = 4;
	private final int DEV_TYPE_USB_CCID = 5;
	private final int DEV_TYPE_USB_SERIAL_HID = 6;
	private final int DEV_TYPE_USB_SERIAL_BB = 7;
	
	private final int READER_MODE_LOCAL = 0;
	private final int READER_MODE_NETWORK = 2;
	private final int READER_MODE_LOCAL_WITHOUT_MCU = 3;
	
	private int mDevType = DEV_TYPE_SERIAL;
	private int mDevCaps = 0;

	public static ReaderTask create(Context context, ReadIDCardMode mode) {
		if (null == INSTANCE) {
			INSTANCE = new ReaderTask(context, mode);
		}
		INSTANCE.setContext(context);
		INSTANCE.setReadIDCardMode(mode);
		return INSTANCE;
	}

	public enum ReadIDCardMode {
		/** 读一次就退出 */
		Single,
		/** 每次读完卡，不立即找卡，等卡片放置较长时间或卡片离开再开始找下一张卡 */
		loop,
		/** 一直不停的找卡选卡读卡 */
		continual
		// cmd //按命令执行读卡操作
	}

	private boolean is_quit = false;
	private Context mContext;
	private ReadIDCardMode mReadIDCardMode;

	public void setContext(Context context) {
		this.mContext = context;
	}

	public void setReadIDCardMode(ReadIDCardMode readIDCardMode) {
		this.mReadIDCardMode = readIDCardMode;
	}

	public void setReader(IReader reader) {
		this.mReader = reader;
	}
	
	private IReaderView mReaderView;
	private IReader mReader;
	private ReaderParas mUSReaderParas;
	private DeviceParamBean mDeviceParamBean;
	private int mDeviceType = DeviceParamBean.DEV_TYPE_UNKNOWN;
	private ReadIDCardMode readCardMode;
	private HandlerThread threadAsyncHandler;
	private Handler handlerAsyncTask;
	private UsbDevPermissionMgr mUsbPermissionMgr;
	private UsbManager mUsbMgr;

	private final int DEF_SERIAL_BAUDRATE = 115200;
	private final String DEF_SERIAL_PORT_NAME_14T = "/dev/ttyS4";
	private final String DEF_SERIAL_PORT_NAME_iDR420 = "/dev/ttyS4";
	private final String DEF_SERIAL_PORT_NAME_iDR500_1 = "/dev/ttyMT1";
	private final String DEF_SERIAL_PORT_NAME_iDR420_1 = "/dev/ttyMT1";// lihuili
																		// add
																		// 20170829
																		// for
																		// iDR420-1

	private Handler uiHandler = new Handler(Looper.getMainLooper());
	private Runnable runnable = new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			if(mReaderView != null) mReaderView.updateParasUI(mUSReaderParas);
			//uiHandler.postDelayed(this, 4000);
		}
	};
	
	/**
	 * 不同的设备串口名称不同
	 * 
	 * @return
	 */
	private String getPortname() {
		DeviceModel mDevModel = DeviceInfo.getDeviceModel();
		if (mDevModel.equals(DeviceModel.iDR420)) {
			return DEF_SERIAL_PORT_NAME_iDR420;
		} else if (mDevModel.equals(DeviceModel.iDR500_1)) {
			return DEF_SERIAL_PORT_NAME_iDR500_1;
		} else if (mDevModel.equals(DeviceModel.CI14T) || mDevModel.equals(DeviceModel.RK3288CI14TG)) {
			return DEF_SERIAL_PORT_NAME_14T;
		} else if (mDevModel.equals(DeviceModel.iDR420_1)) {
			return DEF_SERIAL_PORT_NAME_iDR420_1;
		} else {
			return DEF_SERIAL_PORT_NAME_iDR420;
		}
	}
	
	private ReaderTask(Context context, ReadIDCardMode mode) {
		mContext = context;
		mReadIDCardMode = mode;
	}

	/**
	 */
	public void init() {
		threadAsyncHandler = new HandlerThread("ReaderHandler");
		threadAsyncHandler.start();
		handlerAsyncTask = new ReaderAsyncHandler(threadAsyncHandler.getLooper(), mReader);
		readCardMode = mReadIDCardMode;

		mUsbMgr = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
		mUsbPermissionMgr = new UsbDevPermissionMgr(mContext, mUsbCallBack);
	}

	public void release() {
		if (threadAsyncHandler != null && threadAsyncHandler.getLooper() != null
				&& threadAsyncHandler.getLooper().getThread().isAlive()) {
			Message msg = handlerAsyncTask.obtainMessage(MSG_NOTIFY_QUIT);
			handlerAsyncTask.sendMessage(msg);

		} else {
			Log.e(TAG, "threadAsyncHandler is unavailable");
		}
	}

	public void startReadcard(DeviceParamBean devParamBean) {
		if (threadAsyncHandler != null && threadAsyncHandler.getLooper() != null
				&& threadAsyncHandler.getLooper().getThread().isAlive()) {
			Message msg = handlerAsyncTask.obtainMessage(MSG_START_READCARD);
			msg.obj = devParamBean;
			handlerAsyncTask.sendMessage(msg);
		}
	}
	
	public void setDeviceParam(DeviceParamBean devParamBean) {
		this.mDeviceParamBean = devParamBean;
	}

	public void stopReadcard() {
		if (threadAsyncHandler != null && threadAsyncHandler.getLooper() != null
				&& threadAsyncHandler.getLooper().getThread().isAlive()) {
			Message msg = handlerAsyncTask.obtainMessage(MSG_STOP_READCARD);
			handlerAsyncTask.sendMessage(msg);
		}
	}
	
	public void setReaderView(IReaderView readerView) {
		mReaderView = readerView;
	}

	public void sendCmd(int cmd) {
		if (threadAsyncHandler != null && threadAsyncHandler.getLooper() != null
				&& threadAsyncHandler.getLooper().getThread().isAlive()) {
			Message msg = handlerAsyncTask.obtainMessage(MSG_SEND_CMD);
			msg.obj = cmd;
			handlerAsyncTask.sendMessage(msg);
		}
	}

	public void onReaderCardThreadQuited() {
		if (threadAsyncHandler != null && threadAsyncHandler.getLooper() != null
				&& threadAsyncHandler.getLooper().getThread().isAlive()) {
			Message msg = handlerAsyncTask.obtainMessage(MSG_READCARD_THREAD_QUITED);
			handlerAsyncTask.sendMessage(msg);
		}
	}

	private UsbDevPermissionMgrCallback mUsbCallBack = new UsbDevPermissionMgrCallback() {
		public void onUsbDevReady(UsbDevice device) {
			if (threadAsyncHandler != null && threadAsyncHandler.getLooper() != null
					&& threadAsyncHandler.getLooper().getThread().isAlive()) {
				Message msg = handlerAsyncTask.obtainMessage(MSG_USB_DEV_ATTACHED, device);
				handlerAsyncTask.sendMessage(msg);
			}
		}

		public void onUsbDevRemoved(UsbDevice device) {
			if (threadAsyncHandler != null && threadAsyncHandler.getLooper() != null
					&& threadAsyncHandler.getLooper().getThread().isAlive()) {
				Message msg = handlerAsyncTask.obtainMessage(MSG_USB_DEV_DETACHED, device);
				handlerAsyncTask.sendMessage(msg);
			}
		}

		public void onNoticeStr(String notice) {
			AppendLogEvent appendLogEvent = new AppendLogEvent();
			appendLogEvent.setLog(notice);
			EventBus.getDefault().post(appendLogEvent);
		}

		@Override
		public void onUsbRequestPermission() {
			// TODO Auto-generated method stub
			AppendLogEvent appendLogEvent = new AppendLogEvent();
			appendLogEvent.setCode(AppendLogEvent.LOG_CODE_REQ_USB_PERMISSION);
			appendLogEvent.setLog("To request permission");
			EventBus.getDefault().post(appendLogEvent);
		}
	};
	private final int MSG_CHECK_INNER_READER = 1;
	private final int MSG_USB_DEV_ATTACHED = 2;
	private final int MSG_USB_DEV_DETACHED = 3;
	private final int MSG_NOTIFY_QUIT = 4; // 被view层通知退出
	private final int MSG_READCARD_THREAD_QUITED = 5; // 线程停止后汇报
	private final int MSG_START_READCARD = 6;
	private final int MSG_STOP_READCARD = 7;
	private final int MSG_SEND_CMD = 8;

	private class ReaderAsyncHandler extends Handler {
		private final String TAG = "ReaderAsyncHandler";
		private final int READER_ST_INIT = 0;
		private final int READER_ST_WORK = 1;
		private int mReader_st = READER_ST_INIT;

		private ReadCardThread threadReadCard;
		private UsbDevice waitingDevice = null;
		private IReader mReader;
		private String mBtName;
		
		private final ReaderCallback mUSReaderCallback = new ReaderCallback() {
			
			@Override
			public void updateReaderStatus(ReaderParas paras) {
				// TODO Auto-generated method stub
				//Log.d(TAG, "invoke callback updateReaderStatus");
				
				//连接从正常变成断开
				/*if(mUSReaderParas != null && (mUSReaderParas.isConnected == 0 && paras.isConnected != 0)) {
					stopReadcard();
				}*/
				mUSReaderParas = paras;
				uiHandler.postDelayed(runnable, 100);
				
				if(paras.isConnected == ReaderParas.SERVER_STATUS_UPGRADING) {
					appendLog(AppendLogEvent.LOG_CODE_ANY, "准备升级读卡器固件，停止读卡");
				} else if(paras.isConnected == ReaderParas.READER_STATUS_DFU) {
					appendLog(AppendLogEvent.LOG_CODE_ANY, "读卡器进入DFU升级模式，停止读卡");
					stopReadcard();
				} else if(paras.isConnected != 0)
					appendLog(AppendLogEvent.LOG_CODE_ANY, "服务器断开连接，无法读卡");
				else
					appendLog(AppendLogEvent.LOG_CODE_ANY, "服务器连接成功，开始读卡");
			}
			
			@Override
			public void openReaderSuccess() {
				//切换到启动读卡模式
				ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
				reportStartReadcardSuccessEvent.setError_code(0);
				reportStartReadcardSuccessEvent.setHave_inner_reader(false);
				EventBus.getDefault().post(reportStartReadcardSuccessEvent);
			}
			
			@Override
			public void onReaderError() {
				//停止读卡
				stopReadcard();
				uiHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						mReaderView.onReaderStopped();
					}
				}, 1000);
			}
		};

		private int setReaderState(int new_st) {
			/*if (mReader_st != new_st) {
				Log.d(TAG, "ReaderState from " + mReader_st + " to " + new_st);
			}*/
			mReader_st = new_st;
			return mReader_st;
		}

		private boolean processAny(Message msg) {
			boolean is_process = false;
			switch (msg.what) {
			case MSG_STOP_READCARD: {
				if (threadReadCard != null) {
					threadReadCard.interrupt();
					threadReadCard = null;
				}
				is_process = true;
				uiHandler.removeCallbacks(runnable);
			}
				break;
			case MSG_NOTIFY_QUIT: {
				Log.d(TAG, "MSG_NOTIFY_QUIT");
				if (threadReadCard != null) {
					threadReadCard.interrupt();
					threadReadCard = null;
				}
				postStopReadCard();

				if (mUsbPermissionMgr != null) {
					mUsbPermissionMgr.releaseMgr();
				}
				threadAsyncHandler = null;
				handlerAsyncTask = null;
				is_process = true;
			}
				break;
			case MSG_READCARD_THREAD_QUITED: {
				Log.d(TAG, "MSG_READCARD_THREAD_QUITED");
				threadReadCard = null;
				postStopReadCard();
				setReaderState(READER_ST_INIT);
				is_process = true;
				
				/*uiHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						mUsbPermissionMgr.releaseMgr();
						Log.d(TAG, "init usb mgr again:" + mUsbPermissionMgr.initMgr());
					}
				}, 10000);*/
			}
				break;

			default:
				break;
			}
			return is_process;
		}

		/**
		 * 
		 */
		private void postStopReadCard() {
			if (mReader != null) {
				// 给安全模块下电
				mReader.RTN_SetSAMPower(0);
				mReader.SDT_ClosePort();
				mReader = null;
			}
			DeviceInfo.PowerOffReader();
			mDevCaps = 0;

		}

		private void processInit(Message msg) {
			switch (msg.what) {
			case MSG_START_READCARD: {
				mMcuVer = "";
				mDeviceParamBean = (DeviceParamBean) msg.obj;
				mDeviceType = mDeviceParamBean.getDevice_type();
				mDevCaps = 0;
				mBtName = null;

				appendLog(AppendLogEvent.LOG_CODE_ANY, "读卡SDK版本:" + new CommonImpl().getSDKVersion());
				if (mDeviceType == DeviceParamBean.DEV_TYPE_BT) {
					// 初始化蓝牙读卡器
					if (mDeviceParamBean.getUser_obj() instanceof BluetoothDevice) {
						BluetoothDevice device = (BluetoothDevice) mDeviceParamBean.getUser_obj();
						int ret = ErrorCode.ErrCodeCommon_E_NOT_FOUND_READER;
						mBtName = device.getName();
						// 1. 打开读卡器
						// 仅LE或双模, 用BLE连接
						if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
							mReader = new BleImpl(mContext);
							mDevType = DEV_TYPE_BLE;
						} else if (device.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
							// 仅经典,用经典连接
							mReader = new BTImpl();
							mDevType = DEV_TYPE_BT;
						} else if (device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL) {
							if (mBtName == null || mBtName.toUpperCase().startsWith("IDR230")
									|| mBtName.toUpperCase().startsWith("IDR240")
									|| mBtName.toUpperCase().startsWith("IDR260")
									|| mBtName.toUpperCase().startsWith("IDR270")) {
								// 老款双模读卡器, 用经典连接
								mReader = new BTImpl();
								mDevType = DEV_TYPE_BT;
							} else {
								mReader = new BleImpl(mContext);
								mDevType = DEV_TYPE_BLE;
							}
						} else {
							Log.d(TAG, "undesired device type :" + mDeviceParamBean.getDevice_type());
							break;
						}
						mReader.setUsingCache(mDeviceParamBean.isUse_cache());
						mReader.setDebug(true);
						ret = mReader.SDT_OpenPort(device);
						Log.d(TAG, "SDT_OpenPort ret=" + ret);
						if (ret >= 0) {
							if (!(mBtName == null || mBtName.toUpperCase().startsWith("IDR230"))) {
								mDevCaps = mReader.RTN_TypeAGetDevCapabilities();
								Log.d(TAG, "mDevCaps = " + mDevCaps);
								String mcu_ver = getMcuVersion(mReader);
								if (mcu_ver != null) {
									mMcuVer = mcu_ver;
									appendLog(AppendLogEvent.LOG_CODE_ANY, "单片机版本号:" + mcu_ver);
								}
							}
							// 2. 启动读卡线程
							setReaderState(READER_ST_WORK);
							startReadcardThread();

						}
						ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
						reportStartReadcardSuccessEvent.setError_code(ret);
						reportStartReadcardSuccessEvent.setHave_inner_reader(false);
						EventBus.getDefault().post(reportStartReadcardSuccessEvent);
					}
				} else if (mDeviceType == DeviceParamBean.DEV_TYPE_SERIAL) {
					String portName = (String) mDeviceParamBean.getUser_obj();
					if (portName == null) {
						break;
					}
					// 1. 打开读卡器
					mReader = new SerialImpl();
					mDevType = DEV_TYPE_SERIAL;
					mReader.setUsingCache(mDeviceParamBean.isUse_cache());
					int ret = mReader.SDT_OpenPort(portName, DEF_SERIAL_BAUDRATE);
					Log.d(TAG, "DEV_TYPE_SERIAL SDT_OpenPort ret=" + ret);
					ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
					reportStartReadcardSuccessEvent.setError_code(ret);
					reportStartReadcardSuccessEvent.setHave_inner_reader(false);
					EventBus.getDefault().post(reportStartReadcardSuccessEvent);

					if (ret == ErrorCode.SUCCESS) {
						//串口设备要区分是不是iDR610-G网络读卡器
					
						if (mDeviceParamBean.isDual_channel()) {
							ret = mReader.RTN_SetTransmissionChannel(true);
							String log = null;
							if (ret == ErrorCode.SUCCESS) {
								log = "开启双通道成功";
							} else if (ret == ErrorCode.ErrCodeCommon_E_NotSupported) {
								log = "读卡器不支持双通道";
							} else {
								log = "开启双通道失败";
							}
							AppendLogEvent appendLogEvent = new AppendLogEvent();
							appendLogEvent.setCode(AppendLogEvent.LOG_CODE_ANY);
							appendLogEvent.setLog(log);
							EventBus.getDefault().post(appendLogEvent);
						}
						String mcu_ver = getMcuVersion(mReader);
						Log.d(TAG, "DEV_TYPE_SERIAL getMcuVersion mcu_ver=" + mcu_ver);
						if (mcu_ver != null) {
							mMcuVer = mcu_ver;
							appendLog(AppendLogEvent.LOG_CODE_ANY, "单片机版本号:" + mcu_ver);
						}
						
						// 给安全模块上电
						ret = mReader.RTN_SetSAMPower(1);
						// 特别说明：安全模块初始化需要时间，2000ms是技术规范文档的建议值。
						if(ret == ErrorCode.SUCCESS) SystemClock.sleep(2000);
						Log.d(TAG, "after RTN_SetSAMPower 1, ret = " + ret);
				    	
						// 2. 启动读卡线程
						setReaderState(READER_ST_WORK);
						startReadcardThread();
					}

				} else if (mDeviceType == DeviceParamBean.DEV_TYPE_USB) {
					// 检查是否读卡器是否已连接
					boolean is_device_connected = mUsbPermissionMgr.initMgr();

					if (is_device_connected) {
						UsbDevice usbDevice = mUsbPermissionMgr.getDevice();
						if (usbDevice != null && mUsbMgr.hasPermission(usbDevice)) {
							// 1. 打开读卡器
							int ret = ErrorCode.SUCCESS;
							if(mReader == null) {
								mReader = new USBImpl();
								ret = mReader.SDT_OpenPort(mUsbMgr, usbDevice);
							}
							
							ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
							reportStartReadcardSuccessEvent.setError_code(ret);
							reportStartReadcardSuccessEvent.setHave_inner_reader(false);
							EventBus.getDefault().post(reportStartReadcardSuccessEvent);

							if (ret == ErrorCode.SUCCESS) {
								// 仅HID支持开启双通道
								if (mDeviceParamBean.isDual_channel()) {
									ret = mReader.RTN_SetTransmissionChannel(true);
									String log = null;
									if (ret == ErrorCode.SUCCESS) {
										log = "开启双通道成功";
									} else if (ret == ErrorCode.ErrCodeCommon_E_NotSupported) {
										log = "读卡器不支持双通道";
									} else {
										log = "开启双通道失败";
									}
									AppendLogEvent appendLogEvent = new AppendLogEvent();
									appendLogEvent.setCode(AppendLogEvent.LOG_CODE_ANY);
									appendLogEvent.setLog(log);
									EventBus.getDefault().post(appendLogEvent);
								}
								// 仅HID支持读单片机版本号
								String mcu_ver = getMcuVersion(mReader);
								if (mcu_ver != null) {
									mMcuVer = mcu_ver;
									appendLog(AppendLogEvent.LOG_CODE_ANY, "单片机版本号:" + mcu_ver);
								}
							}

							// HID:ret=0;部标:ret=1，不支持启用缓存
							if (ret >= 0) {
								if (ret == 0) {
									mReader.setUsingCache(mDeviceParamBean.isUse_cache());
									mDevType = DEV_TYPE_USB_HID;
									mDevCaps = mReader.RTN_TypeAGetDevCapabilities();
									Log.d(TAG, "mDevCaps = " + mDevCaps);
								} else {
									mDevType = DEV_TYPE_USB_BB;
								}

								// 2. 启动读卡线程
								setReaderState(READER_ST_WORK);
								startReadcardThread();
							}

						} else {
							AppendLogEvent appendLogEvent = new AppendLogEvent();
							appendLogEvent.setCode(AppendLogEvent.LOG_CODE_ANY);
							appendLogEvent.setLog("设备未授权");
							EventBus.getDefault().post(appendLogEvent);
						}
					} else {
						AppendLogEvent appendLogEvent = new AppendLogEvent();
						appendLogEvent.setCode(AppendLogEvent.LOG_CODE_ANY);
						appendLogEvent.setLog("设备未连接");
						EventBus.getDefault().post(appendLogEvent);

						ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
						reportStartReadcardSuccessEvent.setError_code(ErrorCode.ErrCodeCommon_E_NOT_FOUND_READER);
						reportStartReadcardSuccessEvent.setHave_inner_reader(false);
						EventBus.getDefault().post(reportStartReadcardSuccessEvent);
					}
				} else if(mDeviceType == DeviceParamBean.DEV_TYPE_USB_SERIAL) {
					// 检查是否读卡器是否已连接
					boolean is_device_connected = mUsbPermissionMgr.initMgr();

					if (is_device_connected) {
						UsbDevice usbDevice = mUsbPermissionMgr.getDevice();
						if (usbDevice != null && mUsbMgr.hasPermission(usbDevice)) {
							// 1. 打开读卡器
							int ret = ErrorCode.SUCCESS;
							if(mReader == null) {
								mReader = new USBImpl();
								// 设置显示读卡器状态的回调
								mReader.setReaderCallback(mUSReaderCallback);
								// 设置连接类型
								mReader.setTransferType(mDeviceParamBean.getTransferType());
								mReader.setUsingCache(mDeviceParamBean.isUse_cache());
								mReader.setAppContext(mContext);
								ret = mReader.SDT_OpenPort(mUsbMgr, usbDevice);
								if(ret == ErrorCode.SUCCESS_UPGRADE_FIRMWARE) {
									appendLog(AppendLogEvent.LOG_CODE_ANY, "读卡器固件升级成功");
									return;
								}
							}
							
							Log.d(TAG, "SDT_OpenPort ret = " + ret);
							// HID:ret=0;部标:ret=1
							if (ret >= ErrorCode.SUCCESS) {
								// 仅HID支持读单片机版本号
								if(ret == ErrorCode.SUCCESS) {
									String mcu_ver = getMcuVersion(mReader);
									if (mcu_ver != null) {
										mMcuVer = mcu_ver;
										appendLog(AppendLogEvent.LOG_CODE_ANY, "单片机版本号:" + mcu_ver);
									}
									
									mDevType = DEV_TYPE_USB_SERIAL_HID;
								} else {
									mDevType = DEV_TYPE_USB_SERIAL_BB;
								}
								
								Log.d(TAG, "mDevType = " + mDevType);
								
								if(mDeviceParamBean.getTransferType() == 2) {
									ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
									reportStartReadcardSuccessEvent.setError_code(ret);
									reportStartReadcardSuccessEvent.setHave_inner_reader(false);
									EventBus.getDefault().post(reportStartReadcardSuccessEvent);
								}
								
								// 2. 启动读卡线程
								setReaderState(READER_ST_WORK);
								startReadcardThread();
							}
						} else {
							AppendLogEvent appendLogEvent = new AppendLogEvent();
							appendLogEvent.setCode(AppendLogEvent.LOG_CODE_ANY);
							appendLogEvent.setLog("设备未授权");
							EventBus.getDefault().post(appendLogEvent);
							
							ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
							reportStartReadcardSuccessEvent.setError_code(ErrorCode.ErrCodeCommon_E_NOT_FOUND_READER);
							reportStartReadcardSuccessEvent.setHave_inner_reader(false);
							EventBus.getDefault().post(reportStartReadcardSuccessEvent);
						}
					} else {
						AppendLogEvent appendLogEvent = new AppendLogEvent();
						appendLogEvent.setCode(AppendLogEvent.LOG_CODE_ANY);
						appendLogEvent.setLog("设备未连接");
						EventBus.getDefault().post(appendLogEvent);

						ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
						reportStartReadcardSuccessEvent.setError_code(ErrorCode.ErrCodeCommon_E_NOT_FOUND_READER);
						reportStartReadcardSuccessEvent.setHave_inner_reader(false);
						EventBus.getDefault().post(reportStartReadcardSuccessEvent);
					}
				} else {
					// 触发接受USB设备拔插广播
					boolean has_outer_device = mUsbPermissionMgr.initMgr();
					if (!has_outer_device) {
						// 触发检测内置读卡器
						Message send_msg = obtainMessage(MSG_CHECK_INNER_READER);
						sendMessage(send_msg);
					}
				}
			}
				break;
			case MSG_CHECK_INNER_READER: {
				// 1. 打开读卡器
				// 初始化内置读卡器：串口 [iDR420/iDR420-1/iDR600/iDR610] 或 USBCCID
				// [iDR410/iDR410-1]
				// 给读卡器上电
				DeviceInfo.PowerOnReader();
				ReaderType rdrType = Common.getReaderType();
				int ret = ErrorCode.ErrCodeCommon_E_NOT_FOUND_READER;
				switch (rdrType) {
				case SERIAL: {
					mReader = new SerialImpl();
					mDevType = DEV_TYPE_SERIAL;
					ret = mReader.SDT_OpenPort(getPortname(), DEF_SERIAL_BAUDRATE);
				}
					break;
				case USBCCID: { // TODO:调试CCID读卡器
					// 特别说明:iDR410读卡器上电后需要等待一段时间才能打开设备.1500ms是安全值，可调试后适当缩短时间。
					SystemClock.sleep(1500);
					mReader = new CCIDImpl();
					mDevType = DEV_TYPE_USB_CCID;
					ret = mReader.SDT_OpenPort();
				}
					break;
				default:
					break;
				}

				if (ret >= 0) {
					mReader.setUsingCache(mDeviceParamBean.isUse_cache());
					// 返回：>0版本号长度;其他为失败
					String mcu_ver = getMcuVersion(mReader);
					if (mcu_ver != null) {
						mMcuVer = mcu_ver;
						appendLog(AppendLogEvent.LOG_CODE_ANY, "单片机版本号:" + mcu_ver);
						
						// 给安全模块上电
						ret = mReader.RTN_SetSAMPower(1);
						// 特别说明：安全模块初始化需要时间，2000ms是技术规范文档的建议值。
						if(ret == ErrorCode.SUCCESS) SystemClock.sleep(2000);
						Log.d(TAG, "after RTN_SetSAMPower 1, ret = " + ret);
						
						// 2. 启动读卡子线程
						setReaderState(READER_ST_WORK);
						startReadcardThread();
					} else {
						// 给读卡器下电
						DeviceInfo.PowerOffReader();
					}
					
				}

				if(ret != ErrorCode.ErrCodeCommon_E_NotSupported) {
					ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
					reportStartReadcardSuccessEvent.setError_code(ret);
					if(getReadMode() <= 0)
						reportStartReadcardSuccessEvent.setHave_inner_reader(true);
					else
						reportStartReadcardSuccessEvent.setHave_inner_reader(false);
					EventBus.getDefault().post(reportStartReadcardSuccessEvent);
				}

				if (ret == ErrorCode.SUCCESS) {
					if (mDeviceParamBean.isDual_channel()) {
						ret = mReader.RTN_SetTransmissionChannel(true);
						String log = (ret == ErrorCode.SUCCESS) ? "开启双通道成功" : "开启双通道失败";
						AppendLogEvent appendLogEvent = new AppendLogEvent();
						appendLogEvent.setCode(AppendLogEvent.LOG_CODE_ANY);
						appendLogEvent.setLog(log);
						EventBus.getDefault().post(appendLogEvent);
					}
				}
			}
				break;
			case MSG_USB_DEV_ATTACHED: {
				// 只自适应支持热插拔USB读卡器
				if (mDeviceParamBean.getDevice_type() != DeviceParamBean.DEV_TYPE_INNER_OR_USB 
						&& (mDeviceParamBean.getDevice_type() != DeviceParamBean.DEV_TYPE_USB_SERIAL)) {
					break;
				}
				waitingDevice = (UsbDevice) msg.obj;
				
				// 1. 打开读卡器
				// 初始化USB HID 或 USB 部标读卡器
				mReader = new USBImpl();
				boolean isNetReader = mReader.isNetworkReader(waitingDevice);
				if(isNetReader) {
					// 设置状态处理的回调
					mReader.setReaderCallback(mUSReaderCallback);
					// 设置连接类型
					mReader.setTransferType(mDeviceParamBean.getTransferType());
					mReader.setAppContext(mContext);
				}
				
				int ret = mReader.SDT_OpenPort(mUsbMgr, waitingDevice);
				Log.d(TAG, "SDT_OpenPort ret=" + ret);
				if(ret == ErrorCode.SUCCESS_UPGRADE_FIRMWARE) {
					appendLog(AppendLogEvent.LOG_CODE_ANY, "读卡器固件升级成功");
					return;
				}

				ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
				reportStartReadcardSuccessEvent.setError_code(ret);
				reportStartReadcardSuccessEvent.setHave_inner_reader(true);

				if (ret >= 0) {
					// 设置读包间隔：默认不要修改。当系统的CPU占用率高时，适当调整读包间隔，确保每次读证成功。
					int READ_INERVAL = 0;// 0ms
					mReader.setReadPackInterval(READ_INERVAL);
					
					// 是HID读卡器
					if (ret == 0) {
						mReader.setUsingCache(mDeviceParamBean.isUse_cache());
						if(isNetReader) {
							mDevType = DEV_TYPE_USB_SERIAL_HID;
							if(mDeviceParamBean.getTransferType() == 2) {
								EventBus.getDefault().post(reportStartReadcardSuccessEvent);
							}
						} else {
							mDevType = DEV_TYPE_USB_HID;
							EventBus.getDefault().post(reportStartReadcardSuccessEvent);
						}
						
						mDevCaps = mReader.RTN_TypeAGetDevCapabilities();
						Log.d(TAG, "mDevCaps = " + mDevCaps);
						String mcu_ver = getMcuVersion(mReader);
						if (mcu_ver != null) {
							mMcuVer = mcu_ver;
							appendLog(AppendLogEvent.LOG_CODE_ANY, "单片机版本号:" + mcu_ver);
							Log.d(TAG, "mcu_ver " + mcu_ver);
						}
					} else {
						if(isNetReader) {
							mReader.setUsingCache(mDeviceParamBean.isUse_cache());
							mDevType = DEV_TYPE_USB_SERIAL_BB;
							if(mDeviceParamBean.getTransferType() == 2) {
								EventBus.getDefault().post(reportStartReadcardSuccessEvent);
							}
						} else {
							mDevType = DEV_TYPE_USB_BB;
							EventBus.getDefault().post(reportStartReadcardSuccessEvent);
						}
					}

					// 2. 启动读卡子线程
					setReaderState(READER_ST_WORK);
					startReadcardThread();
				}

				if (ret == ErrorCode.SUCCESS) {
					if (mDeviceParamBean.isDual_channel()) {
						ret = mReader.RTN_SetTransmissionChannel(true);
						String log = (ret == ErrorCode.SUCCESS) ? "开启双通道成功" : "开启双通道失败";
						AppendLogEvent appendLogEvent = new AppendLogEvent();
						appendLogEvent.setCode(AppendLogEvent.LOG_CODE_ANY);
						appendLogEvent.setLog(log);
						EventBus.getDefault().post(appendLogEvent);
					}
				}
				break;
			}
			default:
				break;
			}
		}

		private void processWork(Message msg) {
			switch (msg.what) {
			case MSG_USB_DEV_DETACHED: {
				setReaderState(READER_ST_INIT);
				if (waitingDevice != null) {
					waitingDevice = null;
				}
				if (threadReadCard != null) {
					threadReadCard.setDevRemoved();
				}
			}
				break;
			default:
				break;
			}
		}

		public ReaderAsyncHandler(Looper looper) {
			super(looper);
			setReaderState(READER_ST_INIT);
		}
		
		public ReaderAsyncHandler(Looper looper, IReader reader) {
			super(looper);
			this.mReader = reader;
			setReaderState(READER_ST_INIT);
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			// Log.d(TAG, "recv msg " + msg.what);
			boolean is_process = processAny(msg);
			if (!is_process) {
				switch (mReader_st) {
				case READER_ST_INIT: {
					processInit(msg);
				}
					break;
				case READER_ST_WORK: {
					processWork(msg);
				}
					break;
				default:
					break;
				}
			}
		}

		private void startReadcardThread() {
			if (threadReadCard != null) {
				threadReadCard.interrupt();
				threadReadCard = null;
			}
			boolean need_new_thread = true;
			if (mDevType == DEV_TYPE_USB_BB) { // 部标读卡器，只支持读B卡
				need_new_thread = false;
				if (mDeviceParamBean.isRead_b()) {
					threadReadCard = new ReadBCardThread(mReader);
				}
			} else if ((mDevType == DEV_TYPE_BLE) || (mDevType == DEV_TYPE_BT)) {
				if (mBtName == null || mBtName.toUpperCase().startsWith("IDR230")) { // iDR230的蓝牙读卡器只支持读B卡
					need_new_thread = false;
					if (mDeviceParamBean.isRead_b()) {
						threadReadCard = new ReadBCardThread(mReader);
					}
				}
			}

			if (need_new_thread) {
				if (mDeviceParamBean.isRead_b() && mDeviceParamBean.isRead_a()) {
					threadReadCard = new ReadABCardThread(mReader);
				} else if (mDeviceParamBean.isRead_b()) {
					threadReadCard = new ReadBCardThread(mReader);
				} else if (mDeviceParamBean.isRead_a()) {
					threadReadCard = new ReadACardThread(mReader);
				}
			}
			if (threadReadCard != null) {
				Log.d(TAG, "threadReadCard started");
				threadReadCard.start();
			}
		}
	}

	private String getDate() { // 得到的是一个日期：格式为：yyyy-MM-dd HH:mm:ss.SSS
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		return sdf.format(new Date());// 将当前日期进行格式化操作
	}

	private String last_log = "";
	private void appendLog(int code, String log) {
		if (!last_log.equals(log)) { // && mDevType != DEV_TYPE_USB_SERIAL_HID && mDevType != DEV_TYPE_USB_SERIAL_BB) {
			// Log.d(TAG, "appendLog " + log);
			AppendLogEvent appendLogEvent = new AppendLogEvent();
			appendLogEvent.setCode(code);
			// appendLogEvent.setLog(getDate() + ":" + log);
			appendLogEvent.setLog(log);
			EventBus.getDefault().post(appendLogEvent);

			last_log = log;
		}
	}

	private void reportExit() {
		ReportReadcardThreadExitEvent reportReadcardThreadExitEvent = new ReportReadcardThreadExitEvent();
		EventBus.getDefault().post(reportReadcardThreadExitEvent);
	}
	
	private int getReadMode() {
		int readMode = 0;
		String readcard_mode = SystemProperties.get("persist.readcard_mode", "-1");
    	if(readcard_mode.compareTo("unknown") != 0) {
    		readMode = Integer.parseInt(readcard_mode);
    	}
    	
    	return readMode;
	}

	/**
	 * 读卡子线程抽象类
	 * 
	 * @author lihuili
	 *
	 */
	private abstract class ReadCardThread extends Thread {
		abstract public void setDevRemoved();

		public int B_ROLL_INTERVAL = 200; // ms
		public int AB_ROLL_INTERVAL = 200; // ms
		
		public volatile boolean isContinue = false; 
	}

	/**
	 * 仅读B卡的子线程
	 * 
	 * @author lihuili 特别说明： 1. 因为读卡器初始化耗时比较长，请只初始化一次 2.
	 *         请不要在多个线程调用读卡SDK，初始化完后，所有的循环读卡都在一个子线程进行
	 */
	private class ReadBCardThread extends ReadCardThread {
		private final String TAG = "ReadCardThread";
		private IDCardInfo iDCardInfo = null;
		private volatile boolean bDevRemoved = false;
		private volatile boolean isNeedReOpen = false;
		ReadIDCardMode mode = ReadIDCardMode.loop;
		private IReader mReader;
		private int total = 0, succeed = 0, failed = 0;
		//private final ReportReadIDCardEvent reportReadIDCardEvent = new ReportReadIDCardEvent();

		public ReadBCardThread(IReader reader) {
			this.mReader = reader;
			this.mode = readCardMode;
			bDevRemoved = false;
		}

		public void setDevRemoved() {
			bDevRemoved = true;
		}
		
		private void printMemoryInfo() {
			float maxMem = (float)(Runtime.getRuntime().maxMemory() * 1.0 / (1024 * 1024));
			float totalMem = (float)(Runtime.getRuntime().totalMemory() * 1.0 / (1024 * 1024));
			float freeMem = (float)(Runtime.getRuntime().freeMemory() * 1.0 / (1024 * 1024));
			Log.d(TAG, "max:" + maxMem + ", total:" + totalMem + ", free:" + freeMem);
		}
		
		private byte[] readFileData(File file) {
			Log.i(TAG, "readFileData " + file.getAbsolutePath());
			FileInputStream fileInputStream = null;
			try {
				fileInputStream = new FileInputStream(file);
				int count = fileInputStream.available();
				byte[] data = new byte[count];
				int readLen = 0;
				while(readLen < count) {
					readLen += fileInputStream.read(data, readLen, count - readLen);
				}
				return data;
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if(fileInputStream != null) {
					try {
						fileInputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			return null;
		}

		@Override
		public void run() {
			super.run();
			int ret = 0;
			int readMode = getReadMode();
			boolean isReady = true;
			boolean isAlreadySetMode = false;
			int getSamTimes = 0;
			
			READY_READ_LOOP: do {
				if (isInterrupted()) {
					break;
				}
				if (bDevRemoved) {
					break;
				}
				if (isNeedReOpen && readMode == READER_MODE_NETWORK) {
					//TODO open
					mReader.SDT_ClosePort();
					ret = mReader.SDT_OpenPort(getPortname(), DEF_SERIAL_BAUDRATE);
					if(ret != ErrorCode.SUCCESS) {
						SystemClock.sleep(1000);
						isNeedReOpen = true;
						continue READY_READ_LOOP;
					} else {
						isNeedReOpen = false;
					}
				}
				// read samId, when success, sam is ready
				SAMIDInfo samIDInfo = new SAMIDInfo();
				ret = mReader.SDT_GetSAMIDToStr(samIDInfo);
				if (ret == 0) {
					isNeedReOpen = false;
					Log.d(TAG, "SDT_GetSAMIDToStr: SAMID is " + samIDInfo.SAMID);
					appendLog(AppendLogEvent.LOG_CODE_SAMID, samIDInfo.SAMID);
					
					ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
					reportStartReadcardSuccessEvent.setError_code(ret);
					EventBus.getDefault().post(reportStartReadcardSuccessEvent);
					
					break;
				} else if(ret == ErrorCode.ErrCodeCommon_E_WriteDataFail) {
					isNeedReOpen = true;
					SystemClock.sleep(1000);
				} else {
					isNeedReOpen = false;
					Log.d(TAG, "SDT_GetSAMIDToStr fail (" + ret + ")");
					SystemClock.sleep(20);
				}
			} while (true);
			
			// start to read card
			READ_CARD_LOOP: do {
				if (isInterrupted()) {
					break;
				}
				if (bDevRemoved) {
					break;
				}
				
				//iDR610-G进行自恢复
				if (!isReady && readMode == READER_MODE_NETWORK) {
					//TODO open
					if(isNeedReOpen) {
						mReader.SDT_ClosePort();
						ret = mReader.SDT_OpenPort(getPortname(), DEF_SERIAL_BAUDRATE);
						if(ret != ErrorCode.SUCCESS) {
							Log.d(TAG, "mReader.SDT_OpenPort() ret:" + ret);
							
							ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
							reportStartReadcardSuccessEvent.setError_code(ErrorCode.ErrCodeSerial_E_CONNECT_FAILED);
							EventBus.getDefault().post(reportStartReadcardSuccessEvent);
							
							SystemClock.sleep(1000);
							isNeedReOpen = true;
							continue READ_CARD_LOOP;
						} else {
							isNeedReOpen = false;
						}
					}
					
					// read samId, when success, sam is ready
					SAMIDInfo samIDInfo = new SAMIDInfo();
					ret = mReader.SDT_GetSAMIDToStr(samIDInfo);
					getSamTimes++;
					if (ret == 0) {
						getSamTimes = 0;
						Log.d(TAG, "SDT_GetSAMIDToStr: SAMID is " + samIDInfo.SAMID);
						ReportStartReadcardResultEvent reportStartReadcardSuccessEvent = new ReportStartReadcardResultEvent();
						reportStartReadcardSuccessEvent.setError_code(ret);
						EventBus.getDefault().post(reportStartReadcardSuccessEvent);
					} else if(ret == ErrorCode.ErrCodeCommon_E_WriteDataFail) {
						isNeedReOpen = true;
						isReady = false;
						SystemClock.sleep(1000);
						continue READ_CARD_LOOP;
					} else {
						if(getSamTimes >= 5) {
							isNeedReOpen = true;
							getSamTimes = 0;
						}
						isReady = false;
						Log.d(TAG, "SDT_GetSAMIDToStr fail (" + ret + ")");
						SystemClock.sleep(1000);
						continue READ_CARD_LOOP;
					}
				}
				
				
				// 读取本地证件数据测试照片解码检验设备是否通过认证
				/*
				byte[] photo = readFileData(mContext.getExternalFilesDir("photo.wlt"));
				Log.i(TAG, "read photo " + photo);
				byte[] baseMsg = readFileData(mContext.getExternalFilesDir("baseinfo.bin"));
				Log.i(TAG, "read base msg: " + baseMsg);
				if (photo != null && baseMsg != null) {
					iDCardInfo = new IDCardInfo();
					mReader.RTN_DecodeBaseMsg(baseMsg, iDCardInfo);
					iDCardInfo.photo = mReader.RTN_DecodeWlt(photo);
					ReportReadIDCardEvent reportReadIDCardEvent1 = new ReportReadIDCardEvent();
					reportReadIDCardEvent1.setiDCardInfo(iDCardInfo);
					reportReadIDCardEvent1.setSuccess(true);
					Log.i(TAG, "post read card event");
					EventBus.getDefault().post(reportReadIDCardEvent1);
				}
				//*/

				long begin_tick = SystemClock.uptimeMillis();
				byte[] sn = new byte[8];
				byte[] data = new byte[24];
				long begin_step_tick = begin_tick;
				long end_step_tick;

				//网络读卡器iDR212、iDR213连续读卡，测试时用，正式使用可注释掉
				/*if(mDevType == DEV_TYPE_USB_SERIAL_HID) {
					ret = mReader.RTN_TypeASearch();
				} else if(mDevType == DEV_TYPE_USB_SERIAL_BB && !isAlreadySetMode) {
					ret = mReader.typeASetBBReaderMode((byte)1);
					isAlreadySetMode = true;
					Log.d(TAG, "typeASetBBReaderMode ret = " + ret);
				} else if(mDevType == DEV_TYPE_SERIAL && readMode != READER_MODE_LOCAL) {
					try {
						Process process = Runtime.getRuntime().exec("cat /dev/samMisc");
						process.waitFor();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}*/
					
				if(mUSReaderParas != null && mUSReaderParas.isConnected == ReaderParas.SERVER_STATUS_UPGRADING) {
					SystemClock.sleep(100);
					continue;
				}
				
				ret = mReader.SDT_FindIDCard(sn);
				end_step_tick = SystemClock.uptimeMillis();
				if(ret == ErrorCode.ErrCodeCommon_E_WriteDataFail) {
					isReady = false;
					isNeedReOpen = true;
					continue READ_CARD_LOOP;
				} else if (ret >= ErrorCode.SUCCESS) {
					appendLog(AppendLogEvent.LOG_CODE_ANY, "Found B Card" + ", time=" + (end_step_tick - begin_step_tick) + "ms");
					begin_step_tick = SystemClock.uptimeMillis();
					ret = mReader.SDT_SelectIDCard(sn);
					end_step_tick = SystemClock.uptimeMillis();
					
					 if (ret < ErrorCode.SUCCESS) {
						isReady = true;
						isNeedReOpen = false;
						appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_SelectIDCard return " + ret);
						SystemClock.sleep(B_ROLL_INTERVAL);
						continue READ_CARD_LOOP;
					} else {
						appendLog(AppendLogEvent.LOG_CODE_ANY,
								"Select B Card" + ", time=" + (end_step_tick - begin_step_tick) + "ms");
					}
				} else {
					isReady = true;
					isNeedReOpen = false;
					appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_FindIDCard return " + ret);
					SystemClock.sleep(B_ROLL_INTERVAL);
					continue READ_CARD_LOOP;
				}

				/**
				 * lihuili 20201023:找卡/选卡成功后，应先读卡体管理号，再读卡
				 */
				String cid = null;
				if (mDeviceParamBean.isRead_b_cid()) {
					cid = getIDCid(mReader);
				}

				boolean readcardSucc = false;
				iDCardInfo = new IDCardInfo();
				String funcname = "";
				begin_step_tick = SystemClock.uptimeMillis();
				if (mDeviceParamBean.isRead_fp()) {
					ret = mReader.RTN_ReadBaseFPMsg(iDCardInfo);
					funcname = "RTN_ReadBaseFPMsg";
				} else {
					ret = mReader.RTN_ReadBaseMsg(iDCardInfo);
					funcname = "RTN_ReadBaseMsg";
				}
				
				end_step_tick = SystemClock.uptimeMillis();
				long end_tick = SystemClock.uptimeMillis();
				
				total++;
				if(ret == ErrorCode.SUCCESS) {
					succeed++;
				} else if(ret == ErrorCode.ErrCodeCommon_E_WriteDataFail) {
					isReady = false;
					isNeedReOpen = true;
					continue READ_CARD_LOOP;
				} else {
					failed++;
				}
				
				appendLog(AppendLogEvent.LOG_CODE_ANY, funcname + " return " + ret + ", time="
						+ (end_step_tick - begin_step_tick) + "ms\ntotal time=" + (end_tick - begin_tick) + "ms\n" +
						"成功:" + succeed + ",失败:" + failed + ",总计:" + total);

				ReportReadIDCardEvent reportReadIDCardEvent = new ReportReadIDCardEvent();
				if (ErrorCode.SUCCESS == ret) { // read card successful
					readcardSucc = true;
					reportReadIDCardEvent.setiDCardInfo(iDCardInfo);
					if (cid != null) {
						reportReadIDCardEvent.setCid(cid);
					}
				} else {
					readcardSucc = false;
				}
				reportReadIDCardEvent.setSuccess(readcardSucc);
				EventBus.getDefault().post(reportReadIDCardEvent);

				// 仅HID设备演示LED和蜂鸣器控制，部标读卡器不能发此命令
				if (mDevType == DEV_TYPE_USB_HID || (mDevType == DEV_TYPE_USB_SERIAL_HID && mUSReaderParas != null && mUSReaderParas.isConnected == 0)) {
					if (ErrorCode.SUCCESS == ret) {
						if ((mDevCaps > 0) && (mDevCaps & Info.DEV_CAPS_MASK_LED) == Info.DEV_CAPS_MASK_LED) {
							// 绿灯亮150ms,提示音100ms
							ret = mReader.RTN_TypeABeepLedNostop(1, 3, 0, 0, 1, 2);
						} else {
							// 绿灯亮灭一次，蜂鸣器响一声 100ms
							mReader.SDT_TypeBBeepLed(3, 0, 100);
						}
					} else {
						if ((mDevCaps > 0) && (mDevCaps & Info.DEV_CAPS_MASK_LED) == Info.DEV_CAPS_MASK_LED) {
							// 红灯亮50ms,提示音50ms
							ret = mReader.RTN_TypeABeepLedNostop(0, 0, 1, 1, 1, 1);
							if (ErrorCode.SUCCESS == ret) {
								// 中间等100-50=50ms
								SystemClock.sleep(100);
								// 红灯亮50ms,提示音50ms
								ret = mReader.RTN_TypeABeepLedNostop(0, 0, 1, 2, 1, 1);
							}
						} else {
							// 红灯亮灭两次[需设备支持]，蜂鸣器响两声
							mReader.SDT_TypeBBeepLed(6, 0, 50);
							SystemClock.sleep(50);
							mReader.SDT_TypeBBeepLed(6, 0, 50);
						}
					}
				}
				if (ReadIDCardMode.Single.equals(mode)) {
					break;
				} else if (ReadIDCardMode.continual.equals(mode)) {
					// do nothing;
				} else if (ReadIDCardMode.loop.equals(mode) && readcardSucc) {
					long startTime = System.currentTimeMillis();
					do {
						if (isInterrupted()) {
							break;
						}
						if (bDevRemoved) {
							break READ_CARD_LOOP;
						}
						SystemClock.sleep(B_ROLL_INTERVAL);
						if(mDevType == DEV_TYPE_USB_BB) {
							ret = mReader.RTN_ReadNewAppMsg(new MoreAddrInfo());
							appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_ReadNewAppMsg return " + ret);
						} else {
							ret = mReader.SDT_ReadIINSNDN(data);
							appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_ReadIINSNDN return " + ret);
						}
							
						if(ret == ErrorCode.ErrCodeCommon_E_WriteDataFail) {
							isReady = false;
							isNeedReOpen = true;
							break;
						} else if (ret < 0 /*|| ((mDevType == DEV_TYPE_USB_SERIAL_HID || mDevType == DEV_TYPE_USB_SERIAL_BB || (mDevType == DEV_TYPE_SERIAL && readMode == READER_MODE_NETWORK)) 
								&& System.currentTimeMillis() - startTime > 500)*/) {	// card removed
							ReportCardRemovedEvent reportCardRemovedEvent = new ReportCardRemovedEvent();
							EventBus.getDefault().post(reportCardRemovedEvent);
							break;
						} else {
							// lihuili：有判断卡在位超过10秒的需要时可还原代码
							// long currentTime =
							// System.currentTimeMillis();
							// if((currentTime - startTime) > 10000){
							// // 有客户要求 5s、10s内不重复读卡，超过即便卡没离开也要重复读，
							// 可按照需求决定是否需要这个判断。
							// // 卡在位超过10秒
							// appendLog("card on over 10s");
							// break;
							// }else{
							if (bDevRemoved) {
								break READ_CARD_LOOP;
							}
							// SystemClock.sleep(50);
							// }
						}
					} while (true);
				}
				if (bDevRemoved) {
					break;
				}
				// 读完卡需要间隔一段时间再找卡。
				SystemClock.sleep(B_ROLL_INTERVAL);
			} while (true);

			appendLog(AppendLogEvent.LOG_CODE_ANY, "finish read card");
			reportExit();
		}
	}

	/**
	 * 读A卡和B卡的子线程
	 * 
	 * @author lihuili 特别说明： 1. 因为读卡器初始化耗时比较长，请只初始化一次 2.
	 *         请不要在多个线程调用读卡SDK，初始化完后，所有的循环读卡都在一个子线程进行
	 */
	private class ReadACardThread extends ReadCardThread {
		private final String TAG = "ReadABCardThread";
		private final int CARD_TYPE_NO = 0; // 无卡
		private final int CARD_TYPE_A = 1; // 有A卡
		private int mCardType = CARD_TYPE_NO;
		private volatile boolean bDevRemoved = false;
		ReadIDCardMode mode = ReadIDCardMode.loop;
		private IReader mReader;
		/**
		 * 找A卡 特别说明：A卡在位时，找A卡偶尔会失败，所以要增加重试机制，连续n次找不到A卡才认为卡不在位
		 */
		private final int MAX_A_CARD_OFF_CNT = 2;

		private int Find_A_Card() {
			int ret = 0;
			int find_cnt = 0;
			while (find_cnt < MAX_A_CARD_OFF_CNT) {
				ret = mReader.RTN_TypeASearch();
				if (ret > 0) {
					// 找到A卡
					break;
				} else {
					find_cnt++;
				}
			}
			return ret;
		}

		/**
		 * 读A卡序号 特别说明：读A卡序号偶尔会失败，所以要增加重试机制，连续n次读A卡序号失败才认为读失败
		 */
		private final int MAX_A_CARD_READ_CNT = 3;

		int ReadCardASn(byte[] a_sn) {
			int read_a_cnt = 0;
			int ret = 0;
			while (read_a_cnt < MAX_A_CARD_READ_CNT) {
				ret = mReader.RTN_TypeAReadCID(a_sn);
				if (ret > 0) {
					break;
				} else {
					// 读A卡失败
					read_a_cnt++;
					SystemClock.sleep(10);
				}
			}
			return ret;
		}

		public ReadACardThread(IReader reader) {
			this.mReader = reader;
			this.mode = readCardMode;
			bDevRemoved = false;
		}

		public void setDevRemoved() {
			bDevRemoved = true;
		}

		@Override
		public void run() {
			super.run();

			// start to read card
			READ_CARD_LOOP: do {
				if (isInterrupted()) {
					break;
				}
				if (bDevRemoved) {
					break;
				}

				// 无卡或A卡, 需要找A卡
				// 找A卡
				int ret = Find_A_Card();
				if (ret > 0) {
					// 找到A卡
					// 读A卡序号
					byte[] a_sn = new byte[32];
					ret = ReadCardASn(a_sn);
					if (ret > 0) {
						// A卡在位
						if (mCardType != CARD_TYPE_A) {
							mCardType = CARD_TYPE_A;
							byte[] card_sn = new byte[ret];
							System.arraycopy(a_sn, 0, card_sn, 0, ret);

							ReportReadACardEvent reportReadACardEvent = new ReportReadACardEvent();
							reportReadACardEvent.setSuccess(true);
							reportReadACardEvent.setCard_sn(card_sn);
							EventBus.getDefault().post(reportReadACardEvent);

							// 读扇区的示例代码，参数请参考接口说明文档index.html
							// byte[] key =
							// {(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff};
							// byte[] data = new byte[16];
							// ret = mReader.RTN_TypeAReadBlockEncy((byte)1,
							// (byte)0, (byte)0x60, key, data);
							// Log.d(TAG, "ret=" + ret);
							// 仅蓝牙和HID设备演示LED和蜂鸣器控制，部标读卡器不能发此命令
							if ((mDevType == DEV_TYPE_BT) || (mDevType == DEV_TYPE_BLE)
									|| (mDevType == DEV_TYPE_USB_HID)) {
								// 此处演示读卡成功后控制LED灯和蜂鸣器,可选
								if (ret > 0) {
									if ((mDevCaps > 0)
											&& (mDevCaps & Info.DEV_CAPS_MASK_LED) == Info.DEV_CAPS_MASK_LED) {
										// 绿灯亮150ms,提示音100ms
										ret = mReader.RTN_TypeABeepLedNostop(1, 3, 0, 0, 1, 2);
									} else {
										// 绿灯亮灭一次，蜂鸣器响一声 100ms
										mReader.RTN_TypeABeepLed(3, 0, 100);
									}
								} else {
									if ((mDevCaps > 0)
											&& (mDevCaps & Info.DEV_CAPS_MASK_LED) == Info.DEV_CAPS_MASK_LED) {
										// 红灯亮50ms,提示音50ms
										ret = mReader.RTN_TypeABeepLedNostop(0, 0, 1, 1, 1, 1);
										if (ErrorCode.SUCCESS == ret) {
											// 中间等100-50=50ms
											SystemClock.sleep(100);
											// 红灯亮50ms,提示音50ms
											ret = mReader.RTN_TypeABeepLedNostop(0, 0, 1, 2, 1, 1);
										}
									} else {
										// 红灯亮灭两次[需设备支持]，蜂鸣器响两声
										mReader.RTN_TypeABeepLed(6, 0, 50);
										SystemClock.sleep(50);
										mReader.RTN_TypeABeepLed(6, 0, 50);
									}
								}
							}
						}
					} else {
						if (mCardType == CARD_TYPE_A) {
							ReportCardRemovedEvent reportCardRemovedEvent = new ReportCardRemovedEvent();
							EventBus.getDefault().post(reportCardRemovedEvent);
						}
						mCardType = CARD_TYPE_NO;
					}
				} else {
					if (mCardType == CARD_TYPE_A) {
						ReportCardRemovedEvent reportCardRemovedEvent = new ReportCardRemovedEvent();
						EventBus.getDefault().post(reportCardRemovedEvent);
					}
					mCardType = CARD_TYPE_NO;
				}

				if (ReadIDCardMode.Single.equals(mode)) {
					break;
				} else if (ReadIDCardMode.continual.equals(mode)) {
					// do nothing;
				} else if (ReadIDCardMode.loop.equals(mode)) {
					// do nothing;
				}
				if (bDevRemoved) {
					break;
				}

				// 读完卡需要间隔一段时间再找卡。
				SystemClock.sleep(AB_ROLL_INTERVAL);
			} while (true);
			appendLog(AppendLogEvent.LOG_CODE_ANY, "finish read card");
			reportExit();
		}
	}

	/**
	 * 读A卡和B卡的子线程
	 * 
	 * @author lihuili 特别说明： 1. 因为读卡器初始化耗时比较长，请只初始化一次 2.
	 *         请不要在多个线程调用读卡SDK，初始化完后，所有的循环读卡都在一个子线程进行
	 */
	private class ReadABCardThread extends ReadCardThread {
		private final String TAG = "ReadABCardThread";
		private final int CARD_TYPE_NO = 0; // 无卡
		private final int CARD_TYPE_A = 1; // 有A卡
		private final int CARD_TYPE_B = 2; // 有B卡
		private int mCardType = CARD_TYPE_NO;
		private IDCardInfo iDCardInfo = null;
		private volatile boolean bDevRemoved = false;
		ReadIDCardMode mode = ReadIDCardMode.loop;
		private IReader mReader;
		/**
		 * 找A卡 特别说明：A卡在位时，找A卡偶尔会失败，所以要增加重试机制，连续n次找不到A卡才认为卡不在位
		 */
		private final int MAX_A_CARD_OFF_CNT = 2;

		private int Find_A_Card() {
			int ret = 0;
			int find_cnt = 0;
			while (find_cnt < MAX_A_CARD_OFF_CNT) {
				ret = mReader.RTN_TypeASearch();
				if (ret > 0) {
					// 找到A卡
					break;
				} else {
					find_cnt++;
				}
			}
			return ret;
		}

		/**
		 * 读A卡序号 特别说明：读A卡序号偶尔会失败，所以要增加重试机制，连续n次读A卡序号失败才认为读失败
		 */
		private final int MAX_A_CARD_READ_CNT = 3;

		int ReadCardASn(byte[] a_sn) {
			int read_a_cnt = 0;
			int ret = 0;
			while (read_a_cnt < MAX_A_CARD_READ_CNT) {
				ret = mReader.RTN_TypeAReadCID(a_sn);
				if (ret > 0) {
					break;
				} else {
					// 读A卡失败
					read_a_cnt++;
					SystemClock.sleep(10);
				}
			}
			return ret;
		}

		public ReadABCardThread(IReader reader) {
			this.mReader = reader;
			this.mode = readCardMode;
			bDevRemoved = false;
		}

		public void setDevRemoved() {
			bDevRemoved = true;
		}

		@Override
		public void run() {
			super.run();
			int ret = 0;
			do {
				if (isInterrupted()) {
					break;
				}
				// read samId, when success, sam is ready
				SAMIDInfo samIDInfo = new SAMIDInfo();
				ret = mReader.SDT_GetSAMIDToStr(samIDInfo);
				if (ret == 0) {
					Log.d(TAG, "SDT_GetSAMIDToStr: SAMID is " + samIDInfo.SAMID);
					appendLog(AppendLogEvent.LOG_CODE_SAMID, samIDInfo.SAMID);
					break;
				} else {
					SystemClock.sleep(20);
				}
			} while (true);
			if (0 == ret) {
				// start to read card
				READ_CARD_LOOP: do {
					if (isInterrupted()) {
						break;
					}
					if (bDevRemoved) {
						break;
					}
					// 无卡或A卡, 需要找A卡
					if (mCardType != CARD_TYPE_B) {
						// 找A卡
						ret = Find_A_Card();
						if (ret > 0) {
							// 找到A卡
							// 读A卡序号
							byte[] a_sn = new byte[32];
							ret = ReadCardASn(a_sn);
							if (ret > 0) {
								// A卡在位
								if (mCardType != CARD_TYPE_A) {
									mCardType = CARD_TYPE_A;
									byte[] card_sn = new byte[ret];
									System.arraycopy(a_sn, 0, card_sn, 0, ret);

									ReportReadACardEvent reportReadACardEvent = new ReportReadACardEvent();
									reportReadACardEvent.setSuccess(true);
									reportReadACardEvent.setCard_sn(card_sn);
									EventBus.getDefault().post(reportReadACardEvent);

									// 读扇区的示例代码，参数请参考接口说明文档index.html
									// byte[] key =
									// {(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff};
									// byte[] data = new byte[16];
									// ret =
									// mReader.RTN_TypeAReadBlockEncy((byte)0,
									// (byte)0, (byte)0x61, key, data);
									// Log.d(TAG, "ret=" + ret);
									// 仅HID设备演示LED和蜂鸣器控制
									if (mDevType == DEV_TYPE_USB_HID) {
										// 此处演示读卡成功后控制LED灯和蜂鸣器,可选
										if (ret > 0) {
											if ((mDevCaps > 0)
													&& (mDevCaps & Info.DEV_CAPS_MASK_LED) == Info.DEV_CAPS_MASK_LED) {
												// 绿灯亮150ms,提示音100ms
												ret = mReader.RTN_TypeABeepLedNostop(1, 3, 0, 0, 1, 2);
											} else {
												// 绿灯亮灭一次，蜂鸣器响一声 100ms
												mReader.RTN_TypeABeepLed(3, 0, 100);
											}
										} else {
											if ((mDevCaps > 0)
													&& (mDevCaps & Info.DEV_CAPS_MASK_LED) == Info.DEV_CAPS_MASK_LED) {
												// 红灯亮50ms,提示音50ms
												ret = mReader.RTN_TypeABeepLedNostop(0, 0, 1, 1, 1, 1);
												if (ErrorCode.SUCCESS == ret) {
													// 中间等100-50=50ms
													SystemClock.sleep(100);
													// 红灯亮50ms,提示音50ms
													ret = mReader.RTN_TypeABeepLedNostop(0, 0, 1, 2, 1, 1);
												}
											} else {
												// 红灯亮灭两次[需设备支持]，蜂鸣器响两声
												mReader.RTN_TypeABeepLed(6, 0, 50);
												SystemClock.sleep(50);
												mReader.RTN_TypeABeepLed(6, 0, 50);
											}
										}
									}
								}
							} else {
								if (mCardType == CARD_TYPE_A) {
									ReportCardRemovedEvent reportCardRemovedEvent = new ReportCardRemovedEvent();
									EventBus.getDefault().post(reportCardRemovedEvent);
								}
								mCardType = CARD_TYPE_NO;
							}
						} else {
							if (mCardType == CARD_TYPE_A) {
								ReportCardRemovedEvent reportCardRemovedEvent = new ReportCardRemovedEvent();
								EventBus.getDefault().post(reportCardRemovedEvent);
							}
							mCardType = CARD_TYPE_NO;
						}
					}

					// 无卡，需要找B卡
					if (mCardType == CARD_TYPE_NO) {
						// 找B卡
						long begin_tick = SystemClock.uptimeMillis();
						// RTN_Authenticate的调用必须增加重试机制，确保从A卡到B卡的场强切换成功
						int retry_count = 0;
						int MAX_RETRY_TIMES = 2;
						do {
							ret = mReader.RTN_Authenticate();
							appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_Authenticate return " + ret);
							if (ErrorCode.SUCCESS == ret) {
								break;
							} else {
								retry_count++;
								if (retry_count >= MAX_RETRY_TIMES) {
									break;
								}
								SystemClock.sleep(10);
							}
						} while (true);
						if (ErrorCode.SUCCESS == ret) {
							/**
							 * lihuili 20201023:找卡/选卡成功后，应先读卡体管理号，再读卡
							 */
							String cid = null;
							if (mDeviceParamBean.isRead_b_cid()) {
								cid = getIDCid(mReader);
							}

							// find card success. notice UI clean idcardInfo
							if (iDCardInfo != null && iDCardInfo.photo != null) {
								iDCardInfo.photo.recycle();
							}
							iDCardInfo = new IDCardInfo();
							String funcname = "";
							if (mDeviceParamBean.isRead_fp()) {
								ret = mReader.RTN_ReadBaseFPMsg(iDCardInfo);
								funcname = "RTN_ReadBaseFPMsg";
							} else {
								ret = mReader.RTN_ReadBaseMsg(iDCardInfo);
								funcname = "RTN_ReadBaseMsg";
							}
							long end_tick = SystemClock.uptimeMillis();
							appendLog(AppendLogEvent.LOG_CODE_ANY,
									funcname + " return " + ret + ",time=" + (end_tick - begin_tick) + "ms");

							ReportReadIDCardEvent reportReadIDCardEvent = new ReportReadIDCardEvent();
							if (ErrorCode.SUCCESS == ret) { // read card
															// successful
								mCardType = CARD_TYPE_B;
								reportReadIDCardEvent.setiDCardInfo(iDCardInfo);
								if (cid != null) {
									reportReadIDCardEvent.setCid(cid);
								}
								reportReadIDCardEvent.setSuccess(true);
							} else {
								mCardType = CARD_TYPE_NO;
								reportReadIDCardEvent.setSuccess(false);
							}
							EventBus.getDefault().post(reportReadIDCardEvent);
							// 仅蓝牙和HID设备演示LED和蜂鸣器控制，部标读卡器不能发此命令
							if ((mDevType == DEV_TYPE_BT) || (mDevType == DEV_TYPE_BLE)
									|| (mDevType == DEV_TYPE_USB_HID)) {
								if (ErrorCode.SUCCESS == ret) {
									if ((mDevCaps > 0)
											&& (mDevCaps & Info.DEV_CAPS_MASK_LED) == Info.DEV_CAPS_MASK_LED) {
										// 绿灯亮150ms,提示音100ms
										ret = mReader.RTN_TypeABeepLedNostop(1, 3, 0, 0, 1, 2);
									} else {
										// 绿灯亮灭一次，蜂鸣器响一声 100ms
										mReader.SDT_TypeBBeepLed(3, 0, 100);
									}
								} else {
									if ((mDevCaps > 0)
											&& (mDevCaps & Info.DEV_CAPS_MASK_LED) == Info.DEV_CAPS_MASK_LED) {
										// 红灯亮50ms,提示音50ms
										ret = mReader.RTN_TypeABeepLedNostop(0, 0, 1, 1, 1, 1);
										if (ErrorCode.SUCCESS == ret) {
											// 中间等100-50=50ms
											SystemClock.sleep(100);
											// 红灯亮50ms,提示音50ms
											ret = mReader.RTN_TypeABeepLedNostop(0, 0, 1, 2, 1, 1);
										}
									} else {
										// 红灯亮灭两次[需设备支持]，蜂鸣器响两声
										mReader.SDT_TypeBBeepLed(6, 0, 50);
										SystemClock.sleep(50);
										mReader.SDT_TypeBBeepLed(6, 0, 50);
									}
								}
							}
						}
					}

					if (ReadIDCardMode.Single.equals(mode)) {
						break;
					} else if (ReadIDCardMode.continual.equals(mode)) {
						// do nothing;
					} else if (ReadIDCardMode.loop.equals(mode)) {
						if (mCardType == CARD_TYPE_B) {
							long startTime = System.currentTimeMillis();
							do {
								if (isInterrupted()) {
									break;
								}
								if (bDevRemoved) {
									break READ_CARD_LOOP;
								}
								ret = mReader.RTN_ReadNewAppMsg(new MoreAddrInfo());
								appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_ReadNewAppMsg return " + ret);
								if (ret < 0) {// card removed
									mCardType = CARD_TYPE_NO;
									ReportCardRemovedEvent reportCardRemovedEvent = new ReportCardRemovedEvent();
									EventBus.getDefault().post(reportCardRemovedEvent);
									break;
								} else {
									// lihuili：有判断卡在位超过10秒的需要时可还原代码
									// long currentTime =
									// System.currentTimeMillis();
									// if((currentTime - startTime) > 10000){
									// // 有客户要求 5s、10s内不重复读卡，超过即便卡没离开也要重复读，
									// 可按照需求决定是否需要这个判断。
									// // 卡在位超过10秒
									// appendLog("card on over 10s");
									// break;
									// }else{
									if (bDevRemoved) {
										break READ_CARD_LOOP;
									}
									SystemClock.sleep(100);
									// }
								}
							} while (true);
						}
					}
					if (bDevRemoved) {
						break;
					}

					// 读完卡需要间隔一段时间再找卡。
					SystemClock.sleep(AB_ROLL_INTERVAL);
				} while (true);
			}
			appendLog(AppendLogEvent.LOG_CODE_ANY, "finish read card");
			reportExit();
		}
	}

	private String getMcuVersion(IReader reader) {
		if (reader == null) {
			return null;
		}
		String mcu_ver = null;
		// 查询单片机版本
		byte[] out_data = new byte[6];
		int read_a_cnt = 0;
		int MAX_A_CARD_READ_CNT = 3;
		while (read_a_cnt < MAX_A_CARD_READ_CNT) {
			int ret = reader.RTN_TypeAGetMcuVersion(out_data);
			if (ret < 0) {
				read_a_cnt++;
				try {
					Thread.sleep(100, 0);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (ret == ErrorCode.ErrCodeCommon_E_WriteDataFail) {
					Log.d(TAG, "RTN_TypeAGetMcuVersion ret=" + ret);
					break;
				}
			} else {
				mcu_ver = String.format("V%d.%d.%d", out_data[0], out_data[1], out_data[2]);
				break;
			}
		}
		// Log.d(TAG, "getMcuVersion read_a_cnt is " + read_a_cnt);
		return mcu_ver;
	}

	private String getIDCid(IReader reader) {
		String cid = null;
		byte[] data = new byte[24];
		int retNo = reader.SDT_ReadIINSNDN(data);
		if (retNo > 0) {
			cid = "";
			for (int i = 0; i < retNo; i++) {
				cid += String.format("%02x", data[i]);
			}
		}
		return cid;
	}
}
