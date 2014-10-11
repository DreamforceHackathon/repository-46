package eval.wit.ai.myapplication;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.Log;
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
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;

import ai.wit.sdk.IWitListener;
import ai.wit.sdk.Wit;


public class MyActivity extends ActionBarActivity implements IWitListener, DataApi.DataListener, ConnectionCallbacks, OnConnectionFailedListener {

    GoogleApiClient _gac;
    private WebSocketClient mWebSocketClient;
    TextToSpeech ttobj;
    Wit _wit;

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

        ttobj=new TextToSpeech(getApplicationContext(),
                new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if(status != TextToSpeech.ERROR){
                            ttobj.setLanguage(Locale.US);
                        }
                    }
                });
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
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        _gac.connect();
        connectWebSocket();
    }

    @Override
    public void onConnected(android.os.Bundle bundle) {
        TextView tv = (TextView) findViewById(R.id.status);
        tv.setText("Connected");
        Log.d("handled", "onConnected");
        GlanduThread gt = new GlanduThread(this, _gac);
        gt.start();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("handled", "onSuspended");
    }

    @Override
    public void onConnectionFailed(com.google.android.gms.common.ConnectionResult connectionResult) {
        Log.d("handled", "onConnectionFailed");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        TextView tv = (TextView) findViewById(R.id.status);
        tv.setText("Got Event!!!");

        Log.d("handled", "got some data ");
        for (DataEvent event : dataEvents) {
            Log.d("handled", "event data: "+event);
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

    @Override
    public void witDidGraspIntent(String intent, HashMap<String, JsonElement> entities, String body, double confidence, Error error) {
        ((TextView) findViewById(R.id.txtText)).setText(body);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(entities);
        ((TextView) findViewById(R.id.jsonView)).setText(Html.fromHtml("<span><b>Intent: " + intent +
                "<b></span><br/>") + jsonOutput +
                Html.fromHtml("<br/><span><b>Confidence: " + confidence + "<b></span>"));
        sendMessage(intent, entities);
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
                            ttobj.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                        }
                        catch (Exception e) {
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
                Log.i("Websocket", "Error " + e.getMessage());
            }
        };
        mWebSocketClient.connect();
    }

    public void sendMessage(String intent, HashMap<String, JsonElement> entities) {
        Gson gson = new Gson();
        VoiceForceRequest request = new VoiceForceRequest("{}", intent, entities);
        String r = gson.toJson(request);
        Log.d("VoiceForce", "Sending " + r);
        mWebSocketClient.send(r);
    }


    class GlanduThread extends Thread {


        MyActivity _my;
        GoogleApiClient _gac;

        public GlanduThread(MyActivity my, GoogleApiClient gac) {
            _my = my;
            _gac = gac;
        }

        @Override
        public void run()
        {
            SecureRandom random = new SecureRandom();
            Wearable.DataApi.addListener(_gac, _my);
            NodeApi.GetConnectedNodesResult nodes =
                    Wearable.NodeApi.getConnectedNodes(_gac).await();
            for (Node node : nodes.getNodes()) {
                Log.d("handled", "Node connected: "+node.getDisplayName());
                for (int i = 0; i < 10; i++) {
                    BigInteger bi = new BigInteger(130, random);
                    Wearable.MessageApi.sendMessage(_gac, node.getId(),"abc", bi.toString().getBytes());
                }
            }
        }
    }
}
