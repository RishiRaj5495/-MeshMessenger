

package com.example.meshmessenger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class MeshMessage {

    private String messageId;
    private String sourceId;
    private String destinationId;
    private String payload;
    private int hopCount;

    public MeshMessage(
            String sourceId,
            String destinationId,
            String payload
    ) {
        this.messageId = UUID.randomUUID().toString();
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.payload = payload;
        this.hopCount = 0;
    }

    private MeshMessage(
            String messageId,
            String sourceId,
            String destinationId,
            String payload,
            int hopCount
    ) {
        this.messageId = messageId;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.payload = payload;
        this.hopCount = hopCount;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public String getPayload() {
        return payload;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void incrementHopCount() {
        hopCount++;
    }

    public String toJson() {

        JSONObject json = new JSONObject();

        try {
            json.put("messageId", messageId);
            json.put("sourceId", sourceId);
            json.put("destinationId", destinationId);
            json.put("payload", payload);
            json.put("hopCount", hopCount);

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return json.toString();
    }

    public static MeshMessage fromJson(String json)
            throws JSONException {

        JSONObject object = new JSONObject(json);

        return new MeshMessage(
                object.getString("messageId"),
                object.getString("sourceId"),
                object.getString("destinationId"),
                object.getString("payload"),
                object.getInt("hopCount")
        );
    }
}