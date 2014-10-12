package com.wit.voiceforce;

import java.util.Locale;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.RemoteViews;

public class MainService extends Service implements TextToSpeech.OnInitListener {

	private static final String LIVE_CARD_TAG = "voiceforce";
	
	private TextToSpeech tts;
	
	public class VoiceForceBinder extends Binder {
		private String state = null;
		
		public void askWit(String query) {
			mLiveCardView.setTextViewText(R.id.home_text, "Retrieving answer...");
			mLiveCard.setViews(mLiveCardView);
	        new Wit(this).execute(query, state);
		}
		
		public String getState() {
			return state;
		}
		
		public void setState(String state) {
			Log.i("VOICE FORCE", "State: " + state);
			this.state = state;
		}
		
		public void readThat(String text) {
			Log.i("VOICE FORCE", "Read that: " + text);
			mLiveCardView.setTextViewText(R.id.home_text, text);
			mLiveCard.setViews(mLiveCardView);
			tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
		}
	}
	
	private LiveCard mLiveCard;
	private RemoteViews mLiveCardView;
	private final VoiceForceBinder mBinder = new VoiceForceBinder();
	
	@Override
    public void onCreate() {
        super.onCreate();
        tts = new TextToSpeech(getApplicationContext(), this);
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
            mLiveCardView.setTextViewText(R.id.home_text, "Tap to start!");
            mLiveCard.setViews(mLiveCardView);
            
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

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
		      tts.setLanguage(Locale.ENGLISH);
		}
		
	}

}
