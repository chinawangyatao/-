package com.routon.plsy.reader.sdk.demo.presenter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.sql.RowSetInternal;

import org.greenrobot.eventbus.EventBus;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.routon.plsy.reader.sdk.common.ErrorCode;
import com.routon.plsy.reader.sdk.common.ReaderLog;
import com.routon.plsy.reader.sdk.common.Info.IDCardInfo;
import com.routon.plsy.reader.sdk.common.Info.MoreAddrInfo;
import com.routon.plsy.reader.sdk.common.Info.ReaderParas;
import com.routon.plsy.reader.sdk.demo.model.AppendLogEvent;
import com.routon.plsy.reader.sdk.demo.model.DeviceParamBean;
import com.routon.plsy.reader.sdk.demo.model.ReportCardRemovedEvent;
import com.routon.plsy.reader.sdk.demo.model.ReportReadIDCardEvent;
import com.routon.plsy.reader.sdk.demo.model.ReportStartReadcardResultEvent;
import com.routon.plsy.reader.sdk.demo.presenter.UsbDevPermissionMgr.UsbDevPermissionMgrCallback;
import com.routon.plsy.reader.sdk.demo.view.IReaderView;
import com.routon.plsy.reader.sdk.intf.IReader;
import com.routon.plsy.reader.sdk.serial.SerialImpl;
import com.routon.plsy.reader.sdk.usb.USBImpl;
import com.routon.plsy.reader.sdk.usb.USBSerialReader.ReaderCallback;

/**
 * 完整的一次读卡流程示例
 * 
 * @author lihuili
 *
 */
public class OnceReadCard {
	private static final String TAG = "OnceReadCard";
	private UsbDevPermissionMgr mUsbPermissionMgr;
	private UsbManager mUsbMgr;
	private IReader mReader;
	private IReaderView mReaderView;
	private UsbDevice mUsbDevice;
	private ReaderParas mUSReaderParas;
	private Handler uiHandler = new Handler();
	private Runnable runnable = new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			if(mReaderView != null) mReaderView.updateParasUI(mUSReaderParas);
		}
	};

	public boolean OpenDevice(Context context) {
		// 初始化USB设备对象
		mUsbMgr = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		mUsbPermissionMgr = new UsbDevPermissionMgr(context, new UsbDevPermissionMgrCallback() {
			@Override
			public void onNoticeStr(String notice) {

			}

			@Override
			public void onUsbDevReady(UsbDevice device) {
				if (mReaderView != null) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备已授权");
				}
			}

			@Override
			public void onUsbDevRemoved(UsbDevice device) {
			}

			@Override
			public void onUsbRequestPermission() {
				if (mReaderView != null) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_REQ_USB_PERMISSION, "请求设备授权中...");
				}
			}
		});

		// 检查是否读卡器是否已连接
		boolean is_device_connected = mUsbPermissionMgr.initMgr();

		if (is_device_connected) {
			mUsbDevice = mUsbPermissionMgr.getDevice();
			if (mUsbDevice != null && mUsbMgr.hasPermission(mUsbDevice)) {
				mReader = new USBImpl();
				Log.d(TAG, mUsbMgr.toString() + ";" + mUsbDevice.toString());
				int ret = mReader.SDT_OpenPort(mUsbMgr, mUsbDevice);
				if (ret >= ErrorCode.SUCCESS) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_OpenPort success");
					return true;
				} else {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_OpenPort fail(" + ret + ")");
				}
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备未授权");
			}
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备未连接");
		}
		return false;
	}
	
	public boolean OpenUSDevice(Context context, DeviceParamBean deviceParamBean) {
		// 初始化USB设备对象
		mUsbMgr = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		mUsbPermissionMgr = new UsbDevPermissionMgr(context, new UsbDevPermissionMgrCallback() {
			@Override
			public void onNoticeStr(String notice) {

			}

			@Override
			public void onUsbDevReady(UsbDevice device) {
				if (mReaderView != null) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备已授权");
				}
			}

			@Override
			public void onUsbDevRemoved(UsbDevice device) {
				
			}

			@Override
			public void onUsbRequestPermission() {
				if (mReaderView != null) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_REQ_USB_PERMISSION, "请求设备授权中...");
				}
			}
		});

		// 检查是否读卡器是否已连接
		boolean is_device_connected = mUsbPermissionMgr.initMgr();
		
		if (is_device_connected) {
			mUsbDevice = mUsbPermissionMgr.getDevice();
			if (mUsbDevice != null && mUsbMgr.hasPermission(mUsbDevice)) {
				mReader = new USBImpl();
				Log.d(TAG, mUsbMgr.toString() + ";" + mUsbDevice.toString());
				
				mReader.setReaderCallback(new ReaderCallback() {
					
					@Override
					public void updateReaderStatus(ReaderParas paras) {
						// 更新读卡器状态，可根据实际使用需要在界面上对不同的状态进行单独处理
						mUSReaderParas = paras;
						uiHandler.postDelayed(runnable, 100);
					}
					
					@Override
					public void openReaderSuccess() {
						uiHandler.postDelayed(new Runnable() {
							
							@Override
							public void run() {
								// 收到打开读卡器成功的回调后才能读卡
								mReaderView.onGotStartReadcardResult(ErrorCode.SUCCESS, false);
							}
						}, 100);
						
					}
				});
				
				// 设置连接类型
				mReader.setTransferType(deviceParamBean.getTransferType());
				mReader.setUsingCache(deviceParamBean.isUse_cache());
				
				int ret = mReader.SDT_OpenPort(mUsbMgr, mUsbDevice);
				if (ret >= ErrorCode.SUCCESS) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_OpenPort success");

					// 仅HID支持读单片机版本号
					String mcu_ver = getMcuVersion(mReader);
					if (mcu_ver != null) {
						mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "单片机版本号:" + mcu_ver);
					}
					
					return true;
				} else {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_OpenPort fail(" + ret + ")");
				}
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备未授权");
			}
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备未连接");
		}
		return false;
	}
	
	public byte[] testCspwLag(DeviceParamBean deviceParamBean) {
		String portName = (String) deviceParamBean.getUser_obj();

		// 1. 打开读卡器
		mReader = new SerialImpl();
		mReader.setUsingCache(deviceParamBean.isUse_cache());
		int ret = mReader.SDT_OpenPort(portName, 115200);
		Log.d(TAG, "DEV_TYPE_SERIAL SDT_OpenPort ret=" + ret);

		if (ret == ErrorCode.SUCCESS) {
			//串口设备要区分是不是iDR610-G网络读卡器
			byte[] latency = new byte[11];
			ret = mReader.RTN_TypeBTestNetSpeed(latency);
			mReader.SDT_ClosePort();
			Log.d(TAG, "RTN_TypeBTestNetSpeed ret=" + ret);
			if(ret > 0) {
				return latency;
			}
		}
		
		return null;
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
		return mcu_ver;
	}

	public boolean CheckProgramCode(String progCode) {
		if (TextUtils.isEmpty(progCode) || progCode.length() != 8) {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "请输入8位项目码");
		} else {
			int ret = mReader.RTN_TypeACheckProgramCode(progCode.getBytes());
			if (ret == ErrorCode.SUCCESS) {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "项目认证码验证通过");
			} else if (ret == ErrorCode.ErrCodeCommon_E_NotSupported) {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备不支持项目码功能");
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "项目认证码验证失败(" + ret + ")");
			}

			// byte[] encryptedProgramCode = new byte[16];
			// ret = mReader.RTN_TypeAGetProgramCode(encryptedProgramCode);
			// if(ret == 16){
			// mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "读到原加密的项目认证码:"
			// + toHexStringNoSpace(encryptedProgramCode,
			// encryptedProgramCode.length));
			//
			// ret = mReader.RTN_TypeASetProgramCode(progCode.getBytes());
			// if(ret==ErrorCode.SUCCESS){
			// mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设置项目认证码:" +
			// progCode +"成功");
			// ret = mReader.RTN_TypeAGetProgramCode(encryptedProgramCode);
			// if(ret == 16){
			// mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "读到新加密的项目认证码:"
			// + toHexStringNoSpace(encryptedProgramCode,
			// encryptedProgramCode.length));
			// }
			// }
			// }
		}
		return false;
	}

	public boolean FindIDCard() {
		if (mReader != null) {
			// 打开设备成功，找卡
			byte[] sn = new byte[8];
			int ret = mReader.SDT_FindIDCard(sn);
			if (ret == ErrorCode.SUCCESS) {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_FindIDCard success");
				return true;
			}
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_FindIDCard fail(" + ret + ")");
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "Please init reader");
		}
		return false;
	}

	public boolean SelectIDCard() {
		if (mReader != null) {
			// 找卡成功，选卡
			byte[] sn = new byte[8];
			int ret = mReader.SDT_SelectIDCard(sn);
			if (ret == ErrorCode.SUCCESS) {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_SelectIDCard success");
				return true;
			}
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_SelectIDCard fail(" + ret + ")");
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "Please init reader");
		}
		return false;
	}

	public boolean ReadBaseMsg() {
		if (mReader != null) {
			// 选卡成功，读卡
			IDCardInfo iDCardInfo = new IDCardInfo();
			int ret = mReader.RTN_ReadBaseMsg(iDCardInfo);
			if (ErrorCode.SUCCESS == ret) {
				// 读卡成功，卡的基本信息、照片已经解析并存储到iDCardInfo中
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_ReadBaseMsg success");
				if (mReaderView != null) {
					ReportReadIDCardEvent event = new ReportReadIDCardEvent();
					event.setiDCardInfo(iDCardInfo);
					mReaderView.onReadIDCardSuccessed(event);
				}
				return true;
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_ReadBaseMsg fail(" + ret + ")");
			}
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "Please init reader");
		}
		return false;
	}

	public boolean ReadNewAppMsg() {
		if (mReader != null) {
			// 读卡成功，读追加信息，判断卡是否在位
			int ret = mReader.RTN_ReadNewAppMsg(new MoreAddrInfo());
			if (ret < 0) {// card removed
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "card off");
				ReportCardRemovedEvent reportCardRemovedEvent = new ReportCardRemovedEvent();
				EventBus.getDefault().post(reportCardRemovedEvent);
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "card on");
			}
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "Please init reader");
		}
		return false;
	}
	
	public void getSamId() {
		if (mReader != null) {
			byte[] data = new byte[32];
			// 读卡成功，读追加信息，判断卡是否在位
			int ret = mReader.SDT_GetSAMID(data);
			if (ret < 0) {// card removed
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "获取SAMID失败");
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SAMID：" + toHexStringNoSpace(data, ret));
			}
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "Please init reader");
		}
	}
	
	public void transmit() {
		if (mReader != null) {
			byte[] data = {0x05, 0x00, 0x00};
			// 读卡成功，读追加信息，判断卡是否在位
			byte[] ret = mReader.transmit(data);
			if (ret == null) {// card removed
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "透传失败");
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "透传返回数据：" + toHexStringNoSpace(ret, ret.length));
			}
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "Please init reader");
		}
	}
	
	public void testSuperSIMCard() {
		if(mReader != null) {
			long start = System.currentTimeMillis();
			long end;
			int ret = mReader.RTN_TypeASearch();
			if(ret < ErrorCode.SUCCESS) {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "找卡失败，ret：" + ret);
				return;
			} else {
				end = System.currentTimeMillis();
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "找卡成功，卡类型：" + ret + ", 耗时：" + (end - start) + "ms");
				start = end;
			}
			
			ret = mReader.RTN_TypeACpuActive();
			if(ret < ErrorCode.SUCCESS) {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "激活失败，ret：" + ret);
				return;
			} else {
				end = System.currentTimeMillis();
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "激活成功, 耗时：" + (end - start) + "ms");
				start = end;
			}
			
			byte[] apdu= {0x00, (byte)0xA4, 0x04, 0x00, 0x10, (byte)0xD1, 0x56, 0x00, 0x01, 0x01, (byte)0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, (byte)0xB0, 0x01, 0x52, 0x00};	//选择数字身份应用
			byte[] output = new byte[512];
			ret = mReader.RTN_TypeACpuAPDU(apdu, output);
			if(output[0] == (byte)0x90 && output[1] == 0x00) {
				apdu = new byte[]{0x00, (byte)0x86, 0x3F, 0x00, 0x00};
				ret = mReader.RTN_TypeACpuAPDU(apdu, output);
				if(ret <= 0) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "读取数字身份失败1");
					return;
				} else {
					//mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, ReaderLog.toHexString(output, ret));
					end = System.currentTimeMillis();
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "数字身份数据长度：" + ret+ ", 耗时：" + (end - start) + "ms");
					start = end;
				}
				
				apdu = new byte[]{0x00, (byte)0x86, 0x3F, 0x01, 0x00};
				ret = mReader.RTN_TypeACpuAPDU(apdu, output);
				if(ret <= 0) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "读取数字身份失败2");
					return;
				} else {
					end = System.currentTimeMillis();
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "数字身份数据长度：" + ret+ ", 耗时：" + (end - start) + "ms");
					start = end;
				}
				
				apdu = new byte[]{0x00, (byte)0xA4, 0x04, 0x00, 0x10, (byte)0xD1, 0x56, 0x00, 0x01, 0x01, 0x00, 0x01, 0x60, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00};
				ret = mReader.RTN_TypeACpuAPDU(apdu, output);
				if(ret <= 0) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "选择ISD失败");
					return;
				} else {
					end = System.currentTimeMillis();
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "选择ISD成功, 耗时：" + (end - start) + "ms");
					start = end;
				}
				
				apdu = new byte[]{(byte)0x80, (byte)0xCA, 0x00, 0x44, 0x00};
				ret = mReader.RTN_TypeACpuAPDU(apdu, output);
				if(ret <= 0) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "读取SEID失败");
					return;
				} else {
					Log.d(TAG, "SEID：" + ReaderLog.toHexString(output, ret));
					end = System.currentTimeMillis();
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "读取SEID成功, 耗时：" + (end - start) + "ms");
				}
				
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "选择数字身份应用失败，output:" + ReaderLog.toHexString(output, ret));
			}
		}
	}

	public boolean CloseDevice() {
		if (mReader != null) {
			// 读卡结束，关闭读卡器
			mReader.SDT_ClosePort();
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_ClosePort success");
			mReader = null;
		}
		if (mUsbPermissionMgr != null) {
			// 释放USB对象
			mUsbPermissionMgr.releaseMgr();
			mUsbPermissionMgr = null;
		}
		return true;
	}

	public void ReadCard(Context context) {
		// 初始化USB设备对象
		mUsbMgr = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		mUsbPermissionMgr = new UsbDevPermissionMgr(context, new UsbDevPermissionMgrCallback() {
			@Override
			public void onNoticeStr(String notice) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUsbDevReady(UsbDevice device) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUsbDevRemoved(UsbDevice device) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUsbRequestPermission() {
				// TODO Auto-generated method stub
			}

		});

		// 检查是否读卡器是否已连接
		boolean is_device_connected = mUsbPermissionMgr.initMgr();

		if (is_device_connected) {
			// 已经连接读卡器, 打开读卡器
			mReader = new USBImpl();
			UsbDevice usbDevice = mUsbPermissionMgr.getUsbDevice();
			int ret = mReader.SDT_OpenPort(mUsbMgr, usbDevice);
			if (ret >= ErrorCode.SUCCESS) {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_OpenPort success");
				// 打开设备成功，找卡
				byte[] sn = new byte[8];
				ret = mReader.SDT_FindIDCard(sn);
				if (ret == ErrorCode.SUCCESS) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_FindIDCard success");
					// 找卡成功，选卡
					ret = mReader.SDT_SelectIDCard(sn);
					if (ret == ErrorCode.SUCCESS) {
						mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_SelectIDCard success");
						// 选卡成功，读卡
						IDCardInfo iDCardInfo = new IDCardInfo();
						ret = mReader.RTN_ReadBaseMsg(iDCardInfo);
						if (ErrorCode.SUCCESS == ret) {
							// 读卡成功，卡的基本信息、照片已经解析并存储到iDCardInfo中
							Log.d("test", "读卡成功");
							mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_ReadBaseMsg success");
							if (mReaderView != null) {
								ReportReadIDCardEvent event = new ReportReadIDCardEvent();
								event.setiDCardInfo(iDCardInfo);
								mReaderView.onReadIDCardSuccessed(event);
							}

						} else {
							mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_ReadBaseMsg fail");
						}
					}
				}

				// 读卡结束，关闭读卡器
				mReader.SDT_ClosePort();

				// 释放USB对象
				mUsbPermissionMgr.releaseMgr();
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_OpenPort fail");
			}
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "设备未连接");
		}
	}
	
	public void ReadCard(DeviceParamBean deviceParamBean) {
		// 检查是否读卡器是否已连接
		 if(mUSReaderParas.isConnected == 0) {
			// 打开设备成功，找卡
			byte[] sn = new byte[8];
			int ret = mReader.SDT_FindIDCard(sn);
			if (ret == ErrorCode.SUCCESS) {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_FindIDCard success");
				// 找卡成功，选卡
				ret = mReader.SDT_SelectIDCard(sn);
				if (ret == ErrorCode.SUCCESS) {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_SelectIDCard success");
					// 选卡成功，读卡
					IDCardInfo iDCardInfo = new IDCardInfo();
					if(deviceParamBean.isRead_fp()) {
						ret = mReader.RTN_ReadBaseFPMsg(iDCardInfo);
					} else {
						ret = mReader.RTN_ReadBaseMsg(iDCardInfo);
					}
					
					if (ErrorCode.SUCCESS == ret) {
						// 读卡成功，卡的基本信息、照片已经解析并存储到iDCardInfo中
						mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_ReadBaseMsg success");
						if (mReaderView != null) {
							ReportReadIDCardEvent event = new ReportReadIDCardEvent();
							event.setiDCardInfo(iDCardInfo);
							mReaderView.onReadIDCardSuccessed(event);
						}

					} else {
						mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "RTN_ReadBaseMsg failed, ret = " + ret);
					}
				} else {
					mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_SelectIDCard failed, ret = " + ret);
				}
			} else {
				mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "SDT_FindIDCard failed, ret = " + ret);
			}
		} else {
			mReaderView.appendLog(AppendLogEvent.LOG_CODE_ANY, "未连接服务器");
		}
	}

	public IReaderView getReaderView() {
		return mReaderView;
	}
	
	public IReader getReader() {
		return mReader;
	}

	public void setReaderView(IReaderView readerView) {
		this.mReaderView = readerView;
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
}
