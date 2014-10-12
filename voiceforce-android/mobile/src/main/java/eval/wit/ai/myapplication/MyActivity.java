package eval.wit.ai.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import ai.wit.sdk.IWitListener;
import ai.wit.sdk.Wit;
import ai.wit.sdk.WitMic;

public class MyActivity extends ActionBarActivity implements IWitListener, ConnectionCallbacks, OnConnectionFailedListener, DataApi.DataListener {

    public static String TAG = "handled";
    GoogleApiClient _gac;
    private WebSocketClient mWebSocketClient;
    private Timer _t;
    TextToSpeech ttobj;
    boolean closed;

    Wit _wit;
    String state;
    private PipedInputStream _in;
    private PipedOutputStream _out;
    private int _currentIndex;
    private HashMap<Integer, Pair<Integer, byte[]>> _cache;
    BluetoothActivity _bl;

    // inner class
    // BluetoothHeadsetUtils is an abstract class that has
    // 4 abstracts methods that need to be implemented.
    private class BluetoothActivity extends eval.wit.ai.myapplication.BluetoothHelper
    {
        public BluetoothActivity(Context context)
        {
            super(context);
        }

        @Override
        public void onScoAudioDisconnected()
        {
            toggle(null);
            // Cancel speech recognizer if desired
        }

        @Override
        public void onScoAudioConnected()
        {
            toggle(null);
            Log.d("FOO", "FAR");
            // Should start speech recognition here if not already started
        }

        @Override
        public void onHeadsetDisconnected()
        {

        }

        @Override
        public void onHeadsetConnected()
        {

        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.getAction().equals("android.intent.action.VOICE_COMMAND")){
            _bl.start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        String accessToken = "JBZN5A6EB5D4Q6WBRBYAC35JXUQOQORJ";

        _wit = new Wit(accessToken, this);

        _gac = new GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        ttobj = new TextToSpeech(getApplicationContext(),
                new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (status != TextToSpeech.ERROR) {
                            ttobj.setLanguage(Locale.UK);
                        }
                    }
                });
        closed = true;

        _bl = new BluetoothActivity(this);

    }

    @Override
    public void onPause() {
        super.onPause();
        _bl.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        return id == R.id.action_settings || super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Wearable.DataApi.addListener(_gac, this);
        _gac.connect();
        _t = new Timer("WS", true);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (mWebSocketClient == null ||
                        mWebSocketClient.getReadyState() == WebSocket.READYSTATE.CLOSED ||
                        mWebSocketClient.getReadyState() == WebSocket.READYSTATE.CLOSING ||
                        mWebSocketClient.getReadyState() == WebSocket.READYSTATE.NOT_YET_CONNECTED) {
                    connectWebSocket();
                }
            }
        };
        _t.scheduleAtFixedRate(task, 0, 1000);

        if (this.getIntent().getAction().equals("android.intent.action.VOICE_COMMAND")){
            toggle(null);
        }
    }

    @Override
    public void onConnected(android.os.Bundle bundle) {
        TextView tv = (TextView) findViewById(R.id.status);
        tv.setText("Connected");
        Log.d(TAG, "onConnected");
        Wearable.DataApi.addListener(_gac, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onSuspended");
    }

    @Override
    public void onConnectionFailed(com.google.android.gms.common.ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    private void sendAllPossible(){
        while (_cache.containsKey(_currentIndex)){
            Pair<Integer, byte[]> pair = _cache.get(_currentIndex);
            if (!closed) {
                try {
                    Log.d("VoiceForce", "Sending index: " + _currentIndex);
                    _out.write(pair.second, 0, pair.first);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            _cache.remove(_currentIndex++);
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            Log.e("VoiceForce", "event " + event.getDataItem().getUri().getPath());
            if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.d(TAG, "DataItem deleted: " + event.getDataItem().getUri());
            } else if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().startsWith("/speech")) {
                final DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                final int n = dataMapItem.getDataMap().getInt("n");
                int index = dataMapItem.getDataMap().getInt("index");
                final byte[] bytes = dataMapItem.getDataMap().getByteArray("data");
                Log.d(TAG, "DataItem changed: " + n + " index" + index);
                if (!closed) {
                    _cache.put(index, new Pair<Integer, byte[]>(n, bytes));
                    sendAllPossible();
                }
            } else if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().startsWith("/start")) {
                Log.d(TAG, "Starting streaming to Wit.ai");
                closed = false;
                _currentIndex = 1;
                _cache = new HashMap<Integer, Pair<Integer, byte[]>>();
                _in = new PipedInputStream();
                _out = new PipedOutputStream();
                try {
                    _in.connect(_out);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                _wit.streamRawAudio(_in, "signed-integer", 16, WitMic.SAMPLE_RATE, ByteOrder.LITTLE_ENDIAN);
            } else if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().startsWith("/stop")) {
                Log.d(TAG, "Stopping streaming to Wit.ai");
                try {
                    closed = true;
                    sendAllPossible();
                    _out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    // WIT INTEGRATION

    public void toggle(View v) {
        try {
            _wit.toggleListening();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JsonElement ensureJsonArray(JsonElement elt) {
        if (!elt.isJsonArray()) {
            JsonElement res = new JsonArray();
            res.getAsJsonArray().add(elt);
            return res;
        }
        else
            return elt;
    }

    @Override
    public void witDidGraspIntent(String intent, HashMap<String, JsonElement> entities, String body, double confidence, Error error) {
        if (error != null){
            Log.e("VoiceForce", "An error occured while requesting Wit.ai " + error);
            return;
        }
        ((TextView) findViewById(R.id.txtText)).setText(body);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        HashMap<String, JsonElement> ents = new HashMap<String, JsonElement>();
        for(Map.Entry<String, JsonElement> entry : entities.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            ents.put(key, ensureJsonArray(value));
        }
        String jsonOutput = gson.toJson(ents);
        ((TextView) findViewById(R.id.jsonView)).setText(Html.fromHtml("<span><b>Intent: " + intent +
                "<b></span><br/>") + jsonOutput +
                Html.fromHtml("<br/><span><b>Confidence: " + confidence + "<b></span>"));
        sendMessage(intent, ents);
    }

    @Override
    public void witDidStartListening() {
        ((TextView) findViewById(R.id.txtText)).setText("Listening...");
    }

    @Override
    public void witDidStopListening() {
        ((TextView) findViewById(R.id.txtText)).setText("Processing...");
    }

    public static class PlaceholderFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            // Inflate the layout for this fragment
            return inflater.inflate(R.layout.wit_button, container, false);
        }
    }

    // VOICEFORCE Integration

    private void connectWebSocket() {
        URI uri;
        try {
            uri = new URI("ws://25a029cd.ngrok.com");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("Websocket", "Opened");
            }

            @Override
            public void onMessage(String s) {
                final String message = s;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // TTS
                        try {
                            Gson gson = new Gson();
                            Log.d("VoiceForce", "Message received " + message);
                            VoiceForceResponse response = gson.fromJson(message, VoiceForceResponse.class);
                            Log.d("VoiceForce", "Gson : Response " + gson.toJson(response));
                            String toSpeak = response.get_text();
                            state = response.get_state();
                            ttobj.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                        } catch (Exception e) {
                            Log.e("VoiceForce", "VoiceForce : Error " + e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Closed " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e);
            }
        };
        mWebSocketClient.connect();
    }

    public void sendMessage(String intent, HashMap<String, JsonElement> entities) {
        Gson gson = new Gson();
        VoiceForceRequest request = new VoiceForceRequest(state, intent, entities);
        String r = gson.toJson(request);
        Log.d("VoiceForce", "Sending " + r);
        if (mWebSocketClient.getReadyState() == WebSocket.READYSTATE.OPEN) {
            mWebSocketClient.send(r);
        }
    }

}