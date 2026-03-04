package com.routon.plsy.reader.sdk.demo.view;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routon.plsy.reader.sdk.common.Info;
import com.routon.plsy.reader.sdk.common.Info.IDCardInfo;
import com.routon.plsy.reader.sdk.demo.R;

public class IDCardView extends FrameLayout {
    private TextView mTextViewName;
    private TextView mTextViewGender;
    private TextView mTextViewNationTitle;
    private TextView mTextViewNation;
    private TextView mTextViewYear;
    private TextView mTextViewVer;
    private TextView mTextViewAgencyCode;
    private TextView mTextViewMonth;
    private TextView mTextViewDay;
    private TextView mTextViewAddress;
    private TextView mTextViewIDNoTitle;
    private TextView mTextViewIDNo;
    private TextView mTextViewAgency;
    private TextView mTextViewExpireTitle;
    private TextView mTextViewExpire;
    private TextView mTextViewCardNo;
    private TextView mTextViewPassportNum;
    private TextView mTextViewIssuranceTimes;
    private TextView mTextViewIName;
    private TextView mTextViewOldID;
    private ImageView mImageViewPortrait;
    private ImageView mImageViewFpFlag;
    private LinearLayout linearLayoutChName, linearlayoutAgency, linearLayoutAddress, linearLayoutVer,
    		linearLayoutAgencyCode, linearlayoutGATPassportNum, linearlayoutGATIssuanceTimes,linearlayoutCardBodyNo, linearlayoutOldId;
    public IDCardView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public IDCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public IDCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IDCardView(Context context) {
        super(context);
    }

    @Override
    protected void onFinishInflate() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.idcard_view, this);
        initView();
        super.onFinishInflate();
    }
    
    private void initView(){
        mTextViewVer=(TextView)findViewById(R.id.textViewVer);
        mTextViewOldID=(TextView)findViewById(R.id.textViewOldIDNo);
        mTextViewAgencyCode=(TextView)findViewById(R.id.textViewAgencyCode);
        mTextViewPassportNum = (TextView) findViewById(R.id.textViewPassportNum);// 通行证号码
        mTextViewIssuranceTimes = (TextView) findViewById(R.id.textViewIssuanceTimes);// 签发次数
        mTextViewName = (TextView) findViewById(R.id.textViewName);
        mTextViewGender = (TextView) findViewById(R.id.textViewGender);
        mTextViewNationTitle = (TextView) findViewById(R.id.textViewNationTitle);
        mTextViewNation = (TextView) findViewById(R.id.textViewNation);
        mTextViewYear = (TextView) findViewById(R.id.textViewYear);
        mTextViewMonth = (TextView) findViewById(R.id.textViewMonth);
        mTextViewDay = (TextView) findViewById(R.id.textViewDay);
        mTextViewAddress = (TextView) findViewById(R.id.textViewAddress);
        mTextViewIDNoTitle = (TextView) findViewById(R.id.textViewIDNoTitle);
        mTextViewIDNo = (TextView) findViewById(R.id.textViewIDNo);
        mTextViewAgency = (TextView) findViewById(R.id.textViewAgency);
        mTextViewExpireTitle = (TextView) findViewById(R.id.textViewExpireTitle);
        mTextViewExpire = (TextView) findViewById(R.id.textViewExpire);
        mImageViewPortrait = (ImageView) findViewById(R.id.imageViewPortrait);
        mImageViewFpFlag = (ImageView) findViewById(R.id.imageViewFpFlag);
        mTextViewCardNo = (TextView) findViewById(R.id.textViewCardNo);
        linearLayoutChName = (LinearLayout) findViewById(R.id.linearLayoutChName);
        mTextViewIName = (TextView) findViewById(R.id.textViewIName);
        linearlayoutAgency = (LinearLayout) findViewById(R.id.linearLayoutAgency);
        linearLayoutAddress = (LinearLayout) findViewById(R.id.linearLayoutAddress);
        linearLayoutVer = (LinearLayout) findViewById(R.id.linearLayoutVer);
        linearLayoutAgencyCode = (LinearLayout) findViewById(R.id.linearLayoutAgencyCode);
        linearlayoutGATPassportNum = (LinearLayout) findViewById(R.id.GATPassportNum);
        linearlayoutGATIssuanceTimes = (LinearLayout) findViewById(R.id.GATIssuanceTimes);
        linearlayoutCardBodyNo = (LinearLayout) findViewById(R.id.card_no_linear_layout);
        linearlayoutOldId = (LinearLayout) findViewById(R.id.old_id_linear_layout);
        
        linearlayoutAgency.setVisibility(View.VISIBLE);
        linearLayoutAddress.setVisibility(View.VISIBLE);
        mTextViewNationTitle.setVisibility(View.VISIBLE);
        linearLayoutVer.setVisibility(View.GONE);
        linearLayoutAgencyCode.setVisibility(View.GONE);
        linearlayoutGATPassportNum.setVisibility(View.GONE);// 通行证号码
        linearlayoutGATIssuanceTimes.setVisibility(View.GONE);// 签发次数
        linearLayoutChName.setVisibility(View.GONE);
        linearlayoutCardBodyNo.setVisibility(View.GONE);
        linearlayoutOldId.setVisibility(View.GONE);
    }

    public void setIDCardCIDViewVisibility(int visibility){
        linearlayoutCardBodyNo.setVisibility(visibility);
    }
    
    public void updateIDCardInfo(IDCardInfo idCardInfo, String cid,  boolean display) {
        if (display) {
            if (idCardInfo.cardType == Info.ID_CARD_TYPE_CHINA) {// 身份证
                linearlayoutAgency.setVisibility(View.VISIBLE);
                linearLayoutAddress.setVisibility(View.VISIBLE);
                mTextViewNationTitle.setVisibility(View.VISIBLE);
                linearLayoutVer.setVisibility(View.GONE);
                linearLayoutAgencyCode.setVisibility(View.GONE);
                linearlayoutGATPassportNum.setVisibility(View.GONE);// 通行证号码
                linearlayoutGATIssuanceTimes.setVisibility(View.GONE);// 签发次数
                linearLayoutChName.setVisibility(View.GONE);
                mTextViewName.setText(idCardInfo.name);
                mTextViewNationTitle.setText(R.string.nation);
                mTextViewIDNoTitle.setText(R.string.idno);
                mTextViewExpireTitle.setText(R.string.expire);
            } else if (idCardInfo.cardType == Info.ID_CARD_TYPE_FOREIGN 
            		|| idCardInfo.cardType == Info.ID_CARD_TYPE_FOREIGN_V2023) {// 外国人证
                linearlayoutAgency.setVisibility(View.GONE);
                linearLayoutAddress.setVisibility(View.GONE);
                linearlayoutGATPassportNum.setVisibility(View.GONE);// 通行证号码
                linearlayoutGATIssuanceTimes.setVisibility(View.GONE);// 签发次数
                linearLayoutChName.setVisibility(View.VISIBLE);
                mTextViewNationTitle.setVisibility(View.VISIBLE);
                linearLayoutVer.setVisibility(View.VISIBLE);
                linearLayoutAgencyCode.setVisibility(View.VISIBLE);
                
                mTextViewIName.setText(idCardInfo.name);
                if(idCardInfo.cardType == Info.ID_CARD_TYPE_FOREIGN_V2023) {
                	if(idCardInfo.name.isEmpty())
                		mTextViewIName.setText("无中文姓名");

                	if(idCardInfo.oldId != null && !idCardInfo.oldId.isEmpty()) {
                		linearlayoutOldId.setVisibility(View.VISIBLE);
                		//((TextView)findViewById(R.id.textViewOldIDNo)).setText(idCardInfo.oldId);
                		mTextViewOldID.setText(idCardInfo.oldId);
                	}
                }
                if(idCardInfo.englishName.length() > 25) {
            		idCardInfo.englishName = idCardInfo.englishName.substring(0, 25) + "\r\n" + idCardInfo.englishName.substring(25);
            		if(idCardInfo.englishName.length() > 50) {
                		idCardInfo.englishName = idCardInfo.englishName.substring(0, 50) + "\r\n" + idCardInfo.englishName.substring(50);
        			}
    			}
                mTextViewName.setText(idCardInfo.englishName);
                mTextViewNationTitle.setText(R.string.nation_ex);
                mTextViewIDNoTitle.setText(R.string.idno_foreign);
                mTextViewExpireTitle.setText(R.string.expire_foreign);
                mTextViewAgencyCode.setText(idCardInfo.agencyCode);
                mTextViewVer.setText(idCardInfo.ver);
            } else if (idCardInfo.cardType == Info.ID_CARD_TYPE_GAT) {// 港澳通行证
                mTextViewNationTitle.setVisibility(View.GONE);
                linearLayoutVer.setVisibility(View.GONE);
                linearLayoutAgencyCode.setVisibility(View.GONE);
                linearLayoutAddress.setVisibility(View.VISIBLE);
                linearlayoutGATPassportNum.setVisibility(View.VISIBLE);// 通行证号码
                linearlayoutGATIssuanceTimes.setVisibility(View.VISIBLE);// 签发次数
                mTextViewName.setText(idCardInfo.name);
                mTextViewExpireTitle.setText(R.string.expire);
                mTextViewPassportNum.setText(idCardInfo.passportNum);
                mTextViewIssuranceTimes.setText(idCardInfo.issuanceTimes);
            }
            if(!TextUtils.isEmpty(cid)){
                mTextViewCardNo.setText(cid);
            }
            
            mTextViewGender.setText(idCardInfo.gender);
            mTextViewNation.setText(idCardInfo.nation);
            mTextViewYear.setText(idCardInfo.birthday.substring(0, 4));
            mTextViewMonth.setText(idCardInfo.birthday.substring(4, 6));
            mTextViewDay.setText(idCardInfo.birthday.substring(6, 8));
            if (idCardInfo.address != null) {
                mTextViewAddress.setText(idCardInfo.address);
            }
            mTextViewIDNo.setText(idCardInfo.id);
            if (idCardInfo.agency != null) {
                mTextViewAgency.setText(idCardInfo.agency);
            }
            if (isExpire(idCardInfo.expireEnd)) {
                mTextViewExpire.setTextColor(Color.RED);
                mTextViewExpire.setText(idCardInfo.expireStart + " - " + idCardInfo.expireEnd + "(过期)");
            } else {
                mTextViewExpire.setTextColor(Color.BLACK);
                mTextViewExpire.setText(idCardInfo.expireStart + " - " + idCardInfo.expireEnd);
            }
            //mImageViewPortrait.setImageBitmap(idCardInfo.photo);
            updatePhoto(idCardInfo.photo);
            if(idCardInfo.fpData!=null){
                mImageViewFpFlag.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint_flag));
            }else{
            	mImageViewFpFlag.setImageDrawable(null);
            }
        } else {
            linearlayoutAgency.setVisibility(View.VISIBLE);
            linearLayoutAddress.setVisibility(View.VISIBLE);
            mTextViewNationTitle.setVisibility(View.VISIBLE);
            linearLayoutVer.setVisibility(View.GONE);
            linearLayoutAgencyCode.setVisibility(View.GONE);
            linearlayoutGATPassportNum.setVisibility(View.GONE);// 通行证号码
            linearlayoutGATIssuanceTimes.setVisibility(View.GONE);// 签发次数
            linearLayoutChName.setVisibility(View.GONE);
            mTextViewNationTitle.setText(R.string.nation);
            mTextViewAgencyCode.setText("");
            mTextViewVer.setText("");
            mTextViewName.setText("");
            mTextViewGender.setText("");
            mTextViewNation.setText("");
            mTextViewYear.setText("");
            mTextViewMonth.setText("");
            mTextViewDay.setText("");
            mTextViewAddress.setText("");
            mTextViewIDNoTitle.setText(R.string.idno);
            mTextViewIDNo.setText("");
            mTextViewAgency.setText("");
            mTextViewExpire.setText("");
            mTextViewPassportNum.setText("");
            mTextViewIssuranceTimes.setText("");
            mTextViewCardNo.setText("");
            mTextViewOldID.setText("");
            //mImageViewPortrait.setImageBitmap(null);
            updatePhoto(null);
            mImageViewFpFlag.setImageDrawable(null);
        }
    }
    
    private void updatePhoto(Bitmap photo) {
    	if(mImageViewPortrait == null) return;
    	Drawable drawable = mImageViewPortrait.getDrawable();
    	if(drawable != null && drawable instanceof BitmapDrawable) {
    		BitmapDrawable bitmapDrawable = (BitmapDrawable)drawable;
    		Bitmap bitmap = bitmapDrawable.getBitmap();
    		if(bitmap != null && !bitmap.isRecycled()) {
    			mImageViewPortrait.setImageBitmap(null);
    			bitmap.recycle();
    		}
    	}
    	
    	mImageViewPortrait.setImageBitmap(photo);
    }
    
    public void updateACardInfo(byte[] card_no, boolean display) {
        if (display) {
            linearLayoutAddress.setVisibility(View.VISIBLE);
            linearLayoutChName.setVisibility(View.GONE);
            mTextViewNationTitle.setText(R.string.nation);  
            mTextViewName.setText("");
            mTextViewGender.setText("");
            mTextViewNation.setText("");
            mTextViewYear.setText("");
            mTextViewMonth.setText("");
            mTextViewDay.setText("");
            mTextViewAddress.setText("");               
            mTextViewIDNoTitle.setText(R.string.idno_A);
            
            String cardANo = "";
            //cardANo=String.format("%02X%02X%02X%02X", data[0], data[1], data[2], data[3]);
            for(int i = 0; i<card_no.length; i++){
                cardANo += String.format("%02X",card_no[i]);
            }
            mTextViewIDNo.setText(cardANo);
            mTextViewAgency.setText("");        
            mTextViewExpire.setText("");
            mImageViewPortrait.setImageBitmap(null);
        } else {
            mTextViewAgencyCode.setText("");
            mTextViewVer.setText("");
            linearLayoutAddress.setVisibility(View.VISIBLE);
            linearLayoutChName.setVisibility(View.GONE);
            mTextViewNationTitle.setText(R.string.nation);
            mTextViewName.setText("");
            mTextViewGender.setText("");
            mTextViewNation.setText("");
            mTextViewYear.setText("");
            mTextViewMonth.setText("");
            mTextViewDay.setText("");
            mTextViewAddress.setText("");
            mTextViewIDNoTitle.setText(R.string.idno);
            mTextViewIDNo.setText("");
            mTextViewAgency.setText("");
            mTextViewExpire.setText("");
            mTextViewPassportNum.setText("");
            mTextViewIssuranceTimes.setText("");
            mImageViewPortrait.setImageBitmap(null);
        }
    }
    /**
     * 判断身份证是否过期
     * 
     * @param expireEnd
     * @return
     */
    private boolean isExpire(String expireEnd) {
        if (expireEnd.startsWith("长期")) {
            return false;
        } else {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
            Calendar cal = Calendar.getInstance();
            try {
                cal.setTime(format.parse(expireEnd));
            } catch (ParseException e) {
                e.printStackTrace();
                return false;
            }
            cal.roll(Calendar.DAY_OF_MONTH, 1);
            return cal.getTime().compareTo(new Date()) < 0;
        }
    }
    
}
