package com.example.meshmessenger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    // =========================
    // Wi-Fi Direct
    // =========================

    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver wifiDirectReceiver;

    private final List<WifiP2pDevice> peers = new ArrayList<>();

    private ArrayAdapter<String> deviceAdapter;
    private final List<String> deviceNames = new ArrayList<>();

    private static final int PERMISSION_REQUEST_CODE = 100;

    // =========================
    // TCP
    // =========================

    private static final int TCP_PORT = 8888;

    private ServerSocket serverSocket;
    private Socket clientSocket;

    private InputStream inputStream;
    private OutputStream outputStream;

    private BufferedReader reader;
    private PrintWriter writer;

    private final ExecutorService tcpExecutor =
            Executors.newCachedThreadPool();

    private boolean tcpServerRunning = false;
    private boolean tcpConnected = false;

    // =========================
    // Chat UI
    // =========================

    private TextView txtMessages;
    private TextView txtConnectionStatus;

    private EditText editMessage;
    private Button btnSend;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // =========================
        // Find UI elements
        // =========================

        ListView deviceList = findViewById(R.id.deviceList);

        txtMessages = findViewById(R.id.txtMessages);
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus);

        editMessage = findViewById(R.id.editMessage);
        btnSend = findViewById(R.id.btnSend);

        // Initially SEND is disabled
        btnSend.setEnabled(false);

        // =========================
        // Device List
        // =========================

        deviceAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                deviceNames
        );

        deviceList.setAdapter(deviceAdapter);

        deviceList.setOnItemClickListener(
                (parent, view, position, id) -> {

                    WifiP2pDevice selectedDevice =
                            peers.get(position);

                    connectToDevice(selectedDevice);
                }
        );

        // =========================
        // Wi-Fi Direct Manager
        // =========================

        wifiP2pManager =
                (WifiP2pManager)
                        getSystemService(Context.WIFI_P2P_SERVICE);

        // =========================
        // Wi-Fi Direct Channel
        // =========================

        channel = wifiP2pManager.initialize(
                this,
                getMainLooper(),
                () -> Toast.makeText(
                        this,
                        "Wi-Fi Direct channel disconnected",
                        Toast.LENGTH_SHORT
                ).show()
        );

        // =========================
        // Permissions
        // =========================

        requestPermissionsIfNeeded();

        // =========================
        // Wi-Fi Direct Receiver
        // =========================

        wifiDirectReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context context,
                    Intent intent
            ) {

                String action = intent.getAction();

                // -------------------------
                // Wi-Fi Direct State
                // -------------------------

                if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
                        .equals(action)) {

                    int state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            -1
                    );

                    if (state ==
                            WifiP2pManager.WIFI_P2P_STATE_ENABLED) {

                        Toast.makeText(
                                MainActivity.this,
                                "Wi-Fi Direct is enabled",
                                Toast.LENGTH_SHORT
                        ).show();

                        discoverPeers();

                    } else {

                        Toast.makeText(
                                MainActivity.this,
                                "Please enable Wi-Fi",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                }

                // -------------------------
                // Peers Changed
                // -------------------------

                else if (
                        WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
                                .equals(action)
                ) {

                    discoverPeersList();
                }

                // -------------------------
                // Connection Changed
                // -------------------------

                else if (
                        WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
                                .equals(action)
                ) {

                    NetworkInfo networkInfo =
                            intent.getParcelableExtra(
                                    WifiP2pManager.EXTRA_NETWORK_INFO
                            );

                    if (networkInfo != null &&
                            networkInfo.isConnected()) {

                        Toast.makeText(
                                MainActivity.this,
                                "Phone connected successfully!",
                                Toast.LENGTH_LONG
                        ).show();

                        getConnectionInfo();

                    } else {

                        Toast.makeText(
                                MainActivity.this,
                                "Wi-Fi Direct disconnected",
                                Toast.LENGTH_SHORT
                        ).show();

                        updateConnectionStatus(
                                "Status: Wi-Fi Direct disconnected"
                        );
                    }
                }
            }
        };

        // =========================
        // Discover Button
        // =========================

        findViewById(R.id.btnDiscover)
                .setOnClickListener(v -> {

                    discoverPeers();
                });

        // =========================
        // Check Connection Button
        // =========================

        findViewById(R.id.btnCheckConnection)
                .setOnClickListener(v -> {

                    getConnectionInfo();
                });

        // =========================
        // Send Button
        // =========================

        btnSend.setOnClickListener(v -> {

            sendMessage();
        });
    }


    // ============================================================
    // PERMISSIONS
    // ============================================================

    private void requestPermissionsIfNeeded() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.NEARBY_WIFI_DEVICES
                        },
                        PERMISSION_REQUEST_CODE
                );
            }

        } else if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.M) {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        PERMISSION_REQUEST_CODE
                );
            }
        }
    }


    // ============================================================
    // DISCOVER PEERS
    // ============================================================

    private void discoverPeers() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        wifiP2pManager.discoverPeers(
                channel,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {

                        Toast.makeText(
                                MainActivity.this,
                                "Searching for nearby devices...",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onFailure(int reason) {

                        Toast.makeText(
                                MainActivity.this,
                                "Discovery failed: " + reason,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }


    // ============================================================
    // GET PEER LIST
    // ============================================================

    private void discoverPeersList() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        wifiP2pManager.requestPeers(
                channel,
                peerList -> {

                    peers.clear();

                    peers.addAll(
                            peerList.getDeviceList()
                    );

                    deviceNames.clear();

                    for (WifiP2pDevice device : peers) {

                        deviceNames.add(
                                device.deviceName
                        );
                    }

                    deviceAdapter.notifyDataSetChanged();
                }
        );
    }


    // ============================================================
    // ACTIVITY RESUME
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();

        IntentFilter intentFilter =
                new IntentFilter();

        intentFilter.addAction(
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
        );

        intentFilter.addAction(
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
        );

        intentFilter.addAction(
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
        );

        registerReceiver(
                wifiDirectReceiver,
                intentFilter
        );
    }


    // ============================================================
    // ACTIVITY PAUSE
    // ============================================================

    @Override
    protected void onPause() {

        super.onPause();

        unregisterReceiver(
                wifiDirectReceiver
        );
    }


    // ============================================================
    // CONNECT TO WI-FI DIRECT DEVICE
    // ============================================================

    private void connectToDevice(
            WifiP2pDevice device
    ) {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        WifiP2pManager.ActionListener actionListener =
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {

                        Toast.makeText(
                                MainActivity.this,
                                "Connection request sent to "
                                        + device.deviceName,
                                Toast.LENGTH_SHORT
                        ).show();

                        new android.os.Handler()
                                .postDelayed(
                                        () -> getConnectionInfo(),
                                        3000
                                );
                    }

                    @Override
                    public void onFailure(
                            int reason
                    ) {

                        Toast.makeText(
                                MainActivity.this,
                                "Connection failed: "
                                        + reason,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                };

        WifiP2pManager.Channel channel =
                this.channel;

        android.net.wifi.p2p.WifiP2pConfig config =
                new android.net.wifi.p2p.WifiP2pConfig();

        config.deviceAddress =
                device.deviceAddress;

        wifiP2pManager.connect(
                channel,
                config,
                actionListener
        );
    }


    // ============================================================
    // GET CONNECTION INFO
    // ============================================================

    private void getConnectionInfo() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        wifiP2pManager.requestConnectionInfo(
                channel,
                info -> {

                    if (info.groupFormed &&
                            info.groupOwnerAddress != null) {

                        String groupOwnerIp =
                                info.groupOwnerAddress
                                        .getHostAddress();

                        if (info.isGroupOwner) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Role: Group Owner",
                                    Toast.LENGTH_LONG
                            ).show();

                            updateConnectionStatus(
                                    "Status: Wi-Fi Direct Connected\n"
                                            + "Role: Group Owner"
                            );

                            startTcpServer();

                        } else {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Role: Client\nGO IP: "
                                            + groupOwnerIp
                                            + "\nStarting TCP Client...",
                                    Toast.LENGTH_LONG
                            ).show();

                            updateConnectionStatus(
                                    "Status: Wi-Fi Direct Connected\n"
                                            + "Role: Client"
                            );

                            connectTcpClient(
                                    groupOwnerIp
                            );
                        }

                    } else {

                        Toast.makeText(
                                MainActivity.this,
                                "Wi-Fi Direct group not formed",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }


    // ============================================================
    // TCP SERVER
    // ============================================================

    private void startTcpServer() {

        if (tcpServerRunning) {
            return;
        }

        tcpServerRunning = true;

        tcpExecutor.execute(() -> {

            try {

                serverSocket =
                        new ServerSocket(TCP_PORT);

                runOnUiThread(() -> {

                    Toast.makeText(
                            MainActivity.this,
                            "TCP Server started on port "
                                    + TCP_PORT,
                            Toast.LENGTH_LONG
                    ).show();

                    updateConnectionStatus(
                            "Status: TCP Server waiting..."
                    );
                });

                // Wait for client
                clientSocket =
                        serverSocket.accept();

                // TCP connection established
                setupStreams(clientSocket);

                runOnUiThread(() -> {

                    Toast.makeText(
                            MainActivity.this,
                            "TCP Client connected!",
                            Toast.LENGTH_LONG
                    ).show();

                    updateConnectionStatus(
                            "Status: TCP Connected\n"
                                    + "Role: Group Owner"
                    );
                });

                // Start receiving messages
                startMessageReceiver();

            } catch (IOException e) {

                tcpServerRunning = false;

                runOnUiThread(() -> {

                    Toast.makeText(
                            MainActivity.this,
                            "Server error: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    updateConnectionStatus(
                            "Status: TCP Error"
                    );
                });
            }
        });
    }


    // ============================================================
    // TCP CLIENT
    // ============================================================

    private void connectTcpClient(
            String groupOwnerIp
    ) {

        // Prevent duplicate connections
        if (tcpConnected) {
            return;
        }

        tcpExecutor.execute(() -> {

            try {

                clientSocket =
                        new Socket(
                                groupOwnerIp,
                                TCP_PORT
                        );

                // Setup input/output streams
                setupStreams(clientSocket);

                runOnUiThread(() -> {

                    Toast.makeText(
                            MainActivity.this,
                            "TCP Client connected to "
                                    + groupOwnerIp,
                            Toast.LENGTH_LONG
                    ).show();

                    updateConnectionStatus(
                            "Status: TCP Connected\n"
                                    + "Role: Client"
                    );
                });

                // Start receiving messages
                startMessageReceiver();

            } catch (IOException e) {

                runOnUiThread(() -> {

                    Toast.makeText(
                            MainActivity.this,
                            "Client error: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    updateConnectionStatus(
                            "Status: TCP Connection Failed"
                    );
                });
            }
        });
    }


    // ============================================================
    // SETUP INPUT / OUTPUT STREAMS
    // ============================================================

    private void setupStreams(
            Socket socket
    ) throws IOException {

        inputStream =
                socket.getInputStream();

        outputStream =
                socket.getOutputStream();

        reader =
                new BufferedReader(
                        new InputStreamReader(
                                inputStream
                        )
                );

        writer =
                new PrintWriter(
                        outputStream,
                        true
                );

        tcpConnected = true;

        runOnUiThread(() -> {

            btnSend.setEnabled(true);

        });
    }


    // ============================================================
    // SEND MESSAGE
    // ============================================================

    private void sendMessage() {

        String message =
                editMessage
                        .getText()
                        .toString()
                        .trim();

        // Empty message
        if (message.isEmpty()) {
            return;
        }

        // TCP not connected
        if (!tcpConnected ||
                writer == null) {

            Toast.makeText(
                    this,
                    "TCP is not connected",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // Send through TCP
        tcpExecutor.execute(() -> {

            writer.println(message);
            writer.flush();

            runOnUiThread(() -> {

                addMessage(
                        "You: " + message
                );

                editMessage.setText("");
            });
        });
    }


    // ============================================================
    // RECEIVE MESSAGES
    // ============================================================

    private void startMessageReceiver() {

        tcpExecutor.execute(() -> {

            try {

                String message;

                while (
                        tcpConnected &&
                                reader != null &&
                                (message = reader.readLine()) != null
                ) {

                    String receivedMessage =
                            message;

                    runOnUiThread(() -> {

                        addMessage(
                                "Other: "
                                        + receivedMessage
                        );
                    });
                }

            } catch (IOException e) {

                runOnUiThread(() -> {

                    tcpConnected = false;

                    btnSend.setEnabled(false);

                    updateConnectionStatus(
                            "Status: TCP Disconnected"
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "TCP connection closed",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }


    // ============================================================
    // ADD MESSAGE TO CHAT
    // ============================================================

    private void addMessage(
            String message
    ) {

        String currentMessages =
                txtMessages
                        .getText()
                        .toString();

        if (currentMessages.equals(
                "Messages will appear here..."
        )) {

            currentMessages = "";
        }

        if (!currentMessages.isEmpty()) {

            currentMessages += "\n";
        }

        currentMessages += message;

        txtMessages.setText(
                currentMessages
        );
    }


    // ============================================================
    // UPDATE CONNECTION STATUS
    // ============================================================

    private void updateConnectionStatus(
            String status
    ) {

        runOnUiThread(() -> {

            txtConnectionStatus
                    .setText(status);
        });
    }


    // ============================================================
    // CLEANUP
    // ============================================================

    @Override
    protected void onDestroy() {

        super.onDestroy();

        tcpConnected = false;
        tcpServerRunning = false;

        try {

            if (clientSocket != null) {
                clientSocket.close();
            }

            if (serverSocket != null) {
                serverSocket.close();
            }

        } catch (IOException ignored) {
        }

        tcpExecutor.shutdownNow();
    }
}
