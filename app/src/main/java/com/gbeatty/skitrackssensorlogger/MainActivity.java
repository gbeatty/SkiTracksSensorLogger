package com.gbeatty.skitrackssensorlogger;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity implements ServiceConnection, MwBoardConnectionListener{
    private boolean serviceConnected = false;
    private boolean metaWearBoardConnected = false;
    private boolean logRunning = false;
    private ServiceConnection serviceConnection;
    private LoggingService loggingService;
    private Button boardConnectButton;
    private CheckBox serviceStatus;
    private CheckBox boardStatus;
    private Button loggingButton;
    private CheckBox loggingStatus;

    private static final String[] INITIAL_PERMS={
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private boolean hasPermission(String perm) {
        return(PackageManager.PERMISSION_GRANTED==checkSelfPermission(perm));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        serviceConnection = this;

        if(!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        {
            requestPermissions(INITIAL_PERMS, 1);
        }

        serviceStatus = (CheckBox) findViewById(R.id.serviceStatusCheckBox);
        boardStatus = (CheckBox) findViewById(R.id.boardStatusCheckBox);
        loggingStatus = (CheckBox) findViewById(R.id.loggingStatusCheckBox);

        Button serviceConnectButton = (Button) findViewById(R.id.serviceButton);
        serviceConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceConnected == false) {
                    ConnectToService();
                } else {
                    DisconnectService();
                }
            }
        });

        boardConnectButton = (Button) findViewById(R.id.boardConnectButton);
        boardConnectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (metaWearBoardConnected) {
                    DisconnectMetaWearBoard();
                } else {
                    ConnectMetaWearBoard();
                }
            }
        });

        loggingButton = (Button) findViewById(R.id.loggingButton);
        loggingButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (!logRunning) {
                    loggingService.StartLogging();
                    logRunning = true;
                    loggingStatus.setChecked(true);
                }
                else {
                    loggingService.StopLogging();
                    logRunning = false;
                    loggingStatus.setChecked(false);
                }
            }
        });


    }

    private void DisconnectMetaWearBoard() {
        loggingService.DisconnectFromMetaWearBoard();
    }

    private void ConnectMetaWearBoard() {
        loggingService.ConnectToMetaWearBoard();
    }

    private void DisconnectService() {
        stopService(new Intent(getApplicationContext(), LoggingService.class));
    }

    private void ConnectToService() {
        startService(new Intent(getApplicationContext(), LoggingService.class));
        getApplicationContext().bindService(new Intent(getApplicationContext(), LoggingService.class), serviceConnection, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        serviceConnected = true;
        loggingService = ((LoggingService.LoggingServiceBinder) service).getService();
        loggingService.RegisterConnectionListener(this);
        serviceStatus.setChecked(true);
        boardConnectButton.setEnabled(true);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        loggingService.RegisterConnectionListener(null);
        loggingService = null;
        serviceConnected = false;
        serviceStatus.setChecked(false);
        boardConnectButton.setEnabled(false);
    }

    @Override
    public void Connected() {
        metaWearBoardConnected = true;
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                boardStatus.setChecked(true);
                loggingButton.setEnabled(true);
            }
        });
    }

    @Override
    public void Disconnected() {
        metaWearBoardConnected = false;
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                boardStatus.setChecked(false);
                loggingButton.setEnabled(false);
            }
        });
    }

}
