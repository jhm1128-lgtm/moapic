package app.moapic.gallery;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** OS-confirmed permanent removal; Android 10 requires per-file consent and retry. */
public final class DeleteCoordinator {
    public static final int REQUEST = 61;
    private final Activity activity;
    private final MoapicApp app;
    private final ExecutorService worker;
    private final Runnable changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ArrayList<Uri> queue=new ArrayList<>();
    private int cursor, removed, failed;
    private boolean awaiting, running, authorized;
    public DeleteCoordinator(Activity a, ExecutorService worker, Runnable changed) {
        activity=a; app=(MoapicApp)a.getApplication(); this.worker=worker; this.changed=changed;
    }
    public void save(Bundle out) {
        out.putParcelableArrayList("deleteQueue",queue); out.putInt("deleteCursor",cursor);
        out.putInt("deleteRemoved",removed);out.putInt("deleteFailed",failed);out.putBoolean("deleteAwaiting",awaiting);out.putBoolean("deleteAuthorized",authorized);
    }
    public void restore(Bundle in) {
        if(in==null)return;ArrayList<Uri> q=in.getParcelableArrayList("deleteQueue");if(q!=null)queue=q;
        cursor=in.getInt("deleteCursor");removed=in.getInt("deleteRemoved");failed=in.getInt("deleteFailed");awaiting=in.getBoolean("deleteAwaiting");authorized=in.getBoolean("deleteAuthorized");
    }
    public void resume() { if(Build.VERSION.SDK_INT==29 && cursor<queue.size()&&!awaiting&&!running) next(); }
    public void request(List<Photo> photos) {
        if(photos.isEmpty())return;
        if(awaiting||running){message("삭제를 처리 중이에요.");return;}
        if(photos.size()>2000){message("한 번에 2,000장까지 선택해 주세요.");return;}
        new AlertDialog.Builder(activity).setTitle(photos.size()+"장을 삭제할까요?")
            .setMessage("휴대폰 사진첩의 원본 파일도 함께 영구 삭제돼요. 모아픽에서 되돌릴 수 없어요.")
            .setNegativeButton("취소",null).setPositiveButton("원본 삭제",(d,w)->{
                queue=new ArrayList<>();for(Photo p:photos)queue.add(p.uri);cursor=removed=failed=0;authorized=false;
                if(Build.VERSION.SDK_INT>=30){
                    try { launch(MediaStore.createDeleteRequest(activity.getContentResolver(),queue)); }
                    catch(Exception e){queue.clear();message("삭제 요청을 열지 못했어요. 사진 접근 권한을 확인해 주세요.");}
                }else next();
            }).show();
    }
    private void launch(PendingIntent consent) throws IntentSender.SendIntentException {
        awaiting=true;
        try{activity.startIntentSenderForResult(consent.getIntentSender(),REQUEST,null,0,0,0);}
        catch(IntentSender.SendIntentException e){awaiting=false;throw e;}
    }
    public void result(int result) {
        awaiting=false;
        if(Build.VERSION.SDK_INT>=30){
            if(result==Activity.RESULT_OK){
                for(Uri uri:queue)app.metadata.remove(uri.toString());message(queue.size()+"장을 삭제했어요.");
            }else message("삭제를 취소했어요.");
            queue.clear();changed.run();
        }else if(result==Activity.RESULT_OK){authorized=true;next();}
        else{queue.clear();changed.run();message("남은 사진의 삭제를 취소했어요.");}
    }
    private void next(){
        if(activity.isDestroyed()||running||awaiting)return;
        if(cursor>=queue.size()){
            message(removed+"장 삭제됨"+(failed>0?" · "+failed+"장은 삭제하지 못했어요":""));queue.clear();cursor=0;changed.run();return;
        }
        running=true;Uri uri=queue.get(cursor);boolean hadConsent=authorized;
        worker.execute(()->{
            try{
                int count=activity.getContentResolver().delete(uri,null,null);
                if(count>0)app.metadata.remove(uri.toString());
                main.post(()->{if(activity.isDestroyed())return;running=false;if(count>0)removed++;else failed++;cursor++;authorized=false;next();});
            }catch(RecoverableSecurityException e){
                main.post(()->{
                    if(activity.isDestroyed())return;running=false;
                    if(hadConsent){failed++;cursor++;authorized=false;next();return;}
                    try{launch(e.getUserAction().getActionIntent());}catch(Exception error){failed++;cursor++;next();}
                });
            }catch(Exception e){main.post(()->{if(activity.isDestroyed())return;running=false;failed++;cursor++;authorized=false;next();});}
        });
    }
    private void message(String message){android.widget.Toast.makeText(activity,message,android.widget.Toast.LENGTH_LONG).show();}
}
