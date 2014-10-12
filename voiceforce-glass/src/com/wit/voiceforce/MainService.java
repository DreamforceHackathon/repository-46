package com.wit.voiceforce;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.widget.RemoteViews;

public class MainService extends Service {

	private static final String LIVE_CARD_TAG = "voiceforce";
	
	public class VoiceForceBinder extends Binder {
		public void askWit(String query) {
			//String res = "(no result)";
			new Wit(mLiveCardView, R.id.home_text, mLiveCard).execute(query);
		}
		
		public void query(String recognizedText) {
			askWit(recognizedText);
		}
	}
	
	private LiveCard mLiveCard;
	private RemoteViews mLiveCardView;
	private final VoiceForceBinder mBinder = new VoiceForceBinder();
	
	@Override
    public void onCreate() {
        super.onCreate();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mLiveCard == null) {
            mLiveCard = new LiveCard(this, LIVE_CARD_TAG);
            
            mLiveCardView = new RemoteViews(getPackageName(), R.layout.home);
            mLiveCardView.setTextViewText(R.id.home_text, "Loading VoiceForce...");
            mLiveCard.setViews(mLiveCardView);
            mBinder.askWit("contact Marc Benioff");
            
            Intent menuIntent = new Intent(this, MenuActivity.class);
            menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
            mLiveCard.attach(this);
            mLiveCard.publish(PublishMode.REVEAL);
        } else {
            mLiveCard.navigate();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mLiveCard != null && mLiveCard.isPublished()) {
            mLiveCard.unpublish();
            mLiveCard = null;
        }
        super.onDestroy();
    }

}
