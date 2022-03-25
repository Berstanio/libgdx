package com.badlogic.gdx.backends.iosmoe;

import apple.coreaudiotypes.enums.Enums;
import apple.openal.c.OpenAL;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.backends.iosmoe.objectal.ALBuffer;
import com.badlogic.gdx.backends.iosmoe.objectal.ALDevice;
import com.badlogic.gdx.backends.iosmoe.objectal.ALSource;
import com.badlogic.gdx.backends.iosmoe.objectal.OALSimpleAudio;
import org.moe.natj.general.ptr.IntPtr;
import org.moe.natj.general.ptr.VoidPtr;
import org.moe.natj.general.ptr.impl.PtrFactory;
import org.moe.natj.objc.ObjCRuntime;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Queue;

import static apple.openal.c.OpenAL.*;

public class IOSAudioDevice implements AudioDevice {

	ALSource alSource;
	ArrayList<ALBuffer> alBuffers = new ArrayList<>();
	ArrayList<ALBuffer> alBuffersFree = new ArrayList<>();
	//ShortBuffer shortBuffer = ShortBuffer.allocate(500);

	public IOSAudioDevice() {
		alSource = ALSource.alloc().init();
		for (int i = 0; i < 5; i++) {
			ALBuffer buffer = ALBuffer.alloc().initWithNameDataSizeFormatFrequency("test", null, 0, 0x1103, 44100);
			alBuffersFree.add(buffer);
		}
	}

	@Override
	public boolean isMono () {
		return false;
	}

	@Override
	public void writeSamples (short[] samples, int offset, int numSamples) {
		short[] intArray = new short[samples.length];
		for (int in = 0; in < samples.length; in++) {
			System.out.print(samples[in] + " ");
			int i = (int) samples[in];
			intArray[in] = (short)i;
		}
		System.out.println();
		VoidPtr voidPtr = PtrFactory.newWeakShortArray(intArray);
		ALBuffer buffer = ALBuffer.alloc().initWithNameDataSizeFormatFrequency("test", voidPtr, samples.length, 0x1103, 44100);
		alSource.queueBuffer(buffer);
	}

	@Override
	public void writeSamples (float[] samples, int offset, int numSamples) {
		short[] intArray = new short[samples.length];

		for (int i = offset, j = 0; i < samples.length; i++, j++) {
			float fValue = samples[i];
			if (fValue > 1) fValue = 1;
			if (fValue < -1) fValue = -1;
			short value = (short)(fValue * Short.MAX_VALUE);
			intArray[j] = value;
		}
		System.out.println(numSamples);
		VoidPtr voidPtr = PtrFactory.newWeakShortArray(intArray);

		if (alBuffersFree.size() == 0) {
			while (true) {
				int i = alSource.buffersProcessed();
				if (i != 0) {
					System.out.println(i);
				}
				for (int j = 0; j < i; j++) {
					ALBuffer alBuffer = alBuffers.remove(j);
					alSource.unqueueBuffer(alBuffer);
					//ObjCRuntime.disposeObject(alBuffer);
					alBuffersFree.add(alBuffer);
				}
				if (i != 0) {
					break;
				}
			}
		}
		ALBuffer buffer = alBuffersFree.remove(0);
		//buffer.bufferDataSize(voidPtr, samples.length * 2);
		alBufferData(buffer.bufferId(), 0x1103, voidPtr, samples.length * 2, 44100);
		alSource.queueBuffer(buffer);
		alBuffers.add(buffer);

		if(alBuffers.size() == 2) {
			alSource.play();
		}

		//alSource.play();

	}

	@Override
	public int getLatency () {
		return 0;
	}

	@Override
	public void dispose () {

	}

	@Override
	public void setVolume (float volume) {

	}

	@Override
	public void pause () {

	}

	@Override
	public void resume () {

	}
}
