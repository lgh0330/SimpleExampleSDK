package com.obd.service;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.comobd.zyobd.api.OBDAPI;
import com.obd.app.bean.JsonMsg;
import com.obd.observer.BObserver;
import com.obd.observer.DRObserver;
import com.obd.observer.RSObserver;
import com.obd.simpleexample.StatusInface;
import com.obd.simpleexample.StatusVehicle;
import com.obd.utils.MyLog;
import com.obd.utils.QuickShPref;
import com.obd.widget.GetLoaction;
import com.obd.widget.NetWork;
import com.umeng.analytics.MobclickAgent;

import de.greenrobot.event.EventBus;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

public class DataSyncService extends Service{
	
	private static String TAG = "DataSyncService";
	private GetLoaction mGetLoaction = new GetLoaction();
	NetWork mNetWork = new NetWork();
	
    public class LocalBinder extends Binder {
        public DataSyncService getService() {
            return DataSyncService.this;
        }
    }

	@Override
	public void onCreate() {
		super.onCreate();
		 Log.i(TAG, "onCreate");
		 MobclickAgent.onResume(this);
		 StatusInface.init(this);
		 initOBD();
		 locationInit();
		openADB();
		getIMEI();
		initSingle();
		
		mNetWork.start();
	}
	
	private void initSingle(){
		MyPhoneStateListener MyListener   = new MyPhoneStateListener();  
		TelephonyManager  tel       = ( TelephonyManager )getSystemService(Context.TELEPHONY_SERVICE);  
        tel.listen(MyListener ,PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        
        mGetLoaction.uploadBattery(0);
	}

    @Override
    public void onDestroy() {
        mLocClient.stop();
        MobclickAgent.onPause(this);
        StatusInface.destory(this);
        startService(new Intent(this, DataSyncService.class));
    }
    private final IBinder mBinder = new LocalBinder();
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        if(intent!=null && intent.getExtras()!=null){

        }
        return START_STICKY;
    }
	
    LocationClient mLocClient;
    MyLocationListenner myListener= new MyLocationListenner();
    Handler mHandler = new Handler();
    // 定位初始化
    private void locationInit(){
		mLocClient = new LocationClient(this);
		mLocClient.registerLocationListener(myListener);
		LocationClientOption option = new LocationClientOption();
		option.setOpenGps(true);// 打开gps
		option.setCoorType("bd09ll"); // 设置坐标类型
		option.setScanSpan(30*1000);
		mLocClient.setLocOption(option);
		
		mHandler.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				mLocClient.start(); 
			}
		}, 0*1000);
		
		Log.d(TAG,"百度定位 初始化成功");
    }
	public class MyLocationListenner implements BDLocationListener {
		@Override
		public void onReceiveLocation(BDLocation location) {
			if (location == null)	{Log.e(TAG,"百度定位失败"); return;}
			Log.d(TAG,"百度定位成功:"+location.getLongitude()+","+location.getLatitude());
			mGetLoaction.uploadPos(location);
			saveLastPos(location);

		}
		public void onReceivePoi(BDLocation poiLocation) {}
	}
	
	public void saveLastPos(BDLocation location){
		QuickShPref.putValueObject(QuickShPref.LAT, (float)(location.getLatitude()));
		QuickShPref.putValueObject(QuickShPref.LON, (float)(location.getLongitude()));
		StatusInface.getInstance().getTime();
		MyLog.D("百度定位地址保存成功");
	}
	public static void openADB(){
		new Thread(){
			@Override
			public void run() {
				super.run();
				
				String url = "http://127.0.0.1/goform/debug?action=adb&item=1";
				try{
					HttpResponse resp = getHttpResponse(url);
					
					if(resp != null)
						Log.e(TAG,"openADB--------resposeCode:"+resp.getStatusLine().getStatusCode());
					else
						Log.e(TAG,"openADB--------resposeCode null");
					
					url = "http://127.0.0.1/goform/CommConfig?cmd=TestSuite&action=gpstest&item=start&subitem=0";
					resp = getHttpResponse(url);
					
					if(resp != null)
						Log.e(TAG,"openGPS--------resposeCode:"+resp.getStatusLine().getStatusCode());
					else
						Log.e(TAG,"openGPS--------resposeCode null");
					
				}catch (Exception e) {
					e.printStackTrace();
					Log.e(TAG,"openADB or GPS--------exception-------------------");
				}
				
			}
		}.start();
	}
	
	public static HttpResponse getHttpResponse(String url) throws ClientProtocolException, IOException{
		HttpGet httpget = new HttpGet(url);
		HttpClient client = new DefaultHttpClient();
		
		HttpResponse resp = client.execute(httpget);
		
		return resp;
	}
	private StatusVehicle sv;
	private void initOBD(){
		Log.d(TAG, "initOBD");
		sv = new StatusVehicle();
		
		
		OBDAPI obdapi = OBDAPI.getInstance(this.getApplicationContext(),sv);
		RSObserver rsObserver = new RSObserver(obdapi);
//		BObserver bObserver = new BObserver(obdapi);
		DRObserver drObserver = new DRObserver(obdapi);
		
		obdapi.initObserver();
		Log.d(TAG, "initOBD 成功");
	}
	
	public String getIMEI(){
		String imei = QuickShPref.getString(QuickShPref.IEMI);
		
		if(imei == null){
			imei =((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
			Log.d("ieme", imei);
			QuickShPref.putValueObject(QuickShPref.IEMI, imei);
			mGetLoaction.mIMEI = imei;
			StatusInface.getInstance().mIEMI = imei;
			
		}

		return imei;
	}
	
	private class MyPhoneStateListener extends PhoneStateListener{  
      /* Get the Signal strength from the provider, each tiome there is an update  从得到的信号强度,每个tiome供应商有更新*/  
      @Override
      public void onSignalStrengthsChanged(SignalStrength signalStrength){
         super.onSignalStrengthsChanged(signalStrength);  
         //MyLog.D("signalStrength.getGsmSignalStrength()="+signalStrength.getGsmSignalStrength());
         mGetLoaction.setGSMsingle(signalStrength.getGsmSignalStrength());
      }
	}
	
	public void onEventMainThread(String result) {
		MyLog.D("onEventMainThread String ret="+result);
		StatusInface.getInstance().vehicleResult(result);
	}
	public void onEventMainThread(JsonMsg msg) {
		switch (msg.what) {
		case 2:
			StatusInface.getInstance().DROinface(msg.obj);
			break;
		case 1:
			StatusInface.getInstance().RSOinface(msg.obj);
			break;
		default:
			break;
		}
		
	}
	
	public static void postEvent(int what,JSONObject json){
		JsonMsg msg = new JsonMsg();
		msg.what = what;
		msg.obj = json;
		EventBus.getDefault().post(msg);
	}
	
}
