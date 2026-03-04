package com.routon.plsy.reader.sdk.demo.model;

/**
 *  报告启动读卡结果
 * 
 */
public class ReportStartReadcardResultEvent {
	private int error_code;
	private boolean have_inner_reader;

	public int getError_code() {
		return error_code;
	}

	public void setError_code(int error_code) {
		this.error_code = error_code;
	}

	public boolean isHave_inner_reader() {
		return have_inner_reader;
	}

	public void setHave_inner_reader(boolean have_inner_reader) {
		this.have_inner_reader = have_inner_reader;
	}
}
