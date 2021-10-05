/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 3.0.11
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package com.badlogic.gdx.physics.bullet.linearmath;

import com.badlogic.gdx.physics.bullet.BulletBase;

public class btMatrix3x3FloatData extends BulletBase {
	private long swigCPtr;

	protected btMatrix3x3FloatData (final String className, long cPtr, boolean cMemoryOwn) {
		super(className, cPtr, cMemoryOwn);
		swigCPtr = cPtr;
	}

	/** Construct a new btMatrix3x3FloatData, normally you should not need this constructor it's intended for low-level usage. */
	public btMatrix3x3FloatData (long cPtr, boolean cMemoryOwn) {
		this("btMatrix3x3FloatData", cPtr, cMemoryOwn);
		construct();
	}

	@Override
	protected void reset (long cPtr, boolean cMemoryOwn) {
		if (!destroyed) destroy();
		super.reset(swigCPtr = cPtr, cMemoryOwn);
	}

	public static long getCPtr (btMatrix3x3FloatData obj) {
		return (obj == null) ? 0 : obj.swigCPtr;
	}

	@Override
	protected void finalize () throws Throwable {
		if (!destroyed) destroy();
		super.finalize();
	}

	@Override
	protected synchronized void delete () {
		if (swigCPtr != 0) {
			if (swigCMemOwn) {
				swigCMemOwn = false;
				LinearMathJNI.delete_btMatrix3x3FloatData(swigCPtr);
			}
			swigCPtr = 0;
		}
		super.delete();
	}

	public void setEl (btVector3FloatData value) {
		LinearMathJNI.btMatrix3x3FloatData_el_set(swigCPtr, this, btVector3FloatData.getCPtr(value), value);
	}

	public btVector3FloatData getEl () {
		long cPtr = LinearMathJNI.btMatrix3x3FloatData_el_get(swigCPtr, this);
		return (cPtr == 0) ? null : new btVector3FloatData(cPtr, false);
	}

	public btMatrix3x3FloatData () {
		this(LinearMathJNI.new_btMatrix3x3FloatData(), true);
	}

}
