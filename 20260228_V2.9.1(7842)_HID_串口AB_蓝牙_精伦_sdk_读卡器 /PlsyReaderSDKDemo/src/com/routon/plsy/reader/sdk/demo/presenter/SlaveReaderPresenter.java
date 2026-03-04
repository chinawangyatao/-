package com.routon.plsy.reader.sdk.demo.presenter;

import java.util.Timer;
import java.util.TimerTask;

import org.greenrobot.eventbus.EventBus;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;
import android.util.Log;

import com.routon.plsy.reader.sdk.common.ErrorCode;
import com.routon.plsy.reader.sdk.common.Info.IDCardInfo;
import com.routon.plsy.reader.sdk.common.Info.MoreAddrInfo;
import com.routon.plsy.reader.sdk.demo.model.AppendLogEvent;
import com.routon.plsy.reader.sdk.demo.model.DeviceParamBean;
import com.routon.plsy.reader.sdk.demo.model.ReportCardRemovedEvent;
import com.routon.plsy.reader.sdk.demo.model.ReportReadIDCardEvent;
import com.routon.plsy.reader.sdk.demo.presenter.UsbDevPermissionMgr.UsbDevPermissionMgrCallback;
import com.routon.plsy.reader.sdk.demo.view.IReaderView;
import com.routon.plsy.reader.sdk.intf.IReader;
import com.routon.plsy.reader.sdk.serial.SerialImpl;
import com.routon.plsy.reader.sdk.usb.USBImpl;

/**
 * 完整的一次读卡流程示例
 * @author lihuili
 *
 */
public class SlaveReaderPresenter implements IReaderPresenter{
	private UsbDevPermissionMgr mUsbPermissionMgr;
	private UsbManager mUsbMgr;
	private IReader mReader;
	private IReaderView mReaderView;
	private UsbDevice mUsbDevice;
	private final int DEF_SERIAL_BAUDRATE = 115200;
	private final String DEF_SERIAL_PORT_NAME = "/dev/ttyS4"; //需要修改为真实的串口设备路径
	private Timer mReadTimer;
	private int TASK_DELAY_TIME = 10;
	private int TASK_PERIOD_TIME = 100;
	private Object mLock = new Object();
	
	public IReader getReader() {
		IReader reader = null;
		synchronized (mLock) {
			reader = mReader;
		}
		return reader;
	}

	public void setReader(IReader reader) {
		synchronized (mLock) {
			this.mReader = reader;
		}
	}

	public SlaveReaderPresenter(IReaderView readerView){
		mReaderView = readerView;
	}
	
	private boolean OpenSerialDevice(String devname){		
		if(!TextUtils.isEmpty(devname)){
			IReader reader = new SerialImpl();
			//reader.setDebug(true);
			int ret = reader.SDT_OpenPort(devname, DEF_SERIAL_BAUDRATE);
			setReader(reader);
			if(ret == ErrorCode.SUCCESS){
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_OpenPort success");
				return true;
			}
		}
		mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "打开串口读卡器失败");
		return false;
	}
	
	private boolean OpenUSBDevice(Context context){
		//初始化USB设备对象
		mUsbMgr = (UsbManager)context.getSystemService(Context.USB_SERVICE);
		mUsbPermissionMgr = new UsbDevPermissionMgr(context, new UsbDevPermissionMgrCallback() {
		  @Override
		  public void onNoticeStr(String notice) {
	
		  }
	
		  @Override
		  public void onUsbDevReady(UsbDevice device) {
			  if(mReaderView!=null){
				  mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备已授权");
			  }
		  }

		  @Override
		  public void onUsbDevRemoved(UsbDevice device) {
		
		  }

		  @Override
		  public void onUsbRequestPermission() {
			  if(mReaderView!=null){
				  mReaderView.appendLog(AppendLogEvent.LOG_CODE_REQ_USB_PERMISSION, "请求设备授权中...");
				  }
			  }
		  });
	
	  	//检查是否读卡器是否已连接
	  	boolean is_device_connected = mUsbPermissionMgr.initMgr();
	  
	  	if(is_device_connected ){
			mUsbDevice = mUsbPermissionMgr.getDevice();
			if(mUsbDevice!=null && mUsbMgr.hasPermission(mUsbDevice)){
				IReader reader = new USBImpl();
				int ret = reader.SDT_OpenPort(mUsbMgr, mUsbDevice);
				setReader(reader);
				if(ret >= ErrorCode.SUCCESS){
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "打开USB读卡器成功");
					return true;
				}else{
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "打开USB读卡器失败(" + ret + ")");
				}
			}else{
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备未授权");
			}
		}else{
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备未连接");
		}
		return false;
	}
	
	
	public boolean CloseDevice(){
		IReader reader = getReader();
		if(reader!=null){
			//读卡结束，关闭读卡器
			reader.SDT_ClosePort();
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "关闭从通道接收成功");
			reader = null;
			setReader(null);
		}
		if(mUsbPermissionMgr!=null){
			//释放USB对象
			mUsbPermissionMgr.releaseMgr();
			mUsbPermissionMgr = null;
		}
		return true;
	}
	public IReaderView getReaderView() {
		return mReaderView;
	}
	public void setReaderView(IReaderView readerView) {
		this.mReaderView = readerView;
	}
	
	private String toHexStringNoSpace(byte[] buffer, int length){

	    String bufferString = "";
	    int len = length;
	    if(buffer!=null){
	    	if(len > buffer.length){
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

	@Override
	public void startReadcard(DeviceParamBean devParamBean) {
		// TODO Auto-generated method stub
		if(devParamBean!=null && mReaderView!=null){
			CloseDevice();
			boolean is_opened = false;
			switch(devParamBean.getDevice_type()){
				case DeviceParamBean.DEV_TYPE_SERIAL:
				{
					String portName = (String)devParamBean.getUser_obj();
					if(portName!=null){
						is_opened = OpenSerialDevice(portName);
					}
				}
				break;
				case DeviceParamBean.DEV_TYPE_USB:
				{
					is_opened = OpenUSBDevice(mReaderView.getContext());
				}
					break;
				default:
				{
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "从设备必须指定为串口或USB");
				}
				break;
			}
			
			if(mReadTimer!=null){
				mReadTimer.cancel();
				mReadTimer = null;
			}
			if(is_opened){
				mReadTimer = new Timer();
				mReadTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						IReader reader = getReader();
						if(reader!=null){
							IDCardInfo cardInfo = new IDCardInfo();
							int ret = reader.RTN_GetBaseMsg(cardInfo);
							if(ret == ErrorCode.SUCCESS){
								ReportReadIDCardEvent reportReadIDCardEvent = new ReportReadIDCardEvent();
			                    reportReadIDCardEvent.setiDCardInfo(cardInfo);
			                    reportReadIDCardEvent.setSuccess(true);
			                    EventBus.getDefault().post(reportReadIDCardEvent);
							}
						}
					}
				}, TASK_DELAY_TIME, TASK_PERIOD_TIME);
				
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "从通道接收数据中,请在主通道读卡");
			}
		}
	}

	@Override
	public void stopReadcard() {
		// TODO Auto-generated method stub
		if(mReadTimer!=null){
			mReadTimer.cancel();
			mReadTimer = null;
		}
		CloseDevice();
	}

	@Override
	public void release() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void setDeviceParam(DeviceParamBean devParamBean) {
	}
}
