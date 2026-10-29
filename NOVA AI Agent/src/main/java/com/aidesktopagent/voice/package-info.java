/**
 * Voice input/output: microphone capture, speech-to-text, text-to-speech,
 * and voice command processing into the validated Agent pipeline.
 *
 * <p>Audio is only sent to external providers (e.g. OpenAI Whisper) when an
 * explicit transcription is requested — never continuously in the background.
 */
package com.aidesktopagent.voice;
