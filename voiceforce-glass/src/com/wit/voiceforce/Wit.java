package com.wit.voiceforce;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import com.wit.voiceforce.MainService.VoiceForceBinder;

import android.os.AsyncTask;
import android.util.Log;

public class Wit extends AsyncTask<String, Void, String> {

	private final String access_token = "4IQNHHTPMVK545O5SXPSLQ3HNBO7UA5L";

	private VoiceForceBinder mBinder;
	private WebSocketClient mWebSocketClient;
	
	public Wit (VoiceForceBinder mBinder) {
		this.mBinder = mBinder;
	}
	
	@Override
	protected String doInBackground(String... queries) {
		try {
			return query(queries[0]);
		} catch (IOException e) {
			return e.getMessage();
		}
	}
	
	private String buildRequest(String wit) {
		JSONObject json = new JSONObject();
		try {
			JSONObject wit_msg = new JSONObject(wit);
			JSONObject outcome = wit_msg.getJSONArray("outcomes").getJSONObject(0);
			json.put("text", "Glass");
			json.put("entities", outcome.get("entities"));
			json.put("intent", outcome.get("intent"));
			String state = mBinder.getState();
			if (state != null) {
				json.put("state", state);
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return json.toString();
	}
	
	@Override
	protected void onPostExecute(String result) {
		final String request = buildRequest(result);
        Log.i("VOICE FORCE", "Request: " + request);
        connectWebSocket(request);
	}
	
	private void connectWebSocket(final String request) {
		  URI uri;
		  try {
		    //uri = new URI("ws://192.168.1.25:1337");
			  uri = new URI("ws://2289189c.ngrok.com");
		  } catch (URISyntaxException e) {
		    e.printStackTrace();
		    return;
		  }

		  mWebSocketClient = new WebSocketClient(uri) {
		    @Override
		    public void onOpen(ServerHandshake serverHandshake) {
		      Log.i("Websocket", "Opened");
		      mWebSocketClient.send(request);
		    }

		    @Override
		    public void onMessage(String s) {
		    	Log.i("VOICE FORCE", "Message: " + s);
		        JSONObject json;
				try {
					json = new JSONObject(s);
					mBinder.setState(json.get("state").toString());
					mBinder.readThat(json.get("text").toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		        this.close();
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
	
	private String query(String q) throws IOException {
		HttpClient client = new DefaultHttpClient();
	    HttpGet get = new HttpGet("https://api.wit.ai/message?v=20141609&q=" + URLEncoder.encode(q, "UTF-8"));
	    get.setHeader("Authorization", "Bearer " + access_token);
	    HttpResponse response = client.execute(get);    
		BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = in.readLine()) != null) {
            result.append(line);
        }
        return result.toString();
	}
}
