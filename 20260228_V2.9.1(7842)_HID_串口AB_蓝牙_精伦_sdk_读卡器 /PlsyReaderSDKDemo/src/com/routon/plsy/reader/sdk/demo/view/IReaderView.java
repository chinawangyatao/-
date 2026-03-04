package com.routon.plsy.reader.sdk.demo.view;

import android.content.Context;

import com.routon.plsy.reader.sdk.common.Info.ReaderParas;
import com.routon.plsy.reader.sdk.demo.model.ReportReadIDCardEvent;

/**
 * 读卡视图层接口
 * @author lihuili
 *
 */
public interface IReaderView {
	Context getContext();
	void onReadIDCardSuccessed(ReportReadIDCardEvent event);
	void onReadIDCardFailed();
	void onCardRemoved();
	void appendLog(int code, String log);
	void onGotStartReadcardResult(int error_code, boolean has_inner_reader);
	void onReadACardSuccessed(byte[] card_sn);
	void onReaderStopped();
	
	//更新网络读卡器的参数显示
	void updateParasUI(ReaderParas paras);
}
