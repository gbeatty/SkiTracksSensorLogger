/**
	Port of Seb Madqwick's open source IMU AHRS C implementation http://www.x-io.co.uk/open-source-imu-and-ahrs-algorithms/
	Decided to changed to double precision instead of float. Don't know whether it matters.
	Written by Timo Rantalainen 2014 tjrantal at gmail dot com
	Licensed with GPL 3.0 (https://www.gnu.org/copyleft/gpl.html)
*/
package com.gbeatty.skitrackssensorlogger;
public class MadgwickAHRSIMU extends MadgwickAHRS{
	/**
		Constructor
		@param beta algorithm gain
		@param q orientation quaternion
		@param samplingFreq Sampling frequency
	*/
	public MadgwickAHRSIMU(double beta, double[] q, double samplingFreq){
		super(beta,q,samplingFreq);
	}

    /**
     Update the orientation according to the latest set of measurements
     */
    @Override
    public void AHRSUpdate(double gx, double gy, double gz, double ax, double ay, double az, double mx, double my, double mz)
    {
        double recipNorm;
        double s0, s1, s2, s3;
        double qDot1, qDot2, qDot3, qDot4;
        double hx, hy;
        double _2q0mx, _2q0my, _2q0mz, _2q1mx, _2bx, _2bz, _4bx, _4bz, _2q0, _2q1, _2q2, _2q3, _2q0q2, _2q2q3, q0q0, q0q1, q0q2, q0q3, q1q1, q1q2, q1q3, q2q2, q2q3, q3q3;

        // Use IMU algorithm if magnetometer measurement invalid (avoids NaN in magnetometer normalisation)
        if((mx == 0.0f) && (my == 0.0f) && (mz == 0.0f)) {
            double[] IMUdata = {gx, gy, gz, ax, ay, az};
            IMUUpdate(IMUdata);
            return;
        }

        // Rate of change of quaternion from gyroscope
        qDot1 = 0.5 * (-q[1] * gx - q[2] * gy - q[3] * gz);
        qDot2 = 0.5 * (q[0] * gx + q[2] * gz - q[3] * gy);
        qDot3 = 0.5 * (q[0] * gy - q[1] * gz + q[3] * gx);
        qDot4 = 0.5 * (q[0] * gz + q[1] * gy - q[2] * gx);

        // Compute feedback only if accelerometer measurement valid (avoids NaN in accelerometer normalisation)
        if(!((ax == 0.0f) && (ay == 0.0f) && (az == 0.0f))) {

            // Normalise accelerometer measurement
            recipNorm = invSqrt(ax * ax + ay * ay + az * az);
            ax *= recipNorm;
            ay *= recipNorm;
            az *= recipNorm;

            // Normalise magnetometer measurement
            recipNorm = invSqrt(mx * mx + my * my + mz * mz);
            mx *= recipNorm;
            my *= recipNorm;
            mz *= recipNorm;

            // Auxiliary variables to avoid repeated arithmetic
            _2q0mx = 2.0f * q[0] * mx;
            _2q0my = 2.0f * q[0] * my;
            _2q0mz = 2.0f * q[0] * mz;
            _2q1mx = 2.0f * q[1] * mx;
            _2q0 = 2.0f * q[0];
            _2q1 = 2.0f * q[1];
            _2q2 = 2.0f * q[2];
            _2q3 = 2.0f * q[3];
            _2q0q2 = 2.0f * q[0] * q[2];
            _2q2q3 = 2.0f * q[2] * q[3];
            q0q0 = q[0] * q[0];
            q0q1 = q[0] * q[1];
            q0q2 = q[0] * q[2];
            q0q3 = q[0] * q[3];
            q1q1 = q[1] * q[1];
            q1q2 = q[1] * q[2];
            q1q3 = q[1] * q[3];
            q2q2 = q[2] * q[2];
            q2q3 = q[2] * q[3];
            q3q3 = q[3] * q[3];

            // Reference direction of Earth's magnetic field
            hx = mx * q0q0 - _2q0my * q[3] + _2q0mz * q[2] + mx * q1q1 + _2q1 * my * q[2] + _2q1 * mz * q[3] - mx * q2q2 - mx * q3q3;
            hy = _2q0mx * q[3] + my * q0q0 - _2q0mz * q[1] + _2q1mx * q[2] - my * q1q1 + my * q2q2 + _2q2 * mz * q[3] - my * q3q3;
            _2bx = Math.sqrt(hx * hx + hy * hy);
            _2bz = -_2q0mx * q[2] + _2q0my * q[1] + mz * q0q0 + _2q1mx * q[3] - mz * q1q1 + _2q2 * my * q[3] - mz * q2q2 + mz * q3q3;
            _4bx = 2.0f * _2bx;
            _4bz = 2.0f * _2bz;

            // Gradient decent algorithm corrective step
            s0 = -_2q2 * (2.0f * q1q3 - _2q0q2 - ax) + _2q1 * (2.0f * q0q1 + _2q2q3 - ay) - _2bz * q[2] * (_2bx * (0.5f - q2q2 - q3q3) + _2bz * (q1q3 - q0q2) - mx) + (-_2bx * q[3] + _2bz * q[1]) * (_2bx * (q1q2 - q0q3) + _2bz * (q0q1 + q2q3) - my) + _2bx * q[2] * (_2bx * (q0q2 + q1q3) + _2bz * (0.5f - q1q1 - q2q2) - mz);
            s1 = _2q3 * (2.0f * q1q3 - _2q0q2 - ax) + _2q0 * (2.0f * q0q1 + _2q2q3 - ay) - 4.0f * q[1] * (1 - 2.0f * q1q1 - 2.0f * q2q2 - az) + _2bz * q[3] * (_2bx * (0.5f - q2q2 - q3q3) + _2bz * (q1q3 - q0q2) - mx) + (_2bx * q[2] + _2bz * q[0]) * (_2bx * (q1q2 - q0q3) + _2bz * (q0q1 + q2q3) - my) + (_2bx * q[3] - _4bz * q[1]) * (_2bx * (q0q2 + q1q3) + _2bz * (0.5f - q1q1 - q2q2) - mz);
            s2 = -_2q0 * (2.0f * q1q3 - _2q0q2 - ax) + _2q3 * (2.0f * q0q1 + _2q2q3 - ay) - 4.0f * q[2] * (1 - 2.0f * q1q1 - 2.0f * q2q2 - az) + (-_4bx * q[2] - _2bz * q[0]) * (_2bx * (0.5f - q2q2 - q3q3) + _2bz * (q1q3 - q0q2) - mx) + (_2bx * q[1] + _2bz * q[3]) * (_2bx * (q1q2 - q0q3) + _2bz * (q0q1 + q2q3) - my) + (_2bx * q[0] - _4bz * q[2]) * (_2bx * (q0q2 + q1q3) + _2bz * (0.5f - q1q1 - q2q2) - mz);
            s3 = _2q1 * (2.0f * q1q3 - _2q0q2 - ax) + _2q2 * (2.0f * q0q1 + _2q2q3 - ay) + (-_4bx * q[3] + _2bz * q[1]) * (_2bx * (0.5f - q2q2 - q3q3) + _2bz * (q1q3 - q0q2) - mx) + (-_2bx * q[0] + _2bz * q[2]) * (_2bx * (q1q2 - q0q3) + _2bz * (q0q1 + q2q3) - my) + _2bx * q[1] * (_2bx * (q0q2 + q1q3) + _2bz * (0.5f - q1q1 - q2q2) - mz);
            recipNorm = invSqrt(s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3); // normalise step magnitude
            s0 *= recipNorm;
            s1 *= recipNorm;
            s2 *= recipNorm;
            s3 *= recipNorm;

            // Apply feedback step
            qDot1 -= beta * s0;
            qDot2 -= beta * s1;
            qDot3 -= beta * s2;
            qDot4 -= beta * s3;
        }

        // Integrate rate of change of quaternion to yield quaternion
        q[0] += qDot1 * (1.0f / samplingFreq);
        q[1] += qDot2 * (1.0f / samplingFreq);
        q[2] += qDot3 * (1.0f / samplingFreq);
        q[3] += qDot4 * (1.0f / samplingFreq);

        // Normalise quaternion
        recipNorm = invSqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        q[0] *= recipNorm;
        q[1] *= recipNorm;
        q[2] *= recipNorm;
        q[3] *= recipNorm;
    }

	/**
	 Update the orientation according to the latest set of measurements
	 @param IMUdata The latest set of IMU or MARG data [0-2] gyro, [3-5] accelerometer, {[6-8] magnetometer}
	 */
	@Override
	public void  IMUUpdate(double[] IMUdata)
    {
		double recipNorm;
		double[] s		= new double[4];
		double[] qDot	= new double[4];
		double[] _2q	= new double[4];
		double[] _4q	= new double[3];
		double[] _8q	= new double[2];
		double[] qq		= new double[4];
		
		// Rate of change of quaternion from gyroscope
		qDot[0] = 0.5d * (-q[1] * IMUdata[0] - q[2] * IMUdata[1] - q[3] * IMUdata[2]);
		qDot[1] = 0.5d * (q[0] * IMUdata[0] + q[2] * IMUdata[2] - q[3] * IMUdata[1]);
		qDot[2] = 0.5d * (q[0] * IMUdata[1] - q[1] * IMUdata[2] + q[3] * IMUdata[0]);
		qDot[3] = 0.5d * (q[0] * IMUdata[2] + q[1] * IMUdata[1] - q[2] * IMUdata[0]);

		// Compute feedback only if accelerometer measurement valid (avoids NaN in accelerometer normalisation)
		if(!((IMUdata[3] == 0d) && (IMUdata[4] == 0d) && (IMUdata[5] == 0d))) {

			// Normalise accelerometer measurement
			recipNorm	= invSqrt(IMUdata[3] * IMUdata[3] + IMUdata[4] * IMUdata[4] + IMUdata[5] * IMUdata[5]);
			IMUdata[3]	*= recipNorm;
			IMUdata[4]	*= recipNorm;
			IMUdata[5]	*= recipNorm;

			// Auxiliary variables to avoid repeated arithmetic
			_2q[0] = 2d * q[0];
			_2q[1] = 2d * q[1];
			_2q[2] = 2d * q[2];
			_2q[3] = 2d * q[3];
			_4q[0] = 4d * q[0];
			_4q[1] = 4d * q[1];
			_4q[2] = 4d * q[2];
			_8q[0] = 8d * q[1];
			_8q[1] = 8d * q[2];
			qq[0] = q[0] * q[0];
			qq[1] = q[1] * q[1];
			qq[2] = q[2] * q[2];
			qq[3] = q[3] * q[3];

			// Gradient decent algorithm corrective step
			s[0] = _4q[0] * qq[2] + _2q[2] * IMUdata[3] + _4q[0] * qq[1] - _2q[1] * IMUdata[4];
			s[1] = _4q[1] * qq[3] - _2q[3] * IMUdata[3] + 4.0f * qq[0] * q[1] - _2q[0] * IMUdata[4] - _4q[1] + _8q[0] * qq[1] + _8q[0] * qq[2] + _4q[1] * IMUdata[5];
			s[2] = 4.0f * qq[0] * q[2] + _2q[0] * IMUdata[3] + _4q[2] * qq[3] - _2q[3] * IMUdata[4] - _4q[2] + _8q[1] * qq[1] + _8q[1] * qq[2] + _4q[2] * IMUdata[5];
			s[3] = 4.0f * qq[1] * q[3] - _2q[1] * IMUdata[3] + 4.0f * qq[2] * q[3] - _2q[2] * IMUdata[4];
			recipNorm = invSqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2] + s[3] * s[3]); // normalise step magnitude
			s[0] *= recipNorm;
			s[1] *= recipNorm;
			s[2] *= recipNorm;
			s[3] *= recipNorm;

			// Apply feedback step
			qDot[0] -= beta * s[0];
			qDot[1] -= beta * s[1];
			qDot[2] -= beta * s[2];
			qDot[3] -= beta * s[3];
		}

		// Integrate rate of change of quaternion to yield quaternion
		q[0] += qDot[0] * (1.0f / samplingFreq);
		q[1] += qDot[1] * (1.0f / samplingFreq);
		q[2] += qDot[2] * (1.0f / samplingFreq);
		q[3] += qDot[3] * (1.0f / samplingFreq);

		// Normalise quaternion
		recipNorm = invSqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
		q[0] *= recipNorm;
		q[1] *= recipNorm;
		q[2] *= recipNorm;
		q[3] *= recipNorm;	
	}
}