
package com.badlogic.gdx.backends.iosmoe;

import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.backends.iosmoe.objectal.ALBuffer;
import com.badlogic.gdx.backends.iosmoe.objectal.ALSource;
import com.badlogic.gdx.backends.iosmoe.objectal.OALAudioSession;
import org.moe.natj.general.ptr.ShortPtr;
import org.moe.natj.general.ptr.impl.PtrFactory;

import java.nio.ShortBuffer;
import java.util.ArrayList;

import static apple.openal.c.OpenAL.*;

public class IOSAudioDevice implements AudioDevice {

	private ALSource alSource;
	private ArrayList<ALBuffer> alBuffers = new ArrayList<>();
	private ArrayList<ALBuffer> alBuffersFree = new ArrayList<>();
	private int samplingRate;
	private boolean isMono;
	private int format;
	private ShortBuffer tmpBuffer;
	private int minSize;
	private int latency;

	public IOSAudioDevice (int samplingRate, boolean isMono, int minSize, int bufferCount) {
		this.samplingRate = samplingRate;
		this.isMono = isMono;
		this.format = isMono ? 0x1101 : 0x1103; // AL_FORMAT_STEREO16 : AL_FORMAT_MONO16
		this.minSize = minSize;
		// This will use the native byte order. On iOS this should always be little endian.
		// This is relevant because it might be, that iOS OpenAL only supports little endian, contrary to the OpenAL specification.
		tmpBuffer = ShortBuffer.allocate(minSize);
		latency = minSize / (isMono ? 1 : 2) / bufferCount;
		alSource = ALSource.alloc().init();
		for (int i = 0; i < bufferCount; i++) {
			// We use ALBuffer here to keep as close to ObjectAL as possible. But this is just hackery to generate a bufferid in the
			// end
			ALBuffer buffer = ALBuffer.alloc().initWithNameDataSizeFormatFrequency("test",
				PtrFactory.newWeakShortArray(1, (short)100), 2, format, samplingRate);
			alBuffersFree.add(buffer);
		}
	}

	@Override
	public boolean isMono () {
		return isMono;
	}

	@Override
	public void writeSamples (short[] samples, int offset, int numSamples) {
		ShortPtr voidPtr;
		if (numSamples + tmpBuffer.position() >= minSize) {
			// We can now process the data from the temp buffer
			voidPtr = PtrFactory.newShortArray(numSamples + tmpBuffer.position());

			voidPtr.copyFrom(tmpBuffer.array(), 0, 0, tmpBuffer.position());
			voidPtr.copyFrom(samples, offset, tmpBuffer.position(), numSamples);
			numSamples += tmpBuffer.position();
			tmpBuffer.position(0);
		} else {
			tmpBuffer.put(samples, offset, numSamples);
			return;
		}

		if (alBuffersFree.isEmpty()) {
			while (true) {
				// TODO: 14.11.22 Needs proper solution on ObjectAL side
				if (OALAudioSession.sharedInstance().interrupted()) {
					try {
						// Should be a good enough measure
						Thread.sleep(2);
					} catch (InterruptedException ignored) {
					}
					return;
				}
				boolean freedBuffer = false;
				int toFree = Math.min(alSource.buffersProcessed(), alBuffers.size());
				for (int j = 0; j < toFree; j++) {
					ALBuffer alBuffer = alBuffers.get(0);
					if(alSource.unqueueBuffer(alBuffer)) {
						alBuffersFree.add(alBuffer);
						alBuffers.remove(alBuffer);
						freedBuffer = true;
					} else {
						break;
					}
				}
				if (freedBuffer) {
					break;
				} else {
					try {
						// Should be a good enough measure
						Thread.sleep(2);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}
		ALBuffer buffer = alBuffersFree.remove(0);
		alBufferData(buffer.bufferId(), format, voidPtr, numSamples * 2, samplingRate);
		if(alSource.queueBuffer(buffer)) {
			alBuffers.add(buffer);
		}
		if (!alSource.playing()) {
			alSource.play();
		}
	}

	@Override
	public void writeSamples (float[] samples, int offset, int numSamples) {
		short[] shortSamples = new short[samples.length];

		for (int i = offset, j = 0; i < samples.length; i++, j++) {
			float fValue = samples[i];
			if (fValue > 1) fValue = 1;
			if (fValue < -1) fValue = -1;
			short value = (short)(fValue * Short.MAX_VALUE);
			shortSamples[j] = value;
		}
		writeSamples(shortSamples, offset, numSamples);
	}

	@Override
	public int getLatency () {
		return latency;
	}

	@Override
	public void dispose () {
		alSource.stop();
		/*
		 * for (ALBuffer buffer : alBuffersFree) { ObjCRuntime.disposeObject(buffer); } for (ALBuffer buffer : alBuffers) {
		 * ObjCRuntime.disposeObject(buffer); } ObjCRuntime.disposeObject(alSource);
		 */
		// Maybe let GC handle the disposing?
		alBuffers = null;
		alBuffersFree = null;
		alSource = null;
	}

	@Override
	public void setVolume (float volume) {
		alSource.setVolume(volume);
	}

	@Override
	public void pause () {
		alSource.setPaused(true);
	}

	@Override
	public void resume () {
		alSource.setPaused(false);
	}
}
