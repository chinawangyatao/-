package com.routon.plsy.reader.sdk.demo.model;

import android.R.string;

/**
 * 打开读卡器用到的参数
 * @author lihuili
 *
 */
public class DeviceParamBean {
	public final static int DEV_TYPE_INNER_OR_USB = 0; //内置串口 或者 内置CCID 或者 外接HID 或者 外界部标
	public final static int DEV_TYPE_BT = 1;     //蓝牙读卡器
	public final static int DEV_TYPE_SERIAL = 2; //串口读卡器,需要配置串口名称
	public final static int DEV_TYPE_USB = 3;    //USB读卡器
	public final static int DEV_TYPE_USB_SERIAL = 4;    //网络读卡器
	public final static int DEV_TYPE_UNKNOWN = 10;
	private int device_type; 
	private Object user_obj; //for BT: BluetoothDevice
	private boolean read_fp = false;
	private boolean read_b = false;
	private boolean read_a = false;
	private boolean read_b_cid = false;
	private boolean dual_channel = false; //是否开启双通道
	private int transfer_type = 1;
	private boolean use_cache = false;

	public boolean isUse_cache() {
		return use_cache;
	}
	public void setUse_cache(boolean use_cache) {
		this.use_cache = use_cache;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	private int port;

	public int getTransferType() {
		return transfer_type;
	}
	public void setTransferType(int transfer_type) {
		this.transfer_type = transfer_type;
	}
	public boolean isRead_b_cid() {
		return read_b_cid;
	}
	public void setRead_b_cid(boolean read_b_cid) {
		this.read_b_cid = read_b_cid;
	}
	public boolean isRead_b() {
		return read_b;
	}
	public void setRead_b(boolean read_b) {
		this.read_b = read_b;
	}
	public boolean isRead_a() {
		return read_a;
	}
	public void setRead_a(boolean read_a) {
		this.read_a = read_a;
	}
	public int getDevice_type() {
		return device_type;
	}
	/**
	 * 设置设备类型
	 * @param device_type DEV_TYPE_INNER_OR_USB  or DEV_TYPE_BT or DEV_TYPE_SERIAL or DEV_TYPE_HID
	 */
	public void setDevice_type(int device_type) {
		this.device_type = device_type;
	}
	public Object getUser_obj() {
		return user_obj;
	}
	public void setUser_obj(Object user_obj) {
		this.user_obj = user_obj;
	}
	public boolean isRead_fp() {
		return read_fp;
	}
	public void setRead_fp(boolean read_fp) {
		this.read_fp = read_fp;
	}
	public boolean isDual_channel() {
		return dual_channel;
	}
	public void setDual_channel(boolean dual_channel) {
		this.dual_channel = dual_channel;
	}
}
