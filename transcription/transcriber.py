import whisperx
import sys
import argparse
import os
import re
from pydub import AudioSegment
import subprocess
from dotenv import load_dotenv

def find_speakers_and_inputs(directory):
    speaker_names = {}
    inputs = []

    sample_pattern = re.compile(r"sample-(\w+)\.(\w+)$", re.IGNORECASE)
    media_extensions = (".mp3", ".wav", ".m4a", ".flac", ".aac", ".ogg", ".mp4", ".mov")

    for filename in os.listdir(directory):
        file_path = os.path.join(directory, filename)

        if os.path.isfile(file_path) and filename.lower().endswith(media_extensions):
            match = sample_pattern.match(filename)
            if match:
                speaker = match.group(1)
                speaker_names[speaker] = file_path
            else:
                inputs.append(file_path)

    return speaker_names, inputs

def convert_to_wav(input_file, output_wav):
    """Convert any audio/video file to WAV using FFmpeg."""
    subprocess.run(["ffmpeg", "-y", "-i", input_file, output_wav], check=True)

def merge_wav_files(wav_paths, output_path):
    """Merge a list of WAV files sequentially into one."""
    if not wav_paths:
        raise ValueError("No WAV files provided to merge.")

    combined = AudioSegment.empty()
    for wav_file in wav_paths:
        combined += AudioSegment.from_wav(wav_file)

    combined.export(output_path, format="wav")

def convert_and_merge(inputs, output, directory):
    # Step 1: Convert all inputs to WAV
    converted_wavs = [f"{directory}/converted_temp_{i}.wav" for i in range(len(inputs))]
    for inp, out in zip(inputs, converted_wavs):
        convert_to_wav(inp, out)

    # Step 2: Merge all WAVs
    merge_wav_files(converted_wavs, output)
    print(f"Merged file created: {output}")

    # Step 3: Delete intermediate WAVs
    for wav_file in converted_wavs:
        try:
            os.remove(wav_file)
            print(f"Deleted temporary file: {wav_file}")
        except OSError as e:
            print(f"Error deleting {wav_file}: {e}")

if __name__ == "__main__":
    load_dotenv()
    parser = argparse.ArgumentParser(description="Process media files in a directory")
    parser.add_argument("directory", help="Directory containing media files")
    args = parser.parse_args()

    speaker_names, inputs = find_speakers_and_inputs(args.directory)

    project_id = ""

    match = re.match(r"media-([^_]+)_([^_]+)$", args.directory)
    if match:
        project_id = match.group(1)
        print("Project ID: ", project_id)
    else:
        print("No match for project id found.")

    merged = f"{args.directory}/merged.wav"
    convert_and_merge(inputs, merged, args.directory)

    device = "cpu"  # or "cpu"
    batch_size = 16
    compute_type = "int8"  # use "int8" for CPU

    # --- LOAD MODEL ---
    print("Loading WhisperX...")
    model_dir = "whisper_model/"
    model = whisperx.load_model("turbo", device, compute_type=compute_type)

    # --- PROCESS FILE 1 ---
    print(f"Transcribing {merged}...")
    result = model.transcribe(merged, batch_size=batch_size)
    model_a, metadata = whisperx.load_align_model(language_code=result["language"], device=device)
    result = whisperx.align(result["segments"], model_a, metadata, merged, device)

    # --- PRINT TRANSCRIPTION ---
    print(f"\nTranscription for {merged}:")
    for segment in result["segments"]:
        print(f"{segment['start']:.2f} - {segment['end']:.2f}: {segment['text']}")

    print(f"Diarizing {merged}...")
    diarize_model = whisperx.diarize.DiarizationPipeline(use_auth_token=os.getenv("HF_TOKEN"), device=device)
    diarize_segments = diarize_model(merged)
    result = whisperx.assign_word_speakers(diarize_segments, result)

    # --- CONFIGURE DIARIZATION ---
    speaker_counter = 0
    segment_counter = 0
    segment_index_counter = 0
    for actual_name in speaker_names.keys():
        current_speaker = result["segments"][segment_counter].get("speaker", None)
        for segment in result["segments"][segment_counter:]:
            segment["index"] = segment_index_counter
            segment_index_counter += 1
            if segment.get("speaker") == current_speaker:
                speaker_names[actual_name] = current_speaker
                segment["speaker"] = actual_name
            else:
                segment_counter = result["segments"].index(segment)
                break

    for segment in result["segments"][segment_counter:]:
        segment["index"] = segment_index_counter
        segment_index_counter += 1
        key = next((k for k, v in speaker_names.items() if v == segment["speaker"]), None)
        if key is not None:
            segment["speaker"] = key
        else:
            segment["speaker"] = "Unknown"

    # --- PRINT DIARIZATION ---
    print("\nSpeaker assignment results:")
    print("Speaker\tStart\tEnd\tText")
    for segment in result["segments"]:
        print(f"{segment['speaker']}\t{segment['start']:.2f}\t{segment['end']:.2f}\t{segment['text']}")