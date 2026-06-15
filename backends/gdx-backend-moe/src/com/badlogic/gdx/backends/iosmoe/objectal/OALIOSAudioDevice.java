
package com.badlogic.gdx.backends.iosmoe.objectal;

import com.badlogic.gdx.audio.AudioDevice;
import org.moe.natj.general.ptr.ShortPtr;
import org.moe.natj.general.ptr.impl.PtrFactory;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import static apple.openal.c.OpenAL.alBufferData;

/** @author Jile Gao
 * @author Berstanio */
class OALIOSAudioDevice implements AudioDevice {
	private ALSource alSource;
	private List<ALBuffer> alBuffers = new ArrayList<>();
	private List<ALBuffer> alBuffersFree = new ArrayList<>();
	private final int samplingRate;
	private final boolean isMono;
	private final int format;
	private final ShortBuffer tmpBuffer;
	private final int minSize;
	private final int latency;

	OALIOSAudioDevice (int samplingRate, boolean isMono, int minSize, int bufferCount) {
		this.samplingRate = samplingRate;
		this.isMono = isMono;
		this.format = isMono ? 0x1101 : 0x1103;
		this.minSize = minSize;

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
		if (numSamples < 0) throw new IllegalArgumentException("numSamples cannot be < 0.");

		ShortPtr shortPtr;
		if (numSamples + tmpBuffer.position() >= minSize) {
			// We can now process the data from the temp buffer
			shortPtr = PtrFactory.newShortArray(numSamples + tmpBuffer.position());

			shortPtr.copyFrom(tmpBuffer.array(), 0, 0, tmpBuffer.position());
			shortPtr.copyFrom(samples, offset, tmpBuffer.position(), numSamples);
			numSamples += tmpBuffer.position();
			tmpBuffer.position(0);
		} else {
			tmpBuffer.put(samples, offset, numSamples);
			return;
		}

		while (alBuffersFree.isEmpty()) {
			if (OALAudioSession.sharedInstance().interrupted()) {
				try {
					Thread.sleep(2);
				} catch (InterruptedException ignored) {
				}
				return;
			}
			int toFree = Math.min(alSource.buffersProcessed(), alBuffers.size());
			for (int j = 0; j < toFree; j++) {
				ALBuffer alBuffer = alBuffers.get(0);
				if (alSource.unqueueBuffer(alBuffer)) {
					alBuffersFree.add(alBuffer);
					alBuffers.remove(alBuffer);
				} else {
					break;
				}
			}
			if (alBuffersFree.isEmpty()) {
				try {
					Thread.sleep(2);
				} catch (InterruptedException ignored) {
				}
			}
		}

		ALBuffer buffer = alBuffersFree.remove(0);
		alBufferData(buffer.bufferId(), format, shortPtr, numSamples * 2, samplingRate);
		if (alSource.queueBuffer(buffer)) {
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
