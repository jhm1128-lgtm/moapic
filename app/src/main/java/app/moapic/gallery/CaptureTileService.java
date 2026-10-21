package app.moapic.gallery;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.*;

public final class CaptureTileService extends TileService {
    @Override public void onStartListening(){
        Tile tile=getQsTile();if(tile!=null){tile.setState(Tile.STATE_INACTIVE);tile.setLabel("모아픽 캡처");tile.updateTile();}
    }
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is the only API on Android 10–13; Android 14+ always uses PendingIntent below.
    @Override public void onClick(){
        super.onClick();
        unlockAndRun(()->{
            Intent open=new Intent(this,CaptureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,73,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(open);
        });
    }
}
