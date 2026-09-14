package dev.glass.browser;

import android.app.Activity;
import android.os.*;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.*;
import android.graphics.Color;

/** Visible launcher entry point used by both the home screen and HyperOS sidebar. */
public class MainActivity extends Activity {
    private TextView message;
    private boolean starting, askingNotification, failed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setPadding(28,36,28,28);content.setBackgroundColor(0xff17212b);
        TextView title=new TextView(this);title.setText("Glass Browser 0.5\n透明ウィンドウ");title.setTextSize(23);title.setTextColor(Color.WHITE);content.addView(title);
        message=new TextView(this);message.setTextSize(16);message.setTextColor(Color.WHITE);message.setPadding(0,24,0,24);content.addView(message);
        Button grant=new Button(this);grant.setText("重ねて表示する権限を設定");grant.setOnClickListener(v->{
            failed=false;
            try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
            catch(RuntimeException e){message.setText("端末の設定 → アプリ → Glass Browser → 他のアプリの上に表示、を許可してください。");}
        });content.addView(grant);
        Button launch=new Button(this);launch.setText("透明ウィンドウを開く");launch.setOnClickListener(v->{failed=false;begin();});content.addView(launch);
        Button stop=new Button(this);stop.setText("ウィンドウを終了");stop.setOnClickListener(v->{stopService(new Intent(this,BrowserService.class));finish();});content.addView(stop);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(scroll);
    }
    @Override protected void onResume(){super.onResume();if(!failed&&!askingNotification)begin();}
    private void begin() {
        if(starting||isFinishing())return;
        if(!Settings.canDrawOverlays(this)) {
            message.setText("初回は「他のアプリの上に表示」を許可してください。設定画面にアプリ一覧が出たらGlass Browserを選びます。\n\n許可後はサイドバーやホームから起動すると、透明ウィンドウを表示してこの画面を閉じます。");return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("notificationAsked",false)) {
            getPreferences(0).edit().putBoolean("notificationAsked",true).apply();askingNotification=true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9);return;
        }
        starting=true;message.setText("透明ウィンドウを開いています…");
        ResultReceiver result=new ResultReceiver(handler){@Override protected void onReceiveResult(int code,Bundle data){
            if(isFinishing()||isDestroyed())return;
            starting=false;
            if(code==1)finish();
            else{failed=true;message.setText("開始できませんでした。\n"+(data==null?"":data.getString("message",""))+"\n\n重ねて表示する権限を確認してから、もう一度お試しください。");}
        }};
        try{startForegroundService(new Intent(this,BrowserService.class).putExtra("result",result));}
        catch(RuntimeException e){starting=false;failed=true;message.setText("開始できませんでした: "+e.getClass().getSimpleName()+"\n"+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==9){askingNotification=false;begin();}}
}
