package app.moapic.gallery;

import android.app.*;
import android.content.*;
import android.media.projection.*;
import android.os.*;
import android.widget.Toast;

/** Dedicated entry for quick-settings and launcher shortcuts: leaves the gallery closed. */
public final class CaptureActivity extends Activity {
    private static final int CONSENT=71;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)return;
        if(CaptureService.running){Toast.makeText(this,"이미 캡처 중이에요.",Toast.LENGTH_SHORT).show();finish();return;}
        MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
        Intent request=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();
        startActivityForResult(request,CONSENT);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==CONSENT){
            if(result==RESULT_OK&&data!=null){
                try{startForegroundService(new Intent(this,CaptureService.class).putExtra("code",result).putExtra("data",data).putExtra("delay",1500));}
                catch(Exception e){Toast.makeText(this,"캡처를 시작하지 못했어요. 다시 시도해 주세요.",Toast.LENGTH_LONG).show();}
            }
            finish();
        }
    }
}
