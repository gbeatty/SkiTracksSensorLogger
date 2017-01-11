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
import com.gbeatty.skitrackssensorlogger.MyUtils.AngleUnits;

/**
* Defines a basic "rotation vector" which is comprised of an axis of rotation defined as the vector
* from 0,0,0 to x,y,z, and angle of rotation.  This is not a generalized library.  It only implements the
* functions required for this application.
* @author Michael Stanley
*/
public class RotationVector {
	// a = angle in radians OR degrees (this is up to the programmer, NOT enforced here
	// x, y, z = coordinates of vector about which we will do the rotation.
	public AngleUnits units;
	public float a, x, y, z;
	RotationVector() {
		this.setIdentity();
	}
	RotationVector(AngleUnits units, float[] rv) {
		this.set(units, rv);
	}
	RotationVector(RotationVector rv) {
		this.set(rv);
	}
	RotationVector(AngleUnits units, float a, float x, float y, float z) {
		this.set(units, a, x, y, z);
	}
	synchronized void set(AngleUnits units, float a, float x, float y, float z) {
		this.units=units;
		this.a=a;
		this.x=x;
		this.y=y;
		this.z=z;	
	}
	synchronized void set(AngleUnits units, float [] rv) {
		assert(rv.length==4); 
		this.units = units;
		a=rv[0];
		x=rv[1];
		y=rv[2];
		z=rv[3];
	}
	synchronized void computeFromQuaternion(DemoQuaternion q, AngleUnits units) {
		float theta = (float) Math.acos(q.q0);
		a = 2*theta;
		if (q.q0==1) {
			x = y = z = (float) 0.0;
		} else {
			float sinTheta = (float) Math.sin(theta);
			x = q.q1/sinTheta;
			y = q.q2/sinTheta;
			z = q.q3/sinTheta;
		} 
		this.units = AngleUnits.RADIANS;
		convert(units); // only changes if needed
	}
	synchronized void convert(AngleUnits units) {
		if ((this.units==AngleUnits.RADIANS)&&(units==AngleUnits.DEGREES)) {
			this.units = units;
			this.a = this.a * MyUtils.degreesPerRadian;
		} else if ((this.units==AngleUnits.RADIANS)&&(units==AngleUnits.DEGREES)) {
			this.units = units;
			this.a = this.a * MyUtils.radiansPerDegree;
		}
	}
	synchronized void set(RotationVector rv) {
		this.units = rv.units;
		this.a = rv.a;
		this.x = rv.x;
		this.y = rv.y;
		this.z = rv.z;
	}
	synchronized void setIdentity() {
		units = AngleUnits.RADIANS;
		a=0;
		x=0;
		y=0;
		z=1;		
	}

}
