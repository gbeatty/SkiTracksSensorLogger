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

/**
* Defines a basic set of 3D coordinates and their utility functions
* @author Michael Stanley
*/
public class Triad {
	protected float x, y, z;
	Triad() {
		this.zero();
	}
	Triad(float x, float y, float z) {
		this.set(x,y,z);
	}
	Triad(Triad old) {
		this.set(old);
	}
	public synchronized void set(Triad old) {
		this.x = old.x;
		this.y = old.y;
		this.z = old.z;
	}
	public synchronized void set(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;				
	}
	public synchronized void zero() {
		this.x=0;
		this.y=0;
		this.z=0;		
	}
	public synchronized void scale(float m) {
		this.x = m*this.x;
		this.y = m*this.y;
		this.z = m*this.z;
	}
	public synchronized float[] array() {
		float[] a = new float[3];
		a[0] = this.x;
		a[1] = this.y;
		a[2] = this.z;
		return(a);
	}
	public float x() {
		return(x);
	}
	public float y() {
		return(y);
	}
	public float z() {
		return(z);
	}
	public synchronized String toString()  {
		String str;
		str = x() + " " + y() + " " + z();
		return(str);
	}
	public synchronized String toCsvString()  {
		String str;
		str = x() + ", " + y() + ", " + z();
		return(str);
	}
	
}
