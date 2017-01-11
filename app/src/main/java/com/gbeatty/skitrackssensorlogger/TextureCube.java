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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.opengles.GL10;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLUtils;
/*

/**
 * Defines a cube with texture. This class is used for both the device and panorama views.
 * Effectively, we define a cube and then render graphics onto each face of that cube to get
 * the desired impact.  For boards, the Z dimension is relatively short and the viewer 
 * perspective is from outside the cube.  For the Panorama view, the user perspective is
 * from INSIDE the cube.
 * The class defines the vertices for each face, then 
 * renders the cube by translating and rotating the faces.
 * Based on code found at http://www3.ntu.edu.sg/home/ehchua/programming/android/Android_3D.html
 * @author Michael Stanley
*/
public class TextureCube {
   private int front, left, back, right, top, bottom;
   public String description = null; // used for debug purposes
   private FloatBuffer vertexArrayBuffer; // Buffer for vertex-array
   private FloatBuffer textureBuffer;    // Buffer for texture-coords-array 
   private float w, l, h; // w=screen_width(x)/2, l=length(y)/2, h=screen_height(z)/2
   public float offset;  // perspective offset/2
   private float[] vertices = { // Vertices for top and bottom
      -1.0f, -1.0f, 0.0f,  // 0. left-bottom-front
       1.0f, -1.0f, 0.0f,  // 1. right-bottom-front
      -1.0f,  1.0f, 0.0f,  // 2. left-top-front
       1.0f,  1.0f, 0.0f,  // 3. right-top-front
      -1.0f, -1.0f, 0.0f,  // 0. left-bottom-front
       1.0f, -1.0f, 0.0f,  // 1. right-bottom-front
      -1.0f,  1.0f, 0.0f,  // 2. left-top-front
       1.0f,  1.0f, 0.0f,  // 3. right-top-front
      -1.0f, -1.0f, 0.0f,  // 0. left-bottom-front
       1.0f, -1.0f, 0.0f,  // 1. right-bottom-front
      -1.0f,  1.0f, 0.0f,  // 2. left-top-front
       1.0f,  1.0f, 0.0f   // 3. right-top-front
   };
  
   float[] textureCoordinates = { // Texture coords for the above face 
      0.0f, 1.0f,  // A. left-bottom (NEW)
      1.0f, 1.0f,  // B. right-bottom (NEW)
      0.0f, 0.0f,  // C. left-top (NEW)
      1.0f, 0.0f,  // D. right-top (NEW)
      0.0f, 1.0f,  // A. left-bottom (NEW)
      1.0f, 1.0f,  // B. right-bottom (NEW)
      0.0f, 0.0f,  // C. left-top (NEW)
      1.0f, 0.0f,  // D. right-top (NEW)
      0.0f, 1.0f,  // A. left-bottom (NEW)
      1.0f, 1.0f,  // B. right-bottom (NEW)
      0.0f, 0.0f,  // C. left-top (NEW)
      1.0f, 0.0f   // D. right-top (NEW)
   };
   int[] textureIds = new int[6];   // Array for 1 texture-ID 
     
   // Constructor - Set up the buffers
   //public TextureCube(float w, float l, float h) {
   public TextureCube(int[] surfaces, float[] dimensions, String desc) {
	   assert(dimensions.length==3); 
	   this.description = new String(desc);
	   this.w = dimensions[0];
	   this.l = dimensions[1];
	   this.h = dimensions[2];
	   this.offset = dimensions[3];
	   this.front = surfaces[0];
	   this.left = surfaces[1];
	   this.back = surfaces[2];
	   this.right = surfaces[3];
	   this.top = surfaces[4];
	   this.bottom = surfaces[5];

	   // scale for top & bottom
	  vertices[0]*=w; vertices[1]*=l; 
	  vertices[3]*=w; vertices[4]*=l; 
	  vertices[6]*=w; vertices[7]*=l; 
	  vertices[9]*=w; vertices[10]*=l; 

	  // scale for left and right
	  vertices[12]*=l; vertices[13]*=h; 
	  vertices[15]*=l; vertices[16]*=h; 
	  vertices[18]*=l; vertices[19]*=h; 
	  vertices[21]*=l; vertices[22]*=h; 

	  // scale for front and back
	  vertices[24]*=w; vertices[25]*=h; 
	  vertices[27]*=w; vertices[28]*=h; 
	  vertices[30]*=w; vertices[31]*=h; 
	  vertices[33]*=w; vertices[34]*=h; 

	  // Setup vertex-array buffer. Vertices in float. An float has 4 bytes
      ByteBuffer vbb0 = ByteBuffer.allocateDirect(vertices.length * 4);
      vbb0.order(ByteOrder.nativeOrder());   // Use native byte order
      vertexArrayBuffer = vbb0.asFloatBuffer(); // Convert from byte to float
      vertexArrayBuffer.put(vertices);      // Copy data into buffer
      vertexArrayBuffer.position(0);           // Rewind
   
      // Setup texture-coords-array buffer, in float. An float has 4 bytes (NEW)
      ByteBuffer tbb = ByteBuffer.allocateDirect(textureCoordinates.length * 4);
      tbb.order(ByteOrder.nativeOrder());
      textureBuffer = tbb.asFloatBuffer();
      textureBuffer.put(textureCoordinates);
      textureBuffer.position(0);      
   }
   
   // Draw the shape
   public void draw(GL10 gl) {
      gl.glFrontFace(GL10.GL_CCW);    // Front face in counter-clockwise orientation
   
      gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
      gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexArrayBuffer);
      gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);  // Enable texture-coords-array
      gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, textureBuffer); // Define texture-coords buffer
      
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

	  gl.glEnable(GL10.GL_TEXTURE_2D);
  
      // left
      gl.glPushMatrix();
	  gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[1]);
      gl.glRotatef(90.0f, 0.0f, 0.0f, 1.0f);
      gl.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
      gl.glTranslatef(0.0f, 0.0f, -w);
      gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 4, 4);
      gl.glPopMatrix();
  
  
      // right
      gl.glPushMatrix();
	  gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[3]);
      gl.glRotatef(-90.0f, 0.0f, 0.0f, 1.0f);
      gl.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
      gl.glTranslatef(0.0f, 0.0f, -w);
      gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 4, 4);
      gl.glPopMatrix();
  
      // front
      gl.glPushMatrix();
	  gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[0]);
      gl.glRotatef(180.0f, 0.0f, 1.0f, 0.0f);
      gl.glRotatef(-90.0f, 1.0f, 0.0f, 0.0f);
      gl.glTranslatef(0.0f, 0.0f, l);
      gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 8, 4);
      gl.glPopMatrix();
  
      // back
      gl.glPushMatrix();
	  gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[2]);
      gl.glRotatef(180.0f, 0.0f, 0.0f, 1.0f);
      gl.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
      gl.glTranslatef(0.0f, 0.0f, -l);
      gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 8, 4);
      gl.glPopMatrix();

      // top
      gl.glPushMatrix();
	  gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[4]);
      gl.glTranslatef(0.0f, 0.0f, h);
      gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
      gl.glPopMatrix();

      // bottom
      gl.glPushMatrix();
	  gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[5]);
      gl.glTranslatef(0.0f, 0.0f, -h);
      gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
      gl.glPopMatrix();
  
      gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);  // Disable texture-coords-array (NEW)
      gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
      gl.glDisable(GL10.GL_CULL_FACE);
   }
   public void loadTextures(GL10 gl, Context context) {
	   loadTexture(gl, context, 0, this.front);
	   loadTexture(gl, context, 1, this.left);
	   loadTexture(gl, context, 2, this.back);
	   loadTexture(gl, context, 3, this.right);
	   loadTexture(gl, context, 4, this.top);
	   loadTexture(gl, context, 5, this.bottom);
   }
  
   // Load an image into GL texture
   public void loadTexture(GL10 gl, Context context, int index, int image) {
	      // image is of the form R.drawable.floor

	  if (index==0) {
		  gl.glGenTextures(6, textureIds, 0); // Generate texture-ID array
	  } else {
		  
	  }
 
      gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[index]);   // Bind to texture ID
      // Set up texture filters
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
  
      InputStream istream = context.getResources().openRawResource(image);
      Bitmap bitmap;
      try {
         // Read and decode input as bitmap
         bitmap = BitmapFactory.decodeStream(istream);
      } finally {
         try {
            istream.close();
         } catch(IOException e) { }
      }
  
      // Build Texture from loaded bitmap for the currently-bind texture ID
      GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
      bitmap.recycle();
   }
}