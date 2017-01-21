package com.gbeatty.skitrackssensorlogger;

/**
 * Created by Greg on 1/27/2016.
 */
public interface MwBoardConnectionListener {
    void Connected();
    void Disconnected();
    void UpdateQuaternion(double[] q);
    void UpdateRotationVector(float[] rotationMtx);
}
