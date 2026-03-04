package com.routon.plsy.reader.sdk.demo.model;

/**
 * 报告读到A卡序号
 *
 */
public class ReportReadACardEvent {
	/**
	 * 读卡成功还是失败
	 */
	private boolean isSuccess = false;
	/**
	 * 读卡任务上报的A卡序号
	 */
	private byte[] card_sn;
	public boolean isSuccess() {
		return isSuccess;
	}
	public void setSuccess(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}
	/**
	 * 查询A卡序号
	 * @return
	 */
	public byte[] getCard_sn() {
		return card_sn;
	}
	/**
	 * 设置A卡序号
	 * @param card_sn
	 */
	public void setCard_sn(byte[] card_sn) {
		this.card_sn = card_sn;
	}	
}
