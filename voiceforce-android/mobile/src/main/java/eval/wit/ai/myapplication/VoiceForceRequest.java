package eval.wit.ai.myapplication;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;

/**
 * Created by oliv on 10/11/14.
 */
public class VoiceForceRequest {
    public VoiceForceRequest(String state, String intent, HashMap<String, JsonElement> entities){
        _state = state;
        _intent = intent;
        _entities = entities;
    }

    @SerializedName("state")
    private String _state;

    @SerializedName("intent")
    private String _intent;

    @SerializedName("entities")
    private HashMap<String, JsonElement> _entities;

    public void set_state(String _state) {
        this._state = _state;
    }

    public void set_intent(String _intent) {
        this._intent = _intent;
    }

    public void set_entities(HashMap<String, JsonElement> _entities) {
        this._entities = _entities;
    }
}
