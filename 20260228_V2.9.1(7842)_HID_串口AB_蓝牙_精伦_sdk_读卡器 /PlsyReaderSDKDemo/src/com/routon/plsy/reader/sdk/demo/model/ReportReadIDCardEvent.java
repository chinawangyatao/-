package com.routon.plsy.reader.sdk.demo.model;

import com.routon.plsy.reader.sdk.common.Info.IDCardInfo;

/**
 * 报告读身份证结果
 *
 */
public class ReportReadIDCardEvent {
	/**
	 * 读卡成功还是失败
	 */
	private boolean isSuccess = false;
	/**
	 * 读卡任务上报的读卡结果@see IDCardInfo
	 */
	private IDCardInfo iDCardInfo;	
	private String cid;//卡体管理号
	/**
	 * 查询卡体管理号
	 * @return
	 */
	public String getCid() {
		return cid;
	}
	/**
	 * 设置卡体管理号
	 * @param cid 卡体管理号
	 */
	public void setCid(String cid) {
		this.cid = cid;
	}
	public boolean isSuccess() {
		return isSuccess;
	}
	public void setSuccess(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}
	public IDCardInfo getiDCardInfo() {
		return iDCardInfo;
	}
	public void setiDCardInfo(IDCardInfo iDCardInfo) {
		this.iDCardInfo = iDCardInfo;
	}
}
