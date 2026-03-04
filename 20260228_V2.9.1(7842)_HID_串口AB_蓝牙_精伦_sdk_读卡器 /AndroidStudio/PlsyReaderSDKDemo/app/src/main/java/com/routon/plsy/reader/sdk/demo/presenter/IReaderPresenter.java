package com.routon.plsy.reader.sdk.demo.presenter;

import com.routon.plsy.reader.sdk.demo.model.DeviceParamBean;
import com.routon.plsy.reader.sdk.intf.IReader;

/**
 * 读卡表示层接口
 * @author lihuili
 *
 */
public interface IReaderPresenter {
	void setReader(IReader reader);
	void startReadcard(DeviceParamBean devParamBean);
	void setDeviceParam(DeviceParamBean devParamBean);
	void stopReadcard();
	void release();
}

