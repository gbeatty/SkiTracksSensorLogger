/*
Copyright (c) 2013 - 2016, NXP Semiconductor, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.
 * Neither the name of NXP Semiconductor, Inc. nor the
   names of its contributors may be used to endorse or promote products
   derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL NXP SEMICONDUCTOR, INC. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.gbeatty.skitrackssensorlogger;

import java.io.File;

import android.app.AlertDialog;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Environment;

/**
* Basically a set of utility functions, many of which need a pointer to the main application instance.
* @author Michael Stanley
*/
public class MyUtils {
	static public final float degreesPerRadian = (float) (180.0f/3.14159f);
	static public final float radiansPerDegree = (float) (3.14159f/180.0f);
	public enum AngleUnits {  DEGREES, RADIANS }
	static public float unitScaler = 32768.0f;

	MyUtils() {

	}

	static public float[] transpose(float[] orig_rm) {
		// Input matrix is of the form:
		// rm0 rm1 rm2
		// rm3 rm4 rm5
		// rm6 rm7 rm8
		assert(orig_rm.length==9); // rm is assumed to be 3x3 matrix of form
		float[] rm = new float[9];
		rm[0]=orig_rm[0]; rm[4]=orig_rm[4]; rm[8]=orig_rm[8];
		rm[3]=orig_rm[1]; rm[1]=orig_rm[3];
		rm[6]=orig_rm[2]; rm[2]=orig_rm[6];
		rm[7]=orig_rm[5]; rm[5]=orig_rm[7];
		return(rm);
	}
	static public float dotProduct(float[] a, float[] b) {
		// a and b are assumed to be the same length
		assert(a.length==b.length);
		float dp = 0;
		for (int i = 0; i < b.length; i++) {
			dp = dp + a[i]*b[i];
		}
		return(dp);
	}
	static public void computeCrossProduct(float[] cp, float[] a, float[] b) {
		// a and b are assumed to be 3x1 vectors
		assert(a.length==3);
		assert(b.length==3);
		cp[0] = a[1]*b[2]-a[2]*b[1];
		cp[1] = a[2]*b[0]-a[0]*b[2];
		cp[2] = a[0]*b[1]-a[1]*b[0];
	}
	static public float norm(float[] v) {
		int n = v.length;
		float norm = 0;
		for (int i = 0; i < n; i++) {
			norm = norm + v[i]*v[i];
		}
		norm = (float) Math.sqrt(norm);
		return(norm);
	}
	static public void normalize(float[] vout, float[] vin) {
		scalarMult(vout, vin, 1/norm(vin));
	}
	static public void scalarMult(float[] vout, float[] vin, float s ) {
		assert(vin.length==vout.length);
		for (int i = 0; i < vout.length; i++) {
			vout[i] = s*vin[i];
		}
	}
	static public float axisAngle(float[] axis, float[] v1, float[] v2) {
		assert(v1.length==3);
		assert(v2.length==3);
		float [] cp  = new float[3]; // cross product
		float [] nv1 = new float[3]; // normalized version of v1
		float [] nv2 = new float[3]; // normalized version of v2
		normalize(nv1, v1);
		normalize(nv2, v2);
		float cosAngle = dotProduct(nv1, nv2);
		float angle = (float) Math.acos(cosAngle);
		computeCrossProduct(cp, nv1, nv2);
		normalize(axis, cp);
		return(angle); // Angle is in radians
	}
	
	
	static public float axisAngleD(float[] axis, float[] v1, float[] v2) {
		return(degreesPerRadian*axisAngle(axis, v1, v2));
	}	

	static public float vectorAngle(float[] v1, float[] v2) {
		assert(v1.length==3);
		assert(v2.length==3);
		float [] nv1 = new float[3]; // normalized version of v1
		float [] nv2 = new float[3]; // normalized version of v2
		normalize(nv1, v1);
		normalize(nv2, v2);
		float cosAngle = dotProduct(nv1, nv2);
		float angle = (float) Math.acos(cosAngle);
		return(angle); // Angle is in radians
	}
	
	
	static public float vectorAngleD(float[] v1, float[] v2) {
		return(degreesPerRadian*vectorAngle(v1, v2));
	}	
	static public float vectorAngleD(Triad v1, Triad v2) {
		return(degreesPerRadian*vectorAngle(v1.array(), v2.array()));
	}	
	
	
	
	// Xtrinsic Android accelerometer 3DOF tilt function computing rotation matrix RM
	// This is an alternate implementation which can be used for the "local 3-axis" solution.
	// Currently not used, but kept for future reference.
	static void f3DOFTiltAndroid(float[][] RM, float[] accelReading)
	{
		// local variables
		int X=0;
		int Y=1;
		int Z=2;
		int i;
		float g[] = new float[3];        // normalized accelerometer readings
		float fmodGxyz;                  // modulus of the x, y, z accelerometer readings
		float fmodGyz;                   // modulus of the y, z accelerometer readings
		float ftmp;                      // scratch variable

		// compute the accelerometer magnitude and R00
		fmodGyz = accelReading[Y] * accelReading[Y] + accelReading[Z] * accelReading[Z];
		fmodGxyz = fmodGyz + accelReading[X] * accelReading[X];
		fmodGyz = (float) Math.sqrt(fmodGyz);
		fmodGxyz = (float) Math.sqrt(fmodGxyz);

		// check for freefall and gimbal lock
		if ((fmodGxyz != 0.0F) && (fmodGyz != 0.0F))
		{
			// normalize the accelerometer reading and R00
			for (i = X; i <= Z; i++)
			{
				g[i] = accelReading[i] / fmodGxyz;
			}

			// construct the orientation matrix
			RM[X][X] = fmodGyz / fmodGxyz;
			ftmp = 1.0F / RM[X][X];
			RM[Y][X] = -g[X] * g[Y] * ftmp;
			RM[Z][X] = -g[X] * g[Z] * ftmp;

			RM[X][Y] = 0.0F;
			RM[Y][Y] = g[Z] * ftmp;
			RM[Z][Y] = -g[Y] * ftmp;

			RM[X][Z] = g[X];
			RM[Y][Z] = g[Y];
			RM[Z][Z] = g[Z];
		}
		else
		{
			RM[0][0]=1.0f;
			RM[0][1]=0.0f;
			RM[0][2]=0.0f;
			RM[1][0]=0.0f;
			RM[1][1]=1.0f;
			RM[1][2]=0.0f;
			RM[2][0]=0.0f;
			RM[2][1]=0.0f;
			RM[2][2]=1.0f;
		}
		return;
	}
	
	
	static void toFloatArray(float[] dataOut, double[] dataIn) {
		assert(dataIn.length==dataOut.length);
		for (int i = 0; i < dataOut.length; i++) {
			dataOut[i] = (float)dataIn[i];
		}
	}
	static void toDoubleArray(double[] dataOut, float[] dataIn) {
		assert(dataIn.length==dataOut.length);
		for (int i = 0; i < dataOut.length; i++) {
			dataOut[i] = (double)dataIn[i];
		}
	}

	static public void waitALittle(int delay) {
		try {
			Thread.sleep(delay); 
		} catch (Throwable t) { }
	}
	static public File getFile(String name) {
		File sdDir = null, testDir = null, filePlaceholder = null;
		sdDir 	= new File(Environment.getExternalStorageDirectory().getPath());
		testDir = new File(sdDir.getAbsolutePath() + java.io.File.separator + "NXP_Demo" );
		filePlaceholder 	= new File(testDir.getAbsolutePath() + java.io.File.separator + name);
		if (testDir.isDirectory()) {
			// do nothing
		} else {
			testDir.mkdir();
		}
		return(filePlaceholder);
	}
	static public String getFilePath(String name) {
		File filePlaceholder = getFile(name);
		return(filePlaceholder.getAbsolutePath());
	}
	static public Uri getFileUri(String name) {
		File filePlaceholder = getFile(name);
		Uri uri = Uri.fromFile(filePlaceholder);
		return(uri);
	}
	static int limitI(int val, int lim) {
		if (val<-lim) { 
			val = -lim;
		} else if (val>lim) {
			val = lim;
		}
		return (val);
	}
	static long limitL(long val, long lim) {
		if (val<-lim) { 
			val = -lim;
		} else if (val>lim) {
			val = lim;
		}
		return (val);
	}
	static double limitD(double val, double lim) {
		if (val<-lim) { 
			val = -lim;
		} else if (val>lim) {
			val = lim;
		}
		return (val);
	}


}
