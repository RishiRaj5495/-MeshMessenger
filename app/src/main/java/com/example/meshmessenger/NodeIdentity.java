package com.example.meshmessenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public class NodeIdentity {

    private static final String PREFS_NAME = "mesh_identity";
    private static final String NODE_ID_KEY = "node_id";

    public static String getNodeId(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        String nodeId = prefs.getString(
                NODE_ID_KEY,
                null
        );

        if (nodeId == null) {

            nodeId = "NODE_" +
                    UUID.randomUUID()
                            .toString()
                            .substring(0, 8);

            prefs.edit()
                    .putString(NODE_ID_KEY, nodeId)
                    .apply();
        }

        return nodeId;
    }
}