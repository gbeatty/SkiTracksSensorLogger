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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Timer;
import java.util.TimerTask;

public class LoggingService extends Service implements ServiceConnection, LocationListener{

    SensorFusion sf = new SensorFusion();
    private LinkedBlockingQueue<double[]> accelSamples = new LinkedBlockingQueue<double[]>();
    private LinkedBlockingQueue<double[]> gyroSamples = new LinkedBlockingQueue<double[]>();
    private double[] currentMagSample = null;

    private void StartDataProcessing()
    {
        startRateMonitor(1000);
        startDataUpdate(10);
        startDataReporting(40);
    }

    private void StopDataProcessing()
    {
        rateMonitorTask.cancel();
        dataUpdateTask.cancel();
        dataReportingTask.cancel();
        accelSamples.clear();
        gyroSamples.clear();
    }

    private double lastTimerTime = 0;
    private AtomicInteger numFailedSamples = new AtomicInteger(0);
    private AtomicInteger numProcessedSamples = new AtomicInteger(0);
    private AtomicInteger backlogSize = new AtomicInteger(0);
    private TimerTask rateMonitorTask;
    private void startRateMonitor(int period) {

        rateMonitorTask = new TimerTask() {
            public void run() {
                double currentTime = System.currentTimeMillis();

                if(lastTimerTime != 0) {

                    double deltaTime = currentTime - lastTimerTime;
                    int numSamples = numProcessedSamples.getAndSet(0);
                    Log.i("SkiTracksLogger", "Rate:  " + numSamples / deltaTime * 1000);

                    int backSize = backlogSize.get();
                    int failedSize = numFailedSamples.getAndSet(0);
                    boolean backlogged = backSize > 10;
                    boolean ranDry = failedSize > 1;

                    Log.i("SkiTracksLogger", "Backlog size: " + backSize + "    Failed: " + failedSize);

                    if (backlogged && ranDry) {
                        // Our streaming rate must be fluctuating a lot, so leave the update rate alone
                        Log.i("SkiTracksLogger", "Rate Fluctuating");
                    } else {
                        int newPeriod;
                        if (backlogged) {
                            // pick up the pace, 20% faster
                            newPeriod = updateTaskPeriod - (int) ((double) updateTaskPeriod * 0.2);
                            Log.i("SkiTracksLogger", "Speeding Up: " + newPeriod);


                        } else {
                            // slow down, 20% slower
                            newPeriod = updateTaskPeriod + (int) ((double) updateTaskPeriod * 0.2);
                            Log.i("SkiTracksLogger", "Slowing Down: " + newPeriod);

                        }

                        dataUpdateTask.cancel();
                        startDataUpdate(newPeriod);
                    }
                }

                lastTimerTime = currentTime;
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(rateMonitorTask, 0, period);
    }

    private TimerTask dataUpdateTask;
    private int updateTaskPeriod;
    private void startDataUpdate(int period) {

        dataUpdateTask = new TimerTask() {
            public void run() {
                if (accelSamples.isEmpty() || gyroSamples.isEmpty() || currentMagSample == null) {
                    numFailedSamples.incrementAndGet();
                }
                else {
                    try {
                        double[] accel = accelSamples.take();
                        double[] gyro = gyroSamples.take();

                        numProcessedSamples.incrementAndGet();
                        backlogSize.set(accelSamples.size());

                        float[] magFloat = {(float) currentMagSample[0], (float) currentMagSample[1], (float) currentMagSample[2]};
                        sf.SetMag(magFloat);

                        float[] accelFloat = {(float) accel[0], (float) accel[1], (float) accel[2]};
                        sf.SetAccel(accelFloat);

                        float[] gyroFloat = {(float) gyro[0], (float) gyro[1], (float) gyro[2]};
                        sf.SetGyro(gyroFloat, (float) System.currentTimeMillis() / 1000.0f);
                    }
                    catch (InterruptedException e) {
                    }
                }
            }
        };

        Timer timer = new Timer();
        timer.schedule(dataUpdateTask, 0, period);
        updateTaskPeriod = period;
    }

    private TimerTask dataReportingTask;
    private void startDataReporting(int period) {

        dataReportingTask = new TimerTask() {
            public void run() {
                registeredConnectionListener.UpdateRotationVector(sf.gyroMatrix);
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(dataReportingTask, 0, period);
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

        Notification notification = new Notification.Builder(this)
                .setContentTitle("SkiTracks Logger Service")
                .setTicker("SkiTracks Logger Service")
                .setContentText("SkiTracks Logger Service")
                .setOngoing(true)
                .build();
        startForeground(101, notification);
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

        StartDataProcessing();
    }

    public void StopLogging() {
        StopLogGps();
        StopLogAccel();
        StopLogGryo();
        StopLogMag();
        StopDataProcessing();
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
            //accelWriter = NewOutputStreamWriter("Accel_");

            bmi160AccModule = mwBoard.getModule(Bmi160Accelerometer.class);

            // Set measurement range to +/- 2G
            // Set output data rate to 25Hz
            final int sampleDeltaMillisecond = (int)(1.0 / 100.0 * 1000.0);
            bmi160AccModule.configureAxisSampling()
                    .setFullScaleRange(Bmi160Accelerometer.AccRange.AR_2G)
                    .setOutputDataRate(Bmi160Accelerometer.OutputDataRate.ODR_100_HZ)
                    .commit();

            // enable axis sampling
            bmi160AccModule.enableAxisSampling();

            final Logging logger = mwBoard.getModule(Logging.class);

            bmi160AccModule.routeData().fromHighFreqAxes()
                    .stream("AccData")
                    .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {

                @Override
                public void success(RouteManager result) {
                    result.subscribe("AccData", new RouteManager.MessageHandler() {
                        Calendar sampleTime = null;

                        @Override
                        public void process(Message msg) {
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

                            //try {
                                //accelWriter.write(format.format(sampleTime.getTime()) + ',' + axes.x().toString() + ',' + axes.y().toString() + ',' + axes.z().toString() + '\n');
                                accelSamples.add(new double[]{axes.x(), axes.y(), axes.z()});
                            //} catch (IOException e) {
                            //    Log.e("Exception", "Accel write failed: " + e.toString());
                            //}
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
            //gyroWriter = NewOutputStreamWriter("Gyro_");

            bmi160GyroModule = mwBoard.getModule(Bmi160Gyro.class);

            final int sampleDeltaMillisecond = (int)(1.0 /100.0 * 1000.0);
            bmi160GyroModule.configure()
                    .setFullScaleRange(Bmi160Gyro.FullScaleRange.FSR_250)
                    .setOutputDataRate(Bmi160Gyro.OutputDataRate.ODR_100_HZ)
                    .commit();

            bmi160GyroModule.routeData().fromHighFreqAxes()
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

                            //try {
                                //gyroWriter.write(format.format(sampleTime.getTime()) + ',' + spinData.x().toString() + ',' + spinData.y().toString() + ',' + spinData.z().toString() + '\n');
                                gyroSamples.add(new double[]{spinData.x(), spinData.y(), spinData.z()});
                            //} catch (IOException e) {
                            //    Log.e("Exception", "GyroData write failed: " + e.toString());
                            //}
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
           //magWriter = NewOutputStreamWriter("Mag_");

            bmm150MagModule = mwBoard.getModule(Bmm150Magnetometer.class);
            bmm150MagModule.setPowerPreset(Bmm150Magnetometer.PowerPreset.ENHANCED_REGULAR);
            bmm150MagModule.enableBFieldSampling();

            // ENHANCED_REGULAR mode samples at 10Hz
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
                                            /*double x = magData.x() - hardIronCorrectionX;
                                            double y = magData.y() - hardIronCorrectionY;
                                            double z = magData.z() - hardIronCorrectionZ;

                                            double correctedX = softIronMtx1[0] * x + softIronMtx1[1] * y + softIronMtx1[2] * z;
                                            double correctedY = softIronMtx2[0] * x + softIronMtx2[1] * y + softIronMtx2[2] * z;
                                            double correctedZ = softIronMtx3[0] * x + softIronMtx3[1] * y + softIronMtx3[2] * z;*/

                                            // The magnetometer isn't oriented in the same direction as the accel & gyro.
                                            // It's rotated 180 degrees around the x axis, so correct so it matches
                                            mx =  magData.x();
                                            my = -magData.y();
                                            mz = -magData.z();

                                            magData = CorrectForDeviceMountingOrientation(new CartesianFloatImpl((float)mx, (float)my, (float)mz));

                                            // ignore the timestamp in the message because data gets transferred
                                            // from the board in blocks so we'll get a couple samples together
                                            // with almost identical time stamps. We know the sampling rate
                                            // so we'll manually calculate the sample time.
                                            if (sampleTime == null) {
                                                sampleTime = msg.getTimestamp();
                                                sampleTime.add(Calendar.MILLISECOND, -sampleDeltaMillisecond);
                                            }

                                            sampleTime.add(Calendar.MILLISECOND, sampleDeltaMillisecond);

                                            //try {
                                                //magWriter.write(format.format(sampleTime.getTime()) + ',' + magData.x() + ',' + magData.y() + ',' + magData.z() + '\n');
                                                currentMagSample = new double[]{magData.x(), magData.y(), magData.z()};
                                            //} catch (IOException e) {
                                            //    Log.e("Exception", "MagData write failed: " + e.toString());
                                            //}
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
