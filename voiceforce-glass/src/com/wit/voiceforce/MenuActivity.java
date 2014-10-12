package com.wit.voiceforce;

import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.view.Menu;
import android.view.MenuItem;

public class MenuActivity extends Activity {

	private static final int SPEECH_REQUEST = 0;

	private final Handler mHandler = new Handler();
	
	private MainService.VoiceForceBinder mVoiceForceService;
	private boolean mAttachedToWindow;
    private boolean mOptionsMenuOpen;
	
	private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof MainService.VoiceForceBinder) {
                mVoiceForceService = (MainService.VoiceForceBinder) service;
                openOptionsMenu();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Do nothing.
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService(new Intent(this, MainService.class), mConnection, 0);
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;
        openOptionsMenu();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttachedToWindow = false;
    }

    @Override
    public void openOptionsMenu() {
        if (!mOptionsMenuOpen && mAttachedToWindow && mVoiceForceService != null) {
            super.openOptionsMenu();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.voiceforce, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case R.id.speak:
        		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        	    startActivityForResult(intent, SPEECH_REQUEST);
        		return true;
            case R.id.exit:
                // Stop the service at the end of the message queue for proper options menu
                // animation. This is only needed when starting an Activity or stopping a Service
                // that published a LiveCard.
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        stopService(new Intent(MenuActivity.this, MainService.class));
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            mVoiceForceService.query(results.get(0));
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    
    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        mOptionsMenuOpen = false;

        unbindService(mConnection);

        // We must call finish() from this method to ensure that the activity ends either when an
        // item is selected from the menu or when the menu is dismissed by swiping down.
        finish();
    }
    
}
