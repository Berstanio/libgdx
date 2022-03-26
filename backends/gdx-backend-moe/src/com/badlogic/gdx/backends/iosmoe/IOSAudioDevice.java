/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.backends.iosmoe;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import apple.openal.c.OpenAL;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.backends.iosmoe.objectal.ALSource;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.moe.natj.general.ptr.FloatPtr;
import org.moe.natj.general.ptr.IntPtr;
import org.moe.natj.general.ptr.impl.PtrFactory;
import org.moe.natj.objc.ObjCRuntime;

import static apple.openal.c.OpenAL.*;

/** @author Nathan Sweet */
public class IOSAudioDevice implements AudioDevice {
	static private final int bytesPerSample = 2;
	private static final int AL_FORMAT_STEREO16 = 0x1103;
	private static final int AL_FORMAT_MONO16 = 0x1101;
	private static final int AL_GAIN = 0x100A;
	private static final int AL_LOOPING = 0x1007;
	private static final int AL_FALSE = 0;
	private static final int AL_NO_ERROR = AL_FALSE;
	private static final int AL_BUFFERS_PROCESSED = 0x1016;
	private static final int AL_INVALID_VALUE = 0xA003;
	private static final int AL_SOURCE_STATE = 0x1010;
	private static final int AL_PLAYING = 0x1012;
	private static final int AL_SEC_OFFSET = 0x1024;

	private final int channels;
	private IntBuffer buffers;
	private int sourceID = -1;
	private int format, sampleRate;
	private boolean isPlaying;
	private float volume = 1;
	private float renderedSeconds, secondsPerBuffer;
	private byte[] bytes;
	private final int bufferSize;
	private final int bufferCount;
	private final ByteBuffer tempBuffer;
	private ALSource alSource;

	public IOSAudioDevice (int sampleRate, boolean isMono, int bufferSize, int bufferCount) {
		channels = isMono ? 1 : 2;
		this.bufferSize = bufferSize;
		this.bufferCount = bufferCount;
		this.format = channels > 1 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16;
		this.sampleRate = sampleRate;
		secondsPerBuffer = (float)bufferSize / bytesPerSample / channels / sampleRate;
		tempBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN);
	}

	public void writeSamples (short[] samples, int offset, int numSamples) {
		if (bytes == null || bytes.length < numSamples * 2) bytes = new byte[numSamples * 2];
		int end = Math.min(offset + numSamples, samples.length);
		for (int i = offset, ii = 0; i < end; i++) {
			short sample = samples[i];
			bytes[ii++] = (byte)(sample & 0xFF);
			bytes[ii++] = (byte)((sample >> 8) & 0xFF);
		}
		writeSamples(bytes, 0, numSamples * 2);
	}

	public void writeSamples (float[] samples, int offset, int numSamples) {
		if (bytes == null || bytes.length < numSamples * 2) bytes = new byte[numSamples * 2];
		int end = Math.min(offset + numSamples, samples.length);
		for (int i = offset, ii = 0; i < end; i++) {
			float floatSample = samples[i];
			floatSample = MathUtils.clamp(floatSample, -1f, 1f);
			int intSample = (int)(floatSample * 32767);
			bytes[ii++] = (byte)(intSample & 0xFF);
			bytes[ii++] = (byte)((intSample >> 8) & 0xFF);
		}
		writeSamples(bytes, 0, numSamples * 2);
	}

	public void writeSamples (byte[] data, int offset, int length) {
		if (length < 0) throw new IllegalArgumentException("length cannot be < 0.");

		if (sourceID == -1) {
			alSource = ALSource.alloc().init();
			sourceID = alSource.sourceId();
			if (sourceID == -1) return;
			if (buffers == null) {
				buffers = ByteBuffer.allocateDirect(bufferCount << 2).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
				alGetError();
				alGenBuffers(bufferCount, PtrFactory.newIntPtr(buffers));
				if (alGetError() != AL_NO_ERROR) throw new GdxRuntimeException("Unabe to allocate audio buffers.");
			}
			alSourcei(sourceID, AL_LOOPING, AL_FALSE);
			alSourcef(sourceID, AL_GAIN, volume);
			// Fill initial buffers.
			for (int i = 0; i < bufferCount; i++) {
				int bufferID = buffers.get(i);
				int written = Math.min(bufferSize, length);
				((Buffer)tempBuffer).clear();
				((Buffer)tempBuffer.put(data, offset, written)).flip();
				System.out.println("Start");
				alBufferData(bufferID, format, PtrFactory.newBytePtr(tempBuffer), (data.length - offset) * 2, sampleRate);
				System.out.println("End");
				alSourceQueueBuffers(sourceID, 1, PtrFactory.newIntReference(bufferID));

				length -= written;
				offset += written;
			}
			alSourcePlay(sourceID);
			isPlaying = true;
		}

		while (length > 0) {
			int written = fillBuffer(data, offset, length);
			length -= written;
			offset += written;
		}
	}

	/** Blocks until some of the data could be buffered. */
	private int fillBuffer (byte[] data, int offset, int length) {
		int written = Math.min(bufferSize, length);

		outer:
		while (true) {
			IntPtr intPtr = PtrFactory.newIntPtr(1, true, false);
			alGetSourcei(sourceID, AL_BUFFERS_PROCESSED, intPtr);
			int buffers = intPtr.get(0);
			while (buffers-- > 0) {
				IntPtr bufferIDPtr = PtrFactory.newIntPtr(1, true, false);
				alSourceUnqueueBuffers(sourceID, 1, bufferIDPtr);
				int bufferID = bufferIDPtr.get(0);
				if (bufferID == AL_INVALID_VALUE) break;
				renderedSeconds += secondsPerBuffer;

				((Buffer)tempBuffer).clear();
				((Buffer)tempBuffer.put(data, offset, written)).flip();
				alBufferData(bufferID, format, PtrFactory.newBytePtr(tempBuffer), (data.length - offset) * 2,sampleRate);

				alSourceQueueBuffers(sourceID, 1, PtrFactory.newIntReference(bufferID));
				break outer;
			}
			// Wait for buffer to be free.
			try {
				Thread.sleep((long)(1000 * secondsPerBuffer));
			} catch (InterruptedException ignored) {
			}
		}

		// A buffer underflow will cause the source to stop.
		IntPtr statePtr = PtrFactory.newIntPtr(1, true, false);
		alGetSourcei(sourceID, AL_SOURCE_STATE, statePtr);
		if (!isPlaying || statePtr.get(0) != AL_PLAYING) {
			alSourcePlay(sourceID);
			isPlaying = true;
		}

		return written;
	}

	public void stop () {
		if (sourceID == -1) return;
		ObjCRuntime.disposeObject(alSource);
		alSource = null;
		sourceID = -1;
		renderedSeconds = 0;
		isPlaying = false;
	}

	public boolean isPlaying () {
		if (sourceID == -1) return false;
		return isPlaying;
	}

	public void setVolume (float volume) {
		this.volume = volume;
		if (sourceID != -1) alSourcef(sourceID, AL_GAIN, volume);
	}

	public float getPosition () {
		if (sourceID == -1) return 0;
		FloatPtr floatPtr = PtrFactory.newFloatPtr(1, true, false);
		alGetSourcef(sourceID, AL_SEC_OFFSET, floatPtr);
		return renderedSeconds + floatPtr.get(0);
	}

	public void setPosition (float position) {
		renderedSeconds = position;
	}

	public int getChannels () {
		return format == AL_FORMAT_STEREO16 ? 2 : 1;
	}

	public int getRate () {
		return sampleRate;
	}

	public void dispose () {
		if (buffers == null) return;
		if (sourceID != -1) {
			ObjCRuntime.disposeObject(alSource);
			alSource = null;
			sourceID = -1;
		}
		alDeleteBuffers(bufferCount, PtrFactory.newIntPtr(buffers));
		buffers = null;
	}

	public boolean isMono () {
		return channels == 1;
	}

	public int getLatency () {
		return (int)(secondsPerBuffer * bufferCount * 1000);
	}

	@Override
	public void pause () {
		// A buffer underflow will cause the source to stop.
	}

	@Override
	public void resume () {
		// Automatically resumes when samples are written
	}
}
