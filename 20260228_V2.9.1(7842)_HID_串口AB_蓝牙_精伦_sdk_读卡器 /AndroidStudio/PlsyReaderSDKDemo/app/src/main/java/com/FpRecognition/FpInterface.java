package com.FpRecognition;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.FpRecognition.HostUsb;

public class FpInterface {
    static final String TAG = "FpInterface";
    //****************************************************************************************************
	static 
	{
		try{
			System.loadLibrary("biofp_e_lapi");
		}
		catch(UnsatisfiedLinkError e) {
			Log.e("FpInterface","biofp_e_lapi",e);
		}
	}
	//****************************************************************************************************
	public static final int VID = 0x28E9;//0x0483;
	public static final int PID = 0x028F;//0x5710;
	private static HostUsb m_usbHost = null;
	private static int m_hUSB = 0;
    public static final int MSG_OPEN_DEVICE = 0x10;
    public static final int MSG_CLOSE_DEVICE = 0x11;
    public static final int MSG_BULK_TRANS_IN = 0x12;
    public static final int MSG_BULK_TRANS_OUT = 0x13;
	//****************************************************************************************************
	public static final int WIDTH  = 256;
	public static final int HEIGHT  = 360;
	public static final int FPDEF_RECORD_SIZE = 512;
    public static final int FPDEF_CHECK_SCORE = 50;
    public static final int FPDEF_QUALITY_SCORE = 50;
    public static final int FPDEF_MATCH_SCORE = 45;
    //****************************************************************************************************
    public static final int TRUE = 1;
    public static final int FALSE = 0;
    private static Context m_content = null;
    //****************************************************************************************************
	private static int CallBack (int message, int notify, int param, Object data)
	{
		switch (message) {
		case MSG_OPEN_DEVICE:
			m_usbHost = new HostUsb ();
			if (!m_usbHost.AuthorizeDevice(m_content,VID,PID)) {
				m_usbHost = null;
				return 0;
			}

			m_usbHost.WaitForInterfaces(); 
			m_hUSB = m_usbHost.OpenDeviceInterfaces();
			if (m_hUSB <= 0) {
				m_usbHost = null;
				return 0;
			}
			return m_hUSB;
		case MSG_CLOSE_DEVICE:
			if (m_usbHost != null) {
				m_usbHost.CloseDeviceInterface();
				m_hUSB = -1;
				m_usbHost = null;
			}
			return 1;
		case MSG_BULK_TRANS_IN:
			m_usbHost.USBBulkReceive((byte[])data,notify,param);
			return 1;
		case MSG_BULK_TRANS_OUT:
            m_usbHost.USBBulkSend((byte[])data,notify,param);
			return 1;
		}
		return 0;
	}
    //****************************************************************************************************
	public FpInterface(Context a) {
		m_content = a;
	}
	//****************************************************************************************************
	public void POWER_ON() {
		Intent intent = new Intent();
		intent.setAction("android.intent.action.ChangeHotonReceiver");
		m_content.sendBroadcast(intent);
		intent.setAction("android.intent.action.lightonReceiver");
		m_content.sendBroadcast(intent);

		try {
			Thread.sleep(10);//Thread.sleep(3000); //Modified in 2022-09-02
		} catch (InterruptedException e) {}
    }
    //****************************************************************************************************
	public void POWER_OFF() {
		Intent intent = new Intent();
		intent.setAction("android.intent.action.ChangeHotoffReceiver");
		m_content.sendBroadcast(intent);
		intent.setAction("android.intent.action.lightoffReceiver");
		m_content.sendBroadcast(intent);
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {}
	}
	//------------------------------------------------------------------------------------------------//
	// Purpose   : This function initializes the Fingerprint Recognition Library.
 	// Function  : OpenDevice
	// Arguments : void
	// Return    : long  
	//			     If successful, return handle of device, else 0. 	
	//------------------------------------------------------------------------------------------------//
	public native long OpenDevice();
	public long OpenDeviceEx()
	{
		long ret = 0;
		if(m_usbHost == null){
			POWER_ON();
		}
		for (int i = 0; i < 10; i++)
		{
			ret = OpenDevice();
			if (ret > 0) break;
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {}
			
		}
			
       	if (ret <= 0) POWER_OFF();
		return ret;
	}
	//------------------------------------------------------------------------------------------------//
	// Purpose   : This function finalizes the Fingerprint Recognition Library.
 	// Function  : CloseDevice
	// Arguments : 
	//      (In?? : long device : used with the return of function "OpenDevice()"
	// Return    : int
	//			      If successful, return 1, else 0 	
	//------------------------------------------------------------------------------------------------//
	public  native int CloseDevice(long device);
	public int CloseDeviceEx(long device)
	{
		int ret;
		ret = CloseDevice(device);
        POWER_OFF();
		return ret;
	}
	//------------------------------------------------------------------------------------------------//
	// Purpose   : This function returns image captured from this device.
	// Function  : GetImage
	// Arguments : 
	//      (In?? : long device : used with the return of function "OpenDevice()"
	//  (In/Out?? : byte[] image : image captured from this device
	// Return    : int
	//			      If successful, return 1, else 0 	
	//------------------------------------------------------------------------------------------------//
	public native int GetImage(long device, byte[] image);
	//------------------------------------------------------------------------------------------------//
	// Purpose   : This function checks whether finger is on sensor of this device or not.
	// Function  : CheckFinger
	// Arguments : 
	//      (In?? : long device : used with the return of function "OpenDevice()"
	//		(In) : byte[] image : image return by function "GetImage()"
	// Return    : int 
	//				   return percent value measured fingerprint on sensor(0~100). 	
	//------------------------------------------------------------------------------------------------//
	public native int CheckFinger(long device, byte[] image);
	//------------------------------------------------------------------------------------------------//
	// Purpose   : This function gets the quality value of fingerprint raw image. 
	// Function  : GetImageQuality
	// Arguments : 
	//      (In??: long device : used with the return of function "OpenDevice()"
	//		(In) : byte[] image : image return by function "GetImage()"
	// Return    : int : 
	//				   return quality value(0~100) of fingerprint raw image. 	
	//------------------------------------------------------------------------------------------------//
	public native int GetImageQuality(long device, byte[] image);
	//------------------------------------------------------------------------------------------------//
	// Purpose   : This function extracts the feature from the raw image. 
	// Function  : ExtractFeature
	// Arguments : 
	//      (In??: long device : used with the return of function "OpenDevice()"
	//		(In) : byte[] image : image return by function "GetImage()"
	//	(In/Out) : byte[] feature : feature extracted from image.
	// Return    : int : 
	//				   If this function successes, return none-zero, else 0. 	
	//------------------------------------------------------------------------------------------------//
	public native int ExtractFeature(long device, byte[] image, byte[] feature);
	//------------------------------------------------------------------------------------------------//
	// Purpose   : This function matches two features and returns similar match score.
	//             This function is for 1:1 Matching and only used in fingerprint verification. 
	// Function  : MatchFeature
	// Arguments : 
	//          (In??: long device : used with the return of function "OpenDevice()"
	//			(In) : byte[] feature1 : feature to match : 
	//			(In) : byte[] feature2 : feature to be matched
	// Return    : int 
	//					return similar match score(0~100) of two fingerprint features.
	//------------------------------------------------------------------------------------------------//
	public native int MatchFeature(long device, byte[] feature1, byte[] feature2);
	//------------------------------------------------------------------------------------------------//
}
