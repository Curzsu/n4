import wave
import math
import struct

SAMPLE_RATE = 44100
AMPLITUDE = 0.5


def generate_tone(freq, duration, sample_rate=SAMPLE_RATE, amplitude=AMPLITUDE):
    """Generate a pure sine wave tone."""
    samples = int(sample_rate * duration)
    return [
        int(amplitude * 32767 * math.sin(2 * math.pi * freq * t / sample_rate))
        for t in range(samples)
    ]


def generate_silence(duration, sample_rate=SAMPLE_RATE):
    """Generate silence."""
    samples = int(sample_rate * duration)
    return [0] * samples


def save_wav(filename, samples, sample_rate=SAMPLE_RATE):
    """Save samples as a mono WAV file."""
    with wave.open(filename, 'w') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(struct.pack('<' + 'h' * len(samples), *samples))


def make_classic_alarm(output_file):
    """Classic two-tone alarm: 880Hz and 698Hz alternating."""
    samples = []
    for _ in range(6):
        samples.extend(generate_tone(880, 0.15))
        samples.extend(generate_tone(698, 0.15))
    save_wav(output_file, samples)


def make_short_beep(output_file):
    """Simple short alert beep at 1000Hz."""
    samples = []
    for _ in range(3):
        samples.extend(generate_tone(1000, 0.1))
        samples.extend(generate_silence(0.1))
    save_wav(output_file, samples)


def make_urgent_alarm(output_file):
    """Fast urgent beeping pattern at 1200Hz."""
    samples = []
    for _ in range(10):
        samples.extend(generate_tone(1200, 0.08))
        samples.extend(generate_silence(0.08))
    save_wav(output_file, samples)


if __name__ == '__main__':
    make_classic_alarm('alarm-classic.wav')
    make_short_beep('alarm-beep.wav')
    make_urgent_alarm('alarm-urgent.wav')
    print('Alarm sound files generated successfully.')
