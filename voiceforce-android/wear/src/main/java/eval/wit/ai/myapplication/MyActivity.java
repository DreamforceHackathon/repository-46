package eval.wit.ai.myapplication;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.wearable.view.CardScrollView;
import android.support.wearable.view.CircledImageView;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import ai.wit.sdk.IWitCoordinator;
import ai.wit.sdk.WitMic;

public class MyActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener, IWitCoordinator {

    public static String TAG = "wear";

    public GoogleApiClient _gac;
    WitMic _witMic;
    ForwardAudioSampleThread _ft;
    CircledImageView image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        image = (CircledImageView) findViewById(R.id.push_button);

        _gac = new GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        Wearable.MessageApi.addListener(_gac, this);

        CardScrollView cardScrollView =
                (CardScrollView) findViewById(R.id.card_scroll_view);
        cardScrollView.setCardGravity(Gravity.BOTTOM);
        cardScrollView.setVerticalScrollBarEnabled(true);
        cardScrollView.setVisibility(View.INVISIBLE);

        Log.d(TAG, "Lets go!");
    }



    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        _gac.connect();
        try {
            _witMic = new WitMic(this, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d("VoiceForce", messageEvent.getPath());
        if (messageEvent.getPath().equals("text")) {
            try {
                final String text = new String(messageEvent.getData(), "UTF-8");
                Log.d("VoiceForce", "Received" + text);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        image.setImageResource(R.drawable.microphone);
                        image.setCircleColor(Color.BLUE);
                        CardScrollView cardScrollView =
                                (CardScrollView) findViewById(R.id.card_scroll_view);
                        TextView textView = (TextView) findViewById(R.id.textDesc);
                        textView.setText(text);
                        cardScrollView.setVisibility(View.VISIBLE);
                    }
                });

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public void onConnected(android.os.Bundle bundle) {
        Log.d(TAG, "onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onSuspended");
    }

    @Override
    public void onConnectionFailed(com.google.android.gms.common.ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    public void sendBytesToHandled(byte[] bytes, int n, int index) {
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/speech" + Math.random());
        final DataMap map = putRequest.getDataMap();
        map.putInt("n", n);
        map.putInt("index", index);
        map.putByteArray("data", bytes);

        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
    }

    public void sendStart() {
        Log.d("VoiceForce", "Sending listening to the phone");
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/start" + Math.random());
        final DataMap map = putRequest.getDataMap();
        map.putDouble("c", Math.random());
        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "OnDataChange!");
    }

    public void buttonPressed(View v) {
        Log.d("VoiceForce", "Button pressed " + (_witMic == null || !_witMic.isRecording()));
        if (_witMic == null || !_witMic.isRecording()) {
            micStartListening();
        } else {
            micStopListening();
        }
    }

    public void micStartListening() {
        Log.d("VoiceForce", "Starting listening");
        try {
            _witMic = new WitMic(this, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        _witMic.startRecording();
        image.setImageResource(R.drawable.microphone);
        image.setCircleColor(Color.parseColor("#d9534f"));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendStart();
                CardScrollView cardScrollView =
                        (CardScrollView) findViewById(R.id.card_scroll_view);
                cardScrollView.setVisibility(View.INVISIBLE);
            }
        });
        InputStream inputStream = _witMic.getInputStream();

        _ft = new ForwardAudioSampleThread(inputStream, this);
        _ft.start();
    }

    public void micStopListening()
    {
        image.setImageResource(R.drawable.processing);
        image.setCircleColor(Color.parseColor("#e0e0e0"));
        _witMic.stopRecording(); // stop recording
        _ft.interrupt(); // stop sending bytes to phones
        sendStop(); // send stop to phone
    }

    @Override
    public void stopListening() {
        // vad stopped
        Log.d(TAG, "Stop recording now");
        micStopListening();
    }

    public void sendStop()
    {
        Log.d("VoiceForce", "Sending stop to the phone");
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/stop");
        final DataMap map = putRequest.getDataMap();
        map.putDouble("c", Math.random());
        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
    }

    class ForwardAudioSampleThread extends Thread {
        InputStream _in;
        MyActivity _my;

        public ForwardAudioSampleThread(InputStream in, MyActivity my) {
            _in = in;
            _my = my;
        }

        @Override
        public void run() {
            int n;
            int count = 0;
            byte[] buffer = new byte[1024];
            try {
                while ((-1 != (n = _in.read(buffer)))) {
                    Log.d("VoiceForce", "" + ++count);
                    sendBytesToHandled(buffer, n, count);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
