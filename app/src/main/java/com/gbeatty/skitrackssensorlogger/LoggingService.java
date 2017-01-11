package com.gbeatty.skitrackssensorlogger;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Bmi160Accelerometer;
import com.mbientlab.metawear.module.Bmi160Gyro;
import com.mbientlab.metawear.module.Bmm150Magnetometer;
import com.mbientlab.metawear.module.Logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

public class LoggingService extends Service implements ServiceConnection, LocationListener{

    private int sampleCount = 0;
    private class MyTimerTask extends TimerTask
    {
        int iteration = 1;

        @Override
        public void run() {
            Log.i("SkiTracksLogger", "Rate:  " + sampleCount / iteration);
            ++iteration;
        }
    }

    final SimpleDateFormat format = new SimpleDateFormat("MM_dd_yyyy_hh_mm_ss.SSS");

    private MetaWearBleService.LocalBinder serviceBinder;
    private final String MW_MAC_ADDRESS = "FA:02:BA:BD:6E:EA";

    private MwBoardConnectionListener registeredConnectionListener;
    private boolean connectedToMwBoard = false;
    private MetaWearBoard mwBoard;

    private Bmi160Accelerometer bmi160AccModule;
    private OutputStreamWriter accelWriter;

    private Bmi160Gyro bmi160GyroModule;
    private OutputStreamWriter gyroWriter;

    private Bmm150Magnetometer bmm150MagModule;
    private OutputStreamWriter magWriter;

    private OutputStreamWriter gpsWriter;

    private final IBinder mBinder = new LoggingServiceBinder();

    @Override
    public void onLocationChanged(Location location) {

        if(gpsWriter != null) {
            try {
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTimeInMillis(location.getTime());

                gpsWriter.write(format.format(cal.getTime()) + ',' + location.getLatitude() + ',' + location.getLongitude() + ',' + location.getAltitude() + ',' + location.getSpeed() + ',' + location.getAccuracy() + '\n');
            } catch (Exception e) {
                Log.e("Exception", "Gps write failed: " + e.toString());
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public class LoggingServiceBinder extends Binder {
        LoggingService getService() {
            return LoggingService.this;
        }
    }

    private final MetaWearBoard.ConnectionStateHandler connectionStateHandler = new MetaWearBoard.ConnectionStateHandler() {
        @Override
        public void connected() {
            Log.i("SkiTracksLogger", "MetaWear Board Connected");
            connectedToMwBoard = true;
            if (registeredConnectionListener != null) {
                registeredConnectionListener.Connected();
            }
        }

        @Override
        public void disconnected() {
            Log.i("SkiTracksLogger", "MetaWear Board Disconnected");
            connectedToMwBoard = false;
            if (registeredConnectionListener != null) {
                registeredConnectionListener.Disconnected();
            }
        }
    };

    public LoggingService() {
    }

    @Override
    public void onCreate() {
        Notification notification = new Notification();
        notification.priority = Notification.PRIORITY_MAX;
        startForeground(1, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ConnectToMetaWearService();

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, this);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("SkiTracksLogger", "Logging service stopping");
        DisconnectFromMetaWearService();

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i("SkiTracksLogger", "MetaWear Service Connected");
        serviceBinder = (MetaWearBleService.LocalBinder) service;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        DisconnectFromMetaWearBoard();
        Log.i("SkiTracksLogger", "MetaWear Service Disconnected");
    }

    public void RegisterConnectionListener(MwBoardConnectionListener connectionListener) {
        registeredConnectionListener = connectionListener;
    }

    private void ConnectToMetaWearService() {
        if (serviceBinder == null) {
            Log.e("SkiTracksLogger", "Binding MetaWear service");
            getApplicationContext().bindService(new Intent(this, MetaWearBleService.class),
                    this, Context.BIND_AUTO_CREATE);
        }
    }

    private void DisconnectFromMetaWearService() {
        Log.e("SkiTracksLogger", "Unbinding service");
        getApplicationContext().unbindService(this);
    }

    public void ConnectToMetaWearBoard() {
        final BluetoothManager btManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice =
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        Log.i("SkiTracksLogger", "Get MetaWear Board");
        mwBoard = serviceBinder.getMetaWearBoard(remoteDevice);

        Log.i("SkiTracksLogger", "Connect to MetaWear Board");
        mwBoard.setConnectionStateHandler(connectionStateHandler);
        mwBoard.connect();
    }

    public void DisconnectFromMetaWearBoard() {
        if (connectedToMwBoard && mwBoard != null) {
            mwBoard.disconnect();
        }
    }

    public void StartLogging() {
        StartLogGps();
        StartLogAccel();
        StartLogGyro();
        StartLogMag();
    }

    public void StopLogging() {
        StopLogGps();
        StopLogAccel();
        StopLogGryo();
        StopLogMag();
    }

    private void StartLogGps() {
        Log.i("SkiTracksLogger", "Logging GPS Data");
        gpsWriter = NewOutputStreamWriter("Gps_");
    }

    private void StopLogGps() {
        if(gpsWriter != null) {
            try {
                gpsWriter.close();
                gpsWriter = null;
            } catch (IOException e) {
                Log.e("SkiTracksLogger", "GPS file close failed: " + e.toString());
            }
        }
    }

    private void StartLogAccel() {
        Log.i("SkiTracksLogger", "Logging Accelerometer Data");
        try {
            accelWriter = NewOutputStreamWriter("Accel_");

            bmi160AccModule = mwBoard.getModule(Bmi160Accelerometer.class);

            // Set measurement range to +/- 2G
            // Set output data rate to 25Hz
            final int sampleDeltaMillisecond = (int)(1.0 / 50.0 * 1000.0);
            bmi160AccModule.configureAxisSampling()
                    .setFullScaleRange(Bmi160Accelerometer.AccRange.AR_2G)
                    .setOutputDataRate(Bmi160Accelerometer.OutputDataRate.ODR_50_HZ)
                    .commit();

            MyTimerTask timerTask = new MyTimerTask();
            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(timerTask, 1000, 1000);

            // enable axis sampling
            bmi160AccModule.enableAxisSampling();

            final Logging logger = mwBoard.getModule(Logging.class);

            bmi160AccModule.routeData().fromAxes()
                    .stream("AccData")
                    .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {

                @Override
                public void success(RouteManager result) {
                    result.subscribe("AccData", new RouteManager.MessageHandler() {
                        Calendar sampleTime = null;

                        @Override
                        public void process(Message msg) {
                            ++sampleCount;
                            CartesianFloat axes = CorrectForDeviceMountingOrientation(msg.getData(CartesianFloat.class));

                            // ignore the timestamp in the message because data gets transferred
                            // from the board in blocks so we'll get a couple samples together
                            // with almost identical time stamps. We know the sampling rate
                            // so we'll manually calculate the sample time.
                            if(sampleTime == null)
                            {
                                sampleTime = msg.getTimestamp();
                                sampleTime.add(Calendar.MILLISECOND, -sampleDeltaMillisecond);
                            }

                            sampleTime.add(Calendar.MILLISECOND, sampleDeltaMillisecond);

                            try {
                                accelWriter.write(format.format(sampleTime.getTime()) + ',' + axes.x().toString() + ',' + axes.y().toString() + ',' + axes.z().toString() + '\n');
                                UpdateData(axes.x(), axes.y(), axes.z(), SensorType.ACCEL);
                            } catch (IOException e) {
                                Log.e("Exception", "Accel write failed: " + e.toString());
                            }
                            //Log.i("SkiTracksLogger", "axes:  " + axes.toString());
                        }
                    });

                    // start sampling
                    bmi160AccModule.start();
                }
            });

        } catch (UnsupportedModuleException e) {
            e.printStackTrace();
        }
    }

    private void StopLogAccel() {
        if(bmi160AccModule != null) {
            bmi160AccModule.stop();
            bmi160AccModule.disableAxisSampling();
        }

        if(accelWriter != null) {
            try {
                accelWriter.write(format.format(Calendar.getInstance().getTime()));
                accelWriter.close();
            } catch (IOException e) {
                Log.e("SkiTracksLogger", "Accel file close failed: " + e.toString());
            }
        }
    }

    private void StartLogGyro() {
        Log.i("SkiTracksLogger", "Logging Gyroscope Data");
        try {
            gyroWriter = NewOutputStreamWriter("Gyro_");

            bmi160GyroModule = mwBoard.getModule(Bmi160Gyro.class);

            final int sampleDeltaMillisecond = (int)(1.0 /50.0 * 1000.0);
            bmi160GyroModule.configure()
                    .setFullScaleRange(Bmi160Gyro.FullScaleRange.FSR_250)
                    .setOutputDataRate(Bmi160Gyro.OutputDataRate.ODR_50_HZ)
                    .commit();

            bmi160GyroModule.routeData().fromAxes()
                    .stream("GyroData").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("GyroData", new RouteManager.MessageHandler() {
                        Calendar sampleTime = null;

                        @Override
                        public void process(Message msg) {
                            final CartesianFloat spinData = CorrectForDeviceMountingOrientation(msg.getData(CartesianFloat.class));

                            // ignore the timestamp in the message because data gets transferred
                            // from the board in blocks so we'll get a couple samples together
                            // with almost identical time stamps. We know the sampling rate
                            // so we'll manually calculate the sample time.
                            if(sampleTime == null)
                            {
                                sampleTime = msg.getTimestamp();
                                sampleTime.add(Calendar.MILLISECOND, -sampleDeltaMillisecond);
                            }

                            sampleTime.add(Calendar.MILLISECOND, sampleDeltaMillisecond);

                            try {
                                gyroWriter.write(format.format(sampleTime.getTime()) + ',' + spinData.x().toString() + ',' + spinData.y().toString() + ',' + spinData.z().toString() + '\n');
                                UpdateData(spinData.x(), spinData.y(), spinData.z(), SensorType.GYRO);
                            } catch (IOException e) {
                                Log.e("Exception", "GyroData write failed: " + e.toString());
                            }
                            //Log.i("SkiTracksLogger", "gyro:  " + spinData.toString());
                        }
                    });

                    // start sampling
                    bmi160GyroModule.start();
                }
            });

        } catch (UnsupportedModuleException e) {
            e.printStackTrace();
        }
    }

    private void StopLogGryo() {
        if(bmi160GyroModule != null) {
            bmi160GyroModule.stop();
        }

        if(gyroWriter != null) {
            try {
                gyroWriter.close();
            } catch (IOException e) {
                Log.e("SkiTracksLogger", "Gyro file close failed: " + e.toString());
            }
        }
    }

    double mx;
    double my;
    double mz;
    private void StartLogMag() {
        Log.i("SkiTracksLogger", "Logging Magnetometer Data");
        try {
           magWriter = NewOutputStreamWriter("Mag_");

            bmm150MagModule = mwBoard.getModule(Bmm150Magnetometer.class);
            bmm150MagModule.setPowerPreset(Bmm150Magnetometer.PowerPreset.ENHANCED_REGULAR);
            bmm150MagModule.enableBFieldSampling();

            // HIGH_ACCURACY mode samples at 10Hz
            final int sampleDeltaMillisecond = (int)(1.0 / 10.0 * 1000.0);

            bmm150MagModule.routeData()
                    .fromBField()
                    .stream("MagData")
                    .commit()
                    .onComplete(
                            new AsyncOperation.CompletionHandler<RouteManager>() {
                                @Override
                                public void success(RouteManager result) {
                                    result.subscribe("MagData", new RouteManager.MessageHandler() {
                                        Calendar sampleTime = null;

                                        final double hardIronCorrectionX = -3.155170;
                                        final double hardIronCorrectionY = 12.191927;
                                        final double hardIronCorrectionZ = -9.894998;

                                        final double[] softIronMtx1 = {0.962221, -0.004818, 0.002260};
                                        final double[] softIronMtx2 = {-0.004818, 0.959747, -0.004253};
                                        final double[] softIronMtx3 = {0.002260, -0.004253, 1.038005};

                                        @Override
                                        public void process(Message msg) {
                                            CartesianFloat magData = msg.getData(CartesianFloat.class);

                                            // correct for hard iron distortion
                                            double x = magData.x() - hardIronCorrectionX;
                                            double y = magData.y() - hardIronCorrectionY;
                                            double z = magData.z() - hardIronCorrectionZ;

                                            double correctedX = softIronMtx1[0] * x + softIronMtx1[1] * y + softIronMtx1[2] * z;
                                            double correctedY = softIronMtx2[0] * x + softIronMtx2[1] * y + softIronMtx2[2] * z;
                                            double correctedZ = softIronMtx3[0] * x + softIronMtx3[1] * y + softIronMtx3[2] * z;

                                            // The magnetometer isn't oriented in the same direction as the accel & gyro.
                                            // It's rotated 180 degrees around the x axis, so correct so it matches
                                            mx = correctedX;
                                            my = -correctedY;
                                            mz = -correctedZ;

                                            magData = CorrectForDeviceMountingOrientation(new CartesianFloatImpl((float)mx, (float)my, (float)mz));

                                            double heading = -Math.atan2(magData.y(), magData.x()) * 180.0 / 3.14159;

                                            // ignore the timestamp in the message because data gets transferred
                                            // from the board in blocks so we'll get a couple samples together
                                            // with almost identical time stamps. We know the sampling rate
                                            // so we'll manually calculate the sample time.
                                            if (sampleTime == null) {
                                                sampleTime = msg.getTimestamp();
                                                sampleTime.add(Calendar.MILLISECOND, -sampleDeltaMillisecond);
                                            }

                                            sampleTime.add(Calendar.MILLISECOND, sampleDeltaMillisecond);

                                            try {
                                                magWriter.write(format.format(sampleTime.getTime()) + ',' + magData.x() + ',' + magData.y() + ',' + magData.z() + '\n');
                                                UpdateData(magData.x(), magData.y(), magData.z(), SensorType.MAG);
                                            } catch (IOException e) {
                                                Log.e("Exception", "MagData write failed: " + e.toString());
                                            }
                                            //Log.i("SkiTracksLogger", "mag:  " + magData.toString());
                                            //Log.i("SkiTracksLogger", "heading:  " + String.format("%.2f", heading));
                                        }
                                    });

                                    // start sampling
                                    bmm150MagModule.start();
                                }
                            });

        } catch (UnsupportedModuleException e) {
            e.printStackTrace();
        }
    }

    private void StopLogMag() {
        if(bmm150MagModule != null) {
            bmm150MagModule.stop();
            bmm150MagModule.disableBFieldSampling();
        }

        if(magWriter != null) {
            try {
                magWriter.close();
            } catch (IOException e) {
                Log.e("SkiTracksLogger", "Mag file close failed: " + e.toString());
            }
        }
    }

    private enum SensorType
    {
        ACCEL,
        GYRO,
        MAG
    }

    private boolean accelUpdated = false;
    private boolean gyroUpdated = false;
    double ax, ay, az;
    double gx, gy, gz;
    double magx, magy, magz;
    MadgwickAHRSIMU sensorFusion = null;
    private void UpdateData(double x, double y, double z, SensorType sensorType)
    {
        synchronized(this)
        {
            if(sensorFusion == null)
            {
                double[] q = {0, 0, 0, 1};
                sensorFusion = new MadgwickAHRSIMU(4000, q, 36);
            }

            switch (sensorType) {
                case ACCEL:
                    ax = x;
                    ay = y;
                    az = z;
                    accelUpdated = true;
                    break;

                case GYRO:
                    gx = x;
                    gy = y;
                    gz = z;
                    gyroUpdated = true;
                    break;

                case MAG:
                    magx = x;
                    magy = y;
                    magz = z;
                    break;
            }

            if(accelUpdated == true && gyroUpdated == true)
            {
                sensorFusion.AHRSUpdate(gx, gy, gz, ax, ay, az, magx, magy, magz);
                accelUpdated = false;
                gyroUpdated = false;

                double[] q = sensorFusion.getOrientationQuaternion();
                registeredConnectionListener.UpdateQuaternion(q);
                //Log.i("SkiTracksLogger", "q:  " + q[0] + " " + q[1] + " " + q[2] + " " + q[3]);
            }
        }
    }

    private OutputStreamWriter NewOutputStreamWriter(String prefix) {
        SimpleDateFormat format = new SimpleDateFormat("MM_dd_yyyy_hh_mm_ss");
        String fileName = prefix + format.format(Calendar.getInstance().getTime()) + ".csv";
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        dir.mkdirs();
        File file = new File(dir, fileName);
        try {
            FileOutputStream outStream = new FileOutputStream(file);
            return new OutputStreamWriter(outStream);
        } catch (FileNotFoundException e) {
            Log.e("SkiTracksLogger", "Accel write failed: " + e.toString());
            e.printStackTrace();
            return null;
        }
    }

    private class CartesianFloatImpl extends CartesianFloat {

        @Override
        public Float x() {
            return m_x;
        }

        @Override
        public Float y() {
            return m_y;
        }

        @Override
        public Float z() {
            return m_z;
        }

        CartesianFloatImpl(float x, float y, float z)
        {
            m_x = x;
            m_y = y;
            m_z = z;
        }

        private float m_x;
        private float m_y;
        private float m_z;
    }

    private CartesianFloat CorrectForDeviceMountingOrientation(CartesianFloat device)
    {
        // Our post processing application to compute quaternions expects an North East Down
        // reference frame. When facing north, we expect the +X axis to be pointing north,
        // the +Y axis to be pointing east, and the +Z axis to be pointing down.
        // If the device is mounted in a different orientation, we need to correct for
        // that here

        // Helmet mount:
        // When facing north +Y points east, +Z points north, and +X points up.
        CartesianFloat corrected = new CartesianFloatImpl(device.z(), device.y(), -device.x());

        return corrected;
    }
}
