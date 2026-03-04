package com.routon.plsy.reader.sdk.demo.model;


/**
 * 发送完成事件，使用eventbus发出，应用端注册eventbus监听事件。
 *
 */
public class AppendLogEvent {
	private int code = LOG_CODE_ANY;
	private String log;
	
	public static final int LOG_CODE_ANY = 0;
	public static final int LOG_CODE_SAMID = 1;
	public static final int LOG_CODE_REQ_USB_PERMISSION = 2;
	public static final int LOG_CODE_STATISTICS = 3;
	
	/**
	 * 查询日志代码
	 * @return
	 */
	public int getCode() {
		return code;
	}
	
	/**
	 * 设置日志代码
	 * @param code
	 */
	public void setCode(int code) {
		this.code = code;
	}

	public String getLog() {
		return log;
	}

	public void setLog(String log) {
		this.log = log;
	}

}
